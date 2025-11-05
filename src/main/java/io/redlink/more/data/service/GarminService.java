package io.redlink.more.data.service;

import io.redlink.more.data.configuration.GarminConfiguration;
import io.redlink.more.data.event.DeregistrationSpringEvent;
import io.redlink.more.data.garmin.wellness.ApiClient;
import io.redlink.more.data.garmin.wellness.client.UserApiApi;
import io.redlink.more.data.garmin.wellness.client.UserControllerApi;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.ParticipantKeyValue;
import io.redlink.more.data.model.ParticipantWithObservationProperties;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.garmin.GarminAuthenticationValues;
import io.redlink.more.data.model.garmin.GarminUserAccessToken;
import io.redlink.more.data.model.garmin.UserAccessTokenWithData;
import io.redlink.more.data.repository.ParticipantKeyValueRepository;
import io.redlink.more.data.repository.StudyRepository;
import io.redlink.more.data.util.GarminUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class GarminService implements ApplicationListener<DeregistrationSpringEvent> {
    private final Logger LOG = LoggerFactory.getLogger(GarminService.class);
    public final static String AUTH_VALUES_KEY = "authenticationValues";
    public final static String USER_ACCESS_TOKEN_KEY = "userAccessToken";
    private final static String USER_ID_TYPE_KEY = "keyType";
    private final static String USER_PERMISSIONS_KEY = "permissions";
    private final static String GARMIN_KEY_TYPE = "garmin";
    private final GarminConfiguration garminConfiguration;
    private final RestTemplate restTemplate;
    private final StudyRepository studyRepository;
    private final ParticipantKeyValueRepository participantKeyValueRepository;

    private final UserApiApi userApi;
    private final UserControllerApi userControllerApi;

    public GarminService(GarminConfiguration garminConfiguration, StudyRepository studyRepository, ParticipantKeyValueRepository participantKeyValueRepository, UserApiApi userApi, UserControllerApi userControllerApi) {
        this.garminConfiguration = garminConfiguration;
        this.studyRepository = studyRepository;
        this.participantKeyValueRepository = participantKeyValueRepository;
        this.userControllerApi = userControllerApi;
        this.restTemplate = new RestTemplate();
        this.userApi = userApi;
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
        } catch (RuntimeException e) {
            LOG.error("Error while requesting OAuth URL", e);
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
        addAuthorizationHeader(userApi.getApiClient(), userAccesstokenWithData);
        addAuthorizationHeader(userControllerApi.getApiClient(), userAccesstokenWithData);
        storeGarminUserId(participantWithObservationProperties.studyId(), participantWithObservationProperties.participantId());
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
        participants.forEach(participant ->
                participantKeyValueRepository.upsert(
                        participant.studyId(),
                        participant.participantId(),
                        userId,
                        Map.of(USER_ID_TYPE_KEY, GARMIN_KEY_TYPE, USER_PERMISSIONS_KEY, permissions)
                )
        );
        return true;
    }

    public void deregisterParticipant(RoutingInfo routingInfo) {
        try {
            var data = getUserAccessData(routingInfo.studyId(), routingInfo.participantId());
            if (data.isPresent()) {
                sendDeregistration(data.get());
                deleteUserAccessToken(routingInfo.studyId(), routingInfo.participantId());
            }

            var participantWithKeyValue = keyValuesForParticipant(routingInfo.studyId(), routingInfo.participantId());
            participantWithKeyValue.forEach(participantKeyValue ->
                    deleteGarminUserId(routingInfo.studyId(), routingInfo.participantId(), participantKeyValue.key())
            );
        } catch (RuntimeException e) {
            LOG.error("Could not deregister Garmin User:", e);
        }
    }

    private void storeGarminUserId(Long studyId, int participantId) {
        var userId = getUserId();
        if (userId.isEmpty()) {
            throw new IllegalStateException("No Garmin User ID found for " + participantId);
        }
        var permissions = getPermissions();
        Map<String, Object> data = Map.of(USER_ID_TYPE_KEY, "garmin", USER_PERMISSIONS_KEY, permissions);
        participantKeyValueRepository.upsert(studyId, participantId, userId.get(), data);
    }

    private Optional<String> getUserId() {
        var response = userApi.uSERID();
        return Optional.ofNullable(response.getUserId());
    }

    private List<String> getPermissions() {
        var response = userControllerApi.gETUSERPERMISSIONS();
        return response.getPermissions();
    }

    private List<ParticipantKeyValue> participantForGarminUserId(String garminUserId) {
        var result = participantKeyValueRepository.getByKey(garminUserId);
        return result.stream().filter(participantKeyValue ->
                participantKeyValue.value().containsKey(USER_ID_TYPE_KEY)
                        && participantKeyValue.value().get(USER_ID_TYPE_KEY).equals("garmin")
        ).toList();
    }

    private List<ParticipantKeyValue> keyValuesForParticipant(Long studyId, Integer participantId) {
        var result = participantKeyValueRepository.getKeysWithValue(studyId, participantId);
        return result.stream().filter(participantKeyValue ->
                participantKeyValue.value().containsKey(USER_ID_TYPE_KEY)
                        && participantKeyValue.value().get(USER_ID_TYPE_KEY).equals(GARMIN_KEY_TYPE)
        ).toList();
    }

    private boolean deleteGarminUserId(Long studyId, int participantId, String garminUserId) {
        return participantKeyValueRepository.delete(studyId, participantId, garminUserId, Map.of(USER_ID_TYPE_KEY, GARMIN_KEY_TYPE));
    }

    private void sendDeregistration(UserAccessTokenWithData userAccessTokenWithData) {
        addAuthorizationHeader(userApi.getApiClient(), userAccessTokenWithData);
        userApi.dEREG();
    }

    private Optional<GarminUserAccessToken> queryUserAccessToken(ParticipantWithObservationProperties participantWithObservationProperties, String code) {
        if (!participantWithObservationProperties.properties().containsKey(AUTH_VALUES_KEY) || !(participantWithObservationProperties.properties().get(AUTH_VALUES_KEY) instanceof Map)) {
            throw new IllegalStateException("No Garmin Access Token found for " + participantWithObservationProperties.participantId());
        }
        var garminAuthValues = GarminAuthenticationValues.fromMap((Map<String, String>) participantWithObservationProperties.properties().get(AUTH_VALUES_KEY));

        HttpEntity<?> entity = getHttpEntity(code, garminAuthValues);

        ResponseEntity<GarminUserAccessToken> response = restTemplate.exchange(
                garminConfiguration.garminTokenUri(),
                HttpMethod.POST,
                entity,
                GarminUserAccessToken.class
        );

        return Optional.ofNullable(response.getBody());
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
        var userAccessData = getUserAccessData(studyId, participantId);
        return userAccessData.isPresent() && userAccessData.get().isAccessTokenValid();
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
            LOG.error("Error while deleting User Access Token", e);
            return false;
        }
    }

    private void addAuthorizationHeader(ApiClient apiClient, UserAccessTokenWithData userAccessTokenWithData) {
        apiClient.addDefaultHeader(
                HttpHeaders.AUTHORIZATION,
                StringUtils.capitalize(userAccessTokenWithData.accessToken().tokenType()) + " " + userAccessTokenWithData.accessToken().accessToken()
        );
    }

    @Override
    public void onApplicationEvent(DeregistrationSpringEvent event) {
        deregisterParticipant(event.getRoutingInfo());
    }
}
