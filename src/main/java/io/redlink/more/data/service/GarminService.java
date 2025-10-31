package io.redlink.more.data.service;

import io.redlink.more.data.configuration.GarminConfiguration;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.ParticipantWithObservationProperties;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.garmin.GarminAuthenticationValues;
import io.redlink.more.data.model.garmin.GarminUserAccessToken;
import io.redlink.more.data.model.garmin.UserAccessTokenWithData;
import io.redlink.more.data.properties.GarminProperties;
import io.redlink.more.data.repository.StudyRepository;
import io.redlink.more.data.util.GarminUtils;
import org.springframework.beans.factory.annotation.Autowired;
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
public class GarminService {
    public final static String AUTH_VALUES_KEY = "authenticationValues";
    public final static String USER_ACCESS_TOKEN_KEY = "userAccessToken";
    private final GarminConfiguration garminConfiguration;
    private final RestTemplate restTemplate;
    private final StudyRepository studyRepository;

    public GarminService(@Autowired GarminConfiguration garminConfiguration, StudyRepository studyRepository, GarminProperties garminProperties) {
        this.garminConfiguration = garminConfiguration;
        this.studyRepository = studyRepository;
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

    public Void ssoCallback(String state, String code) {
        var participantWithObservationPropertiesList = studyRepository.getParticipantByGarminStatus(state);
        if (participantWithObservationPropertiesList.isEmpty()) {
            throw new IllegalStateException("No Garmin Status found for state " + state);
        }

        var participantWithObservationProperties = participantWithObservationPropertiesList.stream().findFirst().orElseThrow();
        var garminAccessToken = queryUserAccessToken(participantWithObservationProperties, code);
        if (garminAccessToken.isEmpty()) {
            throw new IllegalStateException("No Garmin Access Token found for " + participantWithObservationPropertiesList.get(0).participantId());
        }
        participantWithObservationPropertiesList
                .stream()
                .filter(properties -> properties.participantId().equals(participantWithObservationProperties.participantId()))
                .map(ParticipantWithObservationProperties::observationId)
                .forEach(observationId -> studyRepository
                        .setParticipantProperties(
                                participantWithObservationProperties.studyId(),
                                participantWithObservationProperties.participantId(),
                                observationId,
                                Map.of(USER_ACCESS_TOKEN_KEY, UserAccessTokenWithData.createNewFrom(garminAccessToken.get()).toMap())
                        )
                );

        return null;
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
        try {
            var properties = studyRepository.getParticipantObservationPropertiesByKeyExists(routingInfo.studyId(), routingInfo.participantId(), USER_ACCESS_TOKEN_KEY);
            if (properties.isEmpty()) {
                return false;
            }
            var userAccessData = getUserAccessData(properties.get(0));
            return userAccessData.isPresent() && userAccessData.get().isAccessTokenValid();
        } catch (RuntimeException e) {
            return false;
        }
    }


    private Optional<UserAccessTokenWithData> getUserAccessData(ParticipantWithObservationProperties participantWithObservationProperties) {
        if (!participantWithObservationProperties.properties().containsKey(USER_ACCESS_TOKEN_KEY) || !(participantWithObservationProperties.properties().get(USER_ACCESS_TOKEN_KEY) instanceof Map)) {
            return Optional.empty();
        }
        return Optional.of(UserAccessTokenWithData.fromMap((Map<String, Object>) participantWithObservationProperties.properties().get(USER_ACCESS_TOKEN_KEY)));
    }
}
