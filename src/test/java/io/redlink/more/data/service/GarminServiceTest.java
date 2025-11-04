package io.redlink.more.data.service;

import io.redlink.more.data.configuration.GarminConfiguration;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.ParticipantKeyValue;
import io.redlink.more.data.model.ParticipantWithObservationProperties;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.garmin.GarminUserAccessToken;
import io.redlink.more.data.model.garmin.UserAccessTokenWithData;
import io.redlink.more.data.repository.KeyValueRepository;
import io.redlink.more.data.repository.StudyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GarminServiceTest {

    @Mock
    private GarminConfiguration garminConfiguration;
    @Mock
    private StudyRepository studyRepository;
    @Mock
    private KeyValueRepository keyValueRepository;

    @InjectMocks
    private GarminService garminService;

    private RoutingInfo routingInfo;

    @BeforeEach
    void setUp() {
        routingInfo = new RoutingInfo(1L, 10, 1, true, true);
    }

    @Test
    @DisplayName("getSsoUrl: returns redirectUri immediately when a valid user access token exists")
    void getSsoUrl_returnsRedirect_whenValidToken() {
        Observation garminObs = new Observation(42, 1, "Garmin", "garmin_activity", null, null, null, Instant.now(), Instant.now(), false, false);
        given(studyRepository.filterObservations(eq(routingInfo), eq(true), any())).willReturn(List.of(garminObs));

        UserAccessTokenWithData valid = UserAccessTokenWithData.createNewFrom(new GarminUserAccessToken("at", "rt", "bearer", 3600, "scope", 7200));
        Map<String, Object> props = Map.of(GarminService.USER_ACCESS_TOKEN_KEY, valid.toMap());
        ParticipantWithObservationProperties pwo = new ParticipantWithObservationProperties(routingInfo.participantId(), routingInfo.studyId(), garminObs.observationId(), props);
        given(studyRepository.getParticipantObservationPropertiesByKeyExists(routingInfo.studyId(), routingInfo.participantId(), GarminService.USER_ACCESS_TOKEN_KEY))
                .willReturn(List.of(pwo));

        given(garminConfiguration.getRedirectUri()).willReturn("https://app.example/redirect");

        String url = garminService.getSsoUrl(routingInfo, "https://app.example/request");

        assertThat(url).isEqualTo("https://app.example/redirect");
        verify(studyRepository, never()).setParticipantProperties(anyLong(), anyInt(), anyInt(), anyMap());
    }

    @Test
    @DisplayName("getSsoUrl: builds OAuth URL and stores auth values when no valid token; returns URL even if HTTP fails")
    void getSsoUrl_buildsOauth_andStoresAuthValues_whenNoValidToken() {
        Observation garminObs = new Observation(7, 1, "Garmin", "garmin_connect", null, null, null, Instant.now(), Instant.now(), false, false);
        given(studyRepository.filterObservations(eq(routingInfo), eq(true), any())).willReturn(List.of(garminObs));
        given(studyRepository.getParticipantObservationPropertiesByKeyExists(anyLong(), anyInt(), anyString())).willReturn(List.of());

        given(garminConfiguration.basicOAuthUri()).willReturn(URI.create("https://diauth.garmin.test/oauth?client_id=abc&response_type=code"));

        String url = garminService.getSsoUrl(routingInfo, "https://app.example/request");

        assertThat(url).startsWith("https://diauth.garmin.test/oauth?client_id=abc&response_type=code")
                .contains("code_challenge=")
                .contains("code_challenge_method=S256")
                .contains("state=");
        verify(studyRepository, atLeastOnce()).setParticipantProperties(eq(routingInfo.studyId()), eq(routingInfo.participantId()), eq(garminObs.observationId()), anyMap());
    }

    @Test
    @DisplayName("ssoCallback: throws when no participant is found for state")
    void ssoCallback_throws_whenNoParticipantForState() {
        given(studyRepository.getParticipantByGarminStatus("state-1")).willReturn(List.of());

        assertThatThrownBy(() -> garminService.ssoCallback("state-1", "code"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No Garmin Status");
    }

    @Test
    @DisplayName("ssoCallback: throws when token exchange yields empty (e.g., missing auth values)")
    void ssoCallback_throws_whenTokenExchangeEmpty() {
        Observation garminObs = new Observation(77, 1, "Garmin", "garmin", null, null, null, Instant.now(), Instant.now(), false, false);
        ParticipantWithObservationProperties pwo = new ParticipantWithObservationProperties(10, 1L, garminObs.observationId(), Map.of());
        given(studyRepository.getParticipantByGarminStatus("state-2")).willReturn(List.of(pwo));

        assertThatThrownBy(() -> garminService.ssoCallback("state-2", "code"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No Garmin Access Token");
        verify(studyRepository, never()).setParticipantProperties(anyLong(), anyInt(), anyInt(), anyMap());
        verify(keyValueRepository, never()).insert(anyLong(), anyInt(), anyString(), anyMap());
    }

    @Test
    @DisplayName("deleteUserIdAndToken: returns true when no participants hold the userId")
    void deleteUserIdAndToken_returnsTrue_whenNoParticipants() {
        given(keyValueRepository.getByKey("garmin-user-1")).willReturn(List.of());

        boolean ok = garminService.deleteUserIdAndToken("garmin-user-1");

        assertThat(ok).isTrue();
        verifyNoInteractions(studyRepository);
    }

    @Test
    @DisplayName("deleteUserIdAndToken: returns false if any delete step fails; returns true when all succeed")
    void deleteUserIdAndToken_mixedResults() {
        String userId = "user-xyz";
        ParticipantKeyValue p1 = new ParticipantKeyValue(1L, 10, userId, Map.of("keyType", "garmin"));
        ParticipantKeyValue p2 = new ParticipantKeyValue(1L, 11, userId, Map.of("keyType", "garmin"));
        given(keyValueRepository.getByKey(userId)).willReturn(List.of(p1, p2));

        given(keyValueRepository.delete(1L, 10, userId, Map.of("keyType", "garmin"))).willReturn(true);
        given(keyValueRepository.delete(1L, 11, userId, Map.of("keyType", "garmin"))).willReturn(false);

        Observation garminObs = new Observation(5, 1, "Garmin", "garmin", null, null, null, Instant.now(), Instant.now(), false, false);
        given(studyRepository.filterObservations(eq(1L), anyInt(), any())).willReturn(List.of(garminObs));
        doNothing().when(studyRepository).removeParticipantPropertyKey(anyLong(), anyInt(), anyInt(), anyString());

        boolean result = garminService.deleteUserIdAndToken(userId);

        assertThat(result).isFalse();

        Mockito.reset(studyRepository);
        given(studyRepository.filterObservations(eq(1L), anyInt(), any())).willReturn(List.of(garminObs));
        doNothing().when(studyRepository).removeParticipantPropertyKey(anyLong(), anyInt(), anyInt(), anyString());
        given(keyValueRepository.delete(1L, 10, userId, Map.of("keyType", "garmin"))).willReturn(true);
        given(keyValueRepository.delete(1L, 11, userId, Map.of("keyType", "garmin"))).willReturn(true);

        boolean resultAllOk = garminService.deleteUserIdAndToken(userId);
        assertThat(resultAllOk).isTrue();
    }

}
