package io.redlink.more.data.service;

import io.redlink.more.data.configuration.GarminConfiguration;
import io.redlink.more.data.configuration.GarminWellnessApiConfiguration;
import io.redlink.more.data.garmin.wellness.client.UserApiApi;
import io.redlink.more.data.garmin.wellness.client.UserControllerApi;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.ParticipantKeyValue;
import io.redlink.more.data.model.ParticipantWithObservationProperties;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.garmin.GarminAuthenticationValues;
import io.redlink.more.data.model.garmin.GarminUserAccessToken;
import io.redlink.more.data.model.garmin.UserAccessTokenWithData;
import io.redlink.more.data.repository.KeyValueRepository;
import io.redlink.more.data.repository.StudyRepository;
import io.redlink.more.data.util.GarminUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class GarminService {
    public final static String AUTH_VALUES_KEY = "authenticationValues";
    public final static String USER_ACCESS_TOKEN_KEY = "userAccessToken";
    private final static String USER_ID_TYPE_KEY = "keyType";
    private final static String USER_PERMISSIONS_KEY = "permissions";
    private final GarminConfiguration garminConfiguration;
    private final RestTemplate restTemplate;
    private final StudyRepository studyRepository;
    private final KeyValueRepository keyValueRepository;
    private final GarminWellnessApiConfiguration garminWellnessApiConfiguration;

    public GarminService(GarminConfiguration garminConfiguration, StudyRepository studyRepository, KeyValueRepository keyValueRepository, GarminWellnessApiConfiguration garminWellnessApiConfiguration) {
        this.garminConfiguration = garminConfiguration;
        this.studyRepository = studyRepository;
        this.keyValueRepository = keyValueRepository;
        this.garminWellnessApiConfiguration = garminWellnessApiConfiguration;
        this.restTemplate = new RestTemplate();
    }

    public String getSsoUrl(RoutingInfo routingInfo, String requestUrl) {
        List<Observation> observations = studyRepository.filterObservations(
                routingInfo,
                true,
                observation -> observation.type().toLowerCase().contains("garmin"));
        if (observations.isEmpty()) {
            throw new IllegalStateException("No Garmin Observations found for " + routingInfo.participantId());
        }

        if (hasValidUserAccessToken(routingInfo)) {
            return garminConfiguration.getRedirectUri();
        }

        URI baseUri = garminConfiguration.basicOAuthUri();

        String codeVerifier = GarminUtils.createCodeVerifier();
        String state = GarminUtils.garminOAuthState();

        GarminAuthenticationValues authenticationValues = new GarminAuthenticationValues(state, codeVerifier, requestUrl);
        Map<String, Object> properties = Map.of(AUTH_VALUES_KEY, authenticationValues.toMap());
        observations.forEach(observation ->
                studyRepository.setParticipantProperties(routingInfo.studyId(), routingInfo.participantId(), observation.observationId(), properties)
        );

        String codeChallenge = GarminUtils.createCodeChallenge(codeVerifier);
        String oauthUrl = baseUri + "&code_challenge=" + codeChallenge + "&code_challenge_method=S256" + "&state=" + state;

        try {
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    oauthUrl,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            return response.getHeaders().getLocation() != null
                    ? response.getHeaders().getLocation().toString()
                    : oauthUrl;
        } catch (Exception e) {
            return oauthUrl;
        }
    }

    public void ssoCallback(String state, String code) {
        var participantWithObservationPropertiesList = studyRepository.getParticipantByGarminStatus(state);
        if (participantWithObservationPropertiesList.isEmpty()) {
            throw new IllegalStateException("No Garmin Status found for state " + state);
        }

        var participantWithObservationProperties = participantWithObservationPropertiesList.stream().findFirst().orElseThrow();
        var garminAccessToken = queryUserAccessToken(participantWithObservationProperties, code);
        if (garminAccessToken.isEmpty()) {
            throw new IllegalStateException("No Garmin Access Token found for " + participantWithObservationPropertiesList.get(0).participantId());
        }
        var userAccesstokenWithData = UserAccessTokenWithData.createNewFrom(garminAccessToken.get());
        storeUserAccessToken(userAccesstokenWithData, participantWithObservationPropertiesList, participantWithObservationProperties);
        storeGarminUserId(participantWithObservationProperties.studyId(), participantWithObservationProperties.participantId(), userAccesstokenWithData);
    }


    public boolean deleteUserIdAndToken(String userId) {
        var participants = participantForGarminUserId(userId);
        if (participants.isEmpty()) {
            return true;
        }

        return participants.stream().allMatch(participant ->
                deleteGarminUserId(participant.studyId(), participant.participantId(), userId)
                        && deleteUserAccessToken(participant.studyId(), participant.participantId())
        );
    }

    public boolean updateUserPermissions(String userId, List<String> permissions) {
        var participants = participantForGarminUserId(userId);
        if (participants.isEmpty()) {
            return false;
        }
        participants.forEach(participant -> keyValueRepository.upsert(participant.studyId(), participant.participantId(), userId, Map.of(USER_ID_TYPE_KEY, "garmin", USER_PERMISSIONS_KEY, permissions)));
        return true;
    }

    private void storeGarminUserId(Long studyId, int participantId, UserAccessTokenWithData userAccessTokenWithData) {
        var userId = getUserId(userAccessTokenWithData);
        if (userId.isEmpty()) {
            throw new IllegalStateException("No Garmin User ID found for " + participantId);
        }
        var permissions = getPermissions(userAccessTokenWithData);
        Map<String, Object> data = Map.of(USER_ID_TYPE_KEY, "garmin", USER_PERMISSIONS_KEY, permissions);
        keyValueRepository.upsert(studyId, participantId, userId.get(), data);
    }

    private Optional<String> getUserId(UserAccessTokenWithData userAccessTokenWithData) {
        try {
            UserApiApi userApi = garminWellnessApiConfiguration.getUserApi(userAccessTokenWithData);
            var response = userApi.uSERID();
            return Optional.ofNullable(response.getUserId());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private List<String> getPermissions(UserAccessTokenWithData userAccessTokenWithData) {
        try {
            UserControllerApi userApi = garminWellnessApiConfiguration.getUserControllerApi(userAccessTokenWithData);
            var response = userApi.gETUSERPERMISSIONS();
            return response.getPermissions();
        } catch (RuntimeException e) {
            return Collections.emptyList();
        }
    }

    private List<ParticipantKeyValue> participantForGarminUserId(String garminUserId) {
        try {
            var result = keyValueRepository.getByKey(garminUserId);
            return result.stream().filter(participantKeyValue ->
                    participantKeyValue.value().containsKey(USER_ID_TYPE_KEY)
                            && participantKeyValue.value().get(USER_ID_TYPE_KEY).equals("garmin")
            ).toList();
        } catch (RuntimeException e) {
            return Collections.emptyList();
        }
    }

    private boolean deleteGarminUserId(Long studyId, int participantId, String garminUserId) {
        try {
            return keyValueRepository.delete(studyId, participantId, garminUserId, Map.of(USER_ID_TYPE_KEY, "garmin"));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private Optional<GarminUserAccessToken> queryUserAccessToken(ParticipantWithObservationProperties participantWithObservationProperties, String code) {
        try {
            if (!participantWithObservationProperties.properties().containsKey(AUTH_VALUES_KEY) || !(participantWithObservationProperties.properties().get(AUTH_VALUES_KEY) instanceof Map)) {
                throw new IllegalStateException("No authentication values found for " + participantWithObservationProperties.participantId());
            }
            var garminAuthValues = GarminAuthenticationValues.fromMap((Map<String, String>) participantWithObservationProperties.properties().get(AUTH_VALUES_KEY));

            HttpEntity<?> entity = getHttpEntity(code, garminAuthValues);

            ResponseEntity<GarminUserAccessToken> response = restTemplate.exchange(
                    garminConfiguration.garminTokenUri(),
                    HttpMethod.POST,
                    entity,
                    GarminUserAccessToken.class
            );

            assert response.getBody() != null;
            return Optional.of(response.getBody());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private HttpEntity<?> getHttpEntity(String code, GarminAuthenticationValues garminAuthValues) {
        String body = "grant_type=authorization_code" +
                "&redirect_uri=" + garminConfiguration.getRedirectUri() +
                "&code=" + code +
                "&code_verifier=" + garminAuthValues.challengeCode();

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded");
        headers.add(HttpHeaders.AUTHORIZATION, garminConfiguration.authorizationHeader());
        return new HttpEntity<>(body, headers);
    }

    private Boolean hasValidUserAccessToken(RoutingInfo routingInfo) {
        return hasValidUserAccessToken(routingInfo.studyId(), routingInfo.participantId());
    }


    private Boolean hasValidUserAccessToken(Long studyId, int participantId) {
        try {
            var userAccessData = getUserAccessData(studyId, participantId);
            return userAccessData.isPresent() && userAccessData.get().isAccessTokenValid();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private Optional<UserAccessTokenWithData> getUserAccessData(Long studyId, int participantId) {
        var properties = studyRepository.getParticipantObservationPropertiesByKeyExists(studyId, participantId, USER_ACCESS_TOKEN_KEY);
        if (properties.isEmpty()) {
            return Optional.empty();
        }
        return getUserAccessData(properties.get(0));
    }


    private Optional<UserAccessTokenWithData> getUserAccessData(ParticipantWithObservationProperties participantWithObservationProperties) {
        if (!participantWithObservationProperties.properties().containsKey(USER_ACCESS_TOKEN_KEY) || !(participantWithObservationProperties.properties().get(USER_ACCESS_TOKEN_KEY) instanceof Map)) {
            return Optional.empty();
        }
        return Optional.of(UserAccessTokenWithData.fromMap((Map<String, Object>) participantWithObservationProperties.properties().get(USER_ACCESS_TOKEN_KEY)));
    }

    private void storeUserAccessToken(UserAccessTokenWithData userAccesstokenWithData, List<ParticipantWithObservationProperties> participantWithObservationPropertiesList, ParticipantWithObservationProperties participantWithObservationProperties) {
        Map<String, Object> map = Map.of(USER_ACCESS_TOKEN_KEY, userAccesstokenWithData.toMap());
        participantWithObservationPropertiesList
                .stream()
                .filter(properties -> properties.participantId().equals(participantWithObservationProperties.participantId()))
                .map(ParticipantWithObservationProperties::observationId)
                .forEach(observationId -> studyRepository
                        .setParticipantProperties(
                                participantWithObservationProperties.studyId(),
                                participantWithObservationProperties.participantId(),
                                observationId,
                                map
                        )
                );
    }

    private boolean deleteUserAccessToken(Long studyId, int participantId) {
        List<Observation> observations = studyRepository.filterObservations(
                studyId,
                participantId,
                observation -> observation.type().toLowerCase().contains("garmin"));
        if (observations.isEmpty()) {
            return true;
        }
        try {
            observations.forEach(observation -> studyRepository.removeParticipantPropertyKey(studyId, participantId, observation.observationId(), USER_ACCESS_TOKEN_KEY));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
