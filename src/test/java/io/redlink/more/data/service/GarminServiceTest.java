package io.redlink.more.data.service;

import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.ParticipantKeyValue;
import io.redlink.more.data.model.ParticipantWithObservationProperties;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.garmin.GarminUserAccessToken;
import io.redlink.more.data.model.garmin.UserAccessTokenWithData;
import io.redlink.more.data.properties.GarminProperties;
import io.redlink.more.data.repository.ParticipantKeyValueRepository;
import io.redlink.more.data.repository.StudyRepository;
import io.redlink.more.data.service.garmin.GarminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.redlink.more.data.util.ElasticUtils.Constants.GARMIN_SUMMARY_ID_KEY;
import static io.redlink.more.data.util.ElasticUtils.Constants.GARMIN_SUMMARY_KEYWORD_FIELD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GarminServiceTest {

    @Mock
    private GarminProperties garminProperties;
    @Mock
    private StudyRepository studyRepository;
    @Mock
    private ParticipantKeyValueRepository participantKeyValueRepository;

    @InjectMocks
    private GarminService garminService;

    @Mock
    private ElasticService elasticService;

    private RoutingInfo routingInfo;

    @BeforeEach
    void setUp() {
        routingInfo = new RoutingInfo(1L, 10, 1, Collections.emptySet(), true, true);
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

        given(garminProperties.getRedirectUri()).willReturn("https://app.example/redirect");

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

        given(garminProperties.basicOAuthUri("https://app.example/request")).willReturn(URI.create("https://diauth.garmin.test/oauth?client_id=abc&response_type=code&redirect_uri=https://app.example/redirect"));

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
        verify(participantKeyValueRepository, never()).insert(anyLong(), anyInt(), anyString(), anyMap());
    }

    @Test
    @DisplayName("deleteUserIdAndToken: returns true when no participants hold the userId")
    void deleteUserIdAndToken_returnsTrue_whenNoParticipants() {
        given(participantKeyValueRepository.getByKey("garmin-user-1")).willReturn(List.of());

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
        given(participantKeyValueRepository.getByKey(userId)).willReturn(List.of(p1, p2));

        given(participantKeyValueRepository.delete(1L, 10, userId, Map.of("keyType", "garmin"))).willReturn(true);
        given(participantKeyValueRepository.delete(1L, 11, userId, Map.of("keyType", "garmin"))).willReturn(false);

        Observation garminObs = new Observation(5, 1, "Garmin", "garmin", null, null, null, Instant.now(), Instant.now(), false, false);
        given(studyRepository.filterObservations(eq(1L), anyInt(), any())).willReturn(List.of(garminObs));
        doNothing().when(studyRepository).removeParticipantPropertyKey(anyLong(), anyInt(), anyInt(), anyString());

        boolean result = garminService.deleteUserIdAndToken(userId);

        assertThat(result).isFalse();

        Mockito.reset(studyRepository);
        given(studyRepository.filterObservations(eq(1L), anyInt(), any())).willReturn(List.of(garminObs));
        doNothing().when(studyRepository).removeParticipantPropertyKey(anyLong(), anyInt(), anyInt(), anyString());
        given(participantKeyValueRepository.delete(1L, 10, userId, Map.of("keyType", "garmin"))).willReturn(true);
        given(participantKeyValueRepository.delete(1L, 11, userId, Map.of("keyType", "garmin"))).willReturn(true);

        boolean resultAllOk = garminService.deleteUserIdAndToken(userId);
        assertThat(resultAllOk).isTrue();
    }

    @Test
    @DisplayName("getAllGarminParticipants: groups participants by UserAccessTokenWithData")
    void getAllGarminParticipants_groupsParticipantsByUserAccessToken() throws Exception {
        Observation garminObs = new Observation(100, 1, "Garmin", "garmin", null, null, null,
                Instant.now(), Instant.now(), false, false);

        UserAccessTokenWithData token1 =
                UserAccessTokenWithData.createNewFrom(new GarminUserAccessToken("at1", "rt1", "bearer", 3600, "scope", 7200));
        Map<String, Object> props1 = Map.of(GarminService.USER_ACCESS_TOKEN_KEY, token1.toMap());

        ParticipantWithObservationProperties p1 =
                new ParticipantWithObservationProperties(10, 1L, garminObs.observationId(), props1);
        ParticipantWithObservationProperties p2 =
                new ParticipantWithObservationProperties(11, 1L, garminObs.observationId(), props1);

        UserAccessTokenWithData token2 =
                UserAccessTokenWithData.createNewFrom(new GarminUserAccessToken("at2", "rt2", "bearer", 3600, "scope", 7200));
        Map<String, Object> props2 = Map.of(GarminService.USER_ACCESS_TOKEN_KEY, token2.toMap());

        ParticipantWithObservationProperties p3 =
                new ParticipantWithObservationProperties(12, 1L, garminObs.observationId(), props2);

        given(studyRepository.getParticipantObservationPropertiesByObservationType(anyString()))
                .willReturn(List.of(p1, p2, p3));

        Method method = GarminService.class.getDeclaredMethod("getAllGarminParticipants");
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UserAccessTokenWithData, List<ParticipantWithObservationProperties>> result =
                (Map<UserAccessTokenWithData, List<ParticipantWithObservationProperties>>) method.invoke(garminService);

        assertThat(result).hasSize(2);
        assertThat(result.values())
                .anySatisfy(list -> assertThat(list).containsExactlyInAnyOrder(p1, p2))
                .anySatisfy(list -> assertThat(list).containsExactlyInAnyOrder(p3));

        verify(studyRepository).getParticipantObservationPropertiesByObservationType(anyString());
    }

    @Test
    @DisplayName("deduplicateDataPoints: deletes existing Garmin datapoints by summary id")
    void deduplicateDataPoints_deletesExistingBySummaryId() throws Exception {
        DataPoint dp1 = mock(DataPoint.class);
        DataPoint dp2 = mock(DataPoint.class);

        Map<String, Object> data1 = Map.of(GARMIN_SUMMARY_ID_KEY, "sum-1");
        Map<String, Object> data2 = Map.of(GARMIN_SUMMARY_ID_KEY, "sum-2");

        when(dp1.data()).thenReturn(data1);
        when(dp2.data()).thenReturn(data2);

        RoutingInfo routingInfo = new RoutingInfo(1L, 10, 1, Collections.emptySet(), true, true);

        when(elasticService.deleteDataPoints(
                eq(routingInfo),
                anyString(),
                any(Set.class)
        )).thenReturn(2L);

        Method method = GarminService.class.getDeclaredMethod("deduplicateDataPoints", List.class, RoutingInfo.class);
        method.setAccessible(true);
        method.invoke(garminService, List.of(dp1, dp2), routingInfo);

        verify(elasticService).deleteDataPoints(
                eq(routingInfo),
                eq(GARMIN_SUMMARY_KEYWORD_FIELD),
                eq(Set.of("sum-1", "sum-2"))
        );
    }

    @Test
    @DisplayName("refreshAllTokens: completes without error when there are no Garmin participants")
    void refreshAllTokens_doesNothingWhenNoParticipants() {
        given(studyRepository.getParticipantObservationPropertiesByObservationType(anyString()))
                .willReturn(List.of());

        garminService.refreshAllTokens();

        verify(studyRepository).getParticipantObservationPropertiesByObservationType(anyString());
        verifyNoInteractions(participantKeyValueRepository);
    }
}