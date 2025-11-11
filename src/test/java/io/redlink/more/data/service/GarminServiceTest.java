package io.redlink.more.data.service;

import io.redlink.more.data.configuration.GarminConfiguration;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.ParticipantKeyValue;
import io.redlink.more.data.model.ParticipantWithObservationProperties;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.garmin.GarminUserAccessToken;
import io.redlink.more.data.model.garmin.UserAccessTokenWithData;
import io.redlink.more.data.repository.ParticipantKeyValueRepository;
import io.redlink.more.data.repository.StudyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
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
    private ParticipantKeyValueRepository participantKeyValueRepository;

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
    void createDataPoint_returnsEmpty_whenSummaryUserAndStartArePresent() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Map<String, Object> data = new HashMap<>();
        data.put("summaryId", "sum-123");
        data.put("startTimeInSeconds", 1_700_000_000L);

        Method method = GarminService.class.getDeclaredMethod(
                "createDataPointFromGarminData",
                String.class, String.class, String.class, Map.class
        );
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        var result = (Optional<DataPoint>) method.invoke(
                garminService,
                "obs-1", "activity", "dailies", data
        );
        assertTrue(result.isEmpty(), "Expected Optional.empty() when all three keys are present");
    }

    @Test
    void createDataPoint_createsDataPoint_andTransformsHrSamples_whenDailies() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        long start = 1_700_000_000L;           // base unix ts
        long tzOffset = 3600L;                 // +1h
        long expectedRecorded = start + tzOffset;

        Map<String, Object> data = new HashMap<>();
        data.put("summaryId", "sum-999");      // keep userId absent to bypass early-return
        data.put("startTimeInSeconds", start);
        data.put("startTimeOffsetInSeconds", tzOffset);
        data.put("userId", "user-xyz");
        // raw HR samples keyed by *offset seconds* as Strings
        Map<String, Number> hr = new HashMap<>();
        hr.put("0", 70);
        hr.put("5", 71);
        hr.put("25", 73);
        data.put("timeOffsetHeartRateSamples", hr);

        Method method = GarminService.class.getDeclaredMethod(
                "createDataPointFromGarminData",
                String.class, String.class, String.class, Map.class
        );
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        var result = (Optional<DataPoint>) method.invoke(
                garminService,
                "obs-42", "wellness", "dailies", data
        );

        assertTrue(result.isPresent(), "Expected a DataPoint to be created");
        DataPoint dp = result.get();

        assertEquals("sum-999", dp.datapointId());
        assertEquals("obs-42", dp.observationId());
        assertEquals("wellness", dp.observationType());
        assertEquals("dailies", dp.dataType());

        assertEquals(Instant.ofEpochSecond(expectedRecorded), dp.effectiveDateTime());

        Map<String, Object> storedData = dp.data();
        assertFalse(storedData.containsKey("summaryId"), "summaryId should be removed from stored data");
        assertFalse(storedData.containsKey("userId"), "userId should be removed from stored data");

        assertTrue(storedData.containsKey("timeOffsetHeartRateSamples"));
        Object transformed = storedData.get("timeOffsetHeartRateSamples");
        assertInstanceOf(List.class, transformed);

        @SuppressWarnings("unchecked")
        List<Map<Long, Short>> groups = (List<Map<Long, Short>>) transformed;

        // With offsets 0,5,25 and a 15s grouping window: [0,5] in first map; [25] in second.
        assertEquals(2, groups.size());

        Map<Long, Short> g1 = groups.get(0);
        Map<Long, Short> g2 = groups.get(1);
        assertEquals(2, g1.size());
        assertEquals(1, g2.size());

        assertEquals(Short.valueOf((short) 70), g1.get(expectedRecorded + 0));
        assertEquals(Short.valueOf((short) 71), g1.get(expectedRecorded + 5));
        assertEquals(Short.valueOf((short) 73), g2.get(expectedRecorded + 25));
    }

    @Test
    void transformHRTimeoffset_returnsEmpty_onNullOrEmptyInput() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method method = GarminService.class.getDeclaredMethod(
                "transformHRTimeoffset",
                Long.class, Map.class, Integer.class
        );
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Map<Long, Short>> nullResult = (List<Map<Long, Short>>) method.invoke(
                garminService,
                1_700_000_000L, null, 15
        );
        assertNotNull(nullResult);
        assertTrue(nullResult.isEmpty());

        @SuppressWarnings("unchecked")
        List<Map<Long, Short>> emptyResult = (List<Map<Long, Short>>) method.invoke(
                garminService,
                1_700_000_000L, Collections.emptyMap(), 15
        );
        assertNotNull(emptyResult);
        assertTrue(emptyResult.isEmpty());
    }


    @Test
    void transformHRTimeoffset_groupsByLessThan15Seconds_window() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        long base = 1_000L;
        Map<String, Short> hr = new HashMap<>();
        hr.put("0", (short) 60);
        hr.put("5", (short) 61);
        hr.put("10", (short) 62);
        hr.put("25", (short) 63);
        hr.put("30", (short) 64);
        // Third group: 46 (since 46 - 30 = 16 => new group)
        hr.put("46", (short) 65);
        Method method = GarminService.class.getDeclaredMethod(
                "transformHRTimeoffset",
                Long.class, Map.class, Integer.class
        );
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Map<Long, Short>> groups = (List<Map<Long, Short>>) method.invoke(
                garminService,
                base, hr, 15
        );

        assertEquals(3, groups.size(), "Expected three groups by 15s rule");

        Map<Long, Short> g1 = groups.get(0);
        Map<Long, Short> g2 = groups.get(1);
        Map<Long, Short> g3 = groups.get(2);

        assertEquals(3, g1.size());
        assertEquals(2, g2.size());
        assertEquals(1, g3.size());

        assertEquals(Short.valueOf((short) 60), g1.get(base + 0));
        assertEquals(Short.valueOf((short) 61), g1.get(base + 5));
        assertEquals(Short.valueOf((short) 62), g1.get(base + 10));

        assertEquals(Short.valueOf((short) 63), g2.get(base + 25));
        assertEquals(Short.valueOf((short) 64), g2.get(base + 30));

        assertEquals(Short.valueOf((short) 65), g3.get(base + 46));
    }


    @Test
    @DisplayName("transformData: creates DataPoints for matching participants and observations")
    void transformData_createsDataPoints_forMatchingParticipants() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        ParticipantKeyValue pkv1 = new ParticipantKeyValue(1L, 10, "garmin-user-1", Map.of("keyType", "garmin"));
        ParticipantKeyValue pkv2 = new ParticipantKeyValue(2L, 20, "garmin-user-2", Map.of("keyType", "garmin"));
        List<ParticipantKeyValue> participantKeyValues = List.of(pkv1, pkv2);

        Observation obs1 = new Observation(100, 1, "Garmin", "garmin", null, null, null, Instant.now(), Instant.now(), false, false);
        Observation obs2 = new Observation(200, 2, "Garmin", "garmin", null, null, null, Instant.now(), Instant.now(), false, false);

        given(studyRepository.filterObservations(eq(1L), eq(10), any())).willReturn(List.of(obs1));
        given(studyRepository.filterObservations(eq(2L), eq(20), any())).willReturn(List.of(obs2));

        Map<String, Object> data1 = new HashMap<>();
        data1.put("userId", "garmin-user-1");
        data1.put("summaryId", "sum-1");
        data1.put("startTimeInSeconds", 1700000000L);

        Map<String, Object> data2 = new HashMap<>();
        data2.put("userId", "garmin-user-2");
        data2.put("summaryId", "sum-2");
        data2.put("startTimeInSeconds", 1700000100L);

        List<Map<String, Object>> dataObjects = List.of(data1, data2);

        Method method = GarminService.class.getDeclaredMethod(
                "transformData",
                List.class, String.class, List.class
        );
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<RoutingInfo, List<DataPoint>> result = (Map<RoutingInfo, List<DataPoint>>) method.invoke(
                garminService,
                participantKeyValues, "activities", dataObjects
        );

        assertNotNull(result);
        assertEquals(2, result.size(), "Expected 2 routing info entries");

        RoutingInfo ri1 = result.keySet().stream()
                .filter(ri -> ri.studyId() == 1L && ri.participantId() == 10)
                .findFirst()
                .orElseThrow();
        RoutingInfo ri2 = result.keySet().stream()
                .filter(ri -> ri.studyId() == 2L && ri.participantId() == 20)
                .findFirst()
                .orElseThrow();

        List<DataPoint> dataPoints1 = result.get(ri1);
        List<DataPoint> dataPoints2 = result.get(ri2);

        assertEquals(1, dataPoints1.size());
        assertEquals(1, dataPoints2.size());

        DataPoint dp1 = dataPoints1.get(0);
        assertEquals("sum-1", dp1.datapointId());
        assertEquals("100", dp1.observationId());
        assertEquals("garmin", dp1.observationType());
        assertEquals("activities", dp1.dataType());

        DataPoint dp2 = dataPoints2.get(0);
        assertEquals("sum-2", dp2.datapointId());
        assertEquals("200", dp2.observationId());
        assertEquals("garmin", dp2.observationType());
        assertEquals("activities", dp2.dataType());
    }

    @Test
    @DisplayName("transformData: filters out data with mismatched userId")
    void transformData_filtersOutMismatchedUserIds() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        ParticipantKeyValue pkv1 = new ParticipantKeyValue(1L, 10, "garmin-user-1", Map.of("keyType", "garmin"));
        List<ParticipantKeyValue> participantKeyValues = List.of(pkv1);

        Observation obs1 = new Observation(100, 1, "Garmin", "garmin", null, null, null, Instant.now(), Instant.now(), false, false);
        given(studyRepository.filterObservations(eq(1L), eq(10), any())).willReturn(List.of(obs1));

        Map<String, Object> data1 = new HashMap<>();
        data1.put("userId", "garmin-user-999");
        data1.put("summaryId", "sum-1");
        data1.put("startTimeInSeconds", 1700000000L);

        List<Map<String, Object>> dataObjects = List.of(data1);

        Method method = GarminService.class.getDeclaredMethod(
                "transformData",
                List.class, String.class, List.class
        );
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<RoutingInfo, List<DataPoint>> result = (Map<RoutingInfo, List<DataPoint>>) method.invoke(
                garminService,
                participantKeyValues, "activities", dataObjects
        );

        assertEquals(1, result.size());
        RoutingInfo ri = result.keySet().iterator().next();
        assertTrue(result.get(ri).isEmpty(), "Expected no data points for mismatched userId");
    }

    @Test
    @DisplayName("transformData: creates multiple DataPoints when participant has multiple observations")
    void transformData_createsMultipleDataPoints_forMultipleObservations() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        ParticipantKeyValue pkv1 = new ParticipantKeyValue(1L, 10, "garmin-user-1", Map.of("keyType", "garmin"));
        List<ParticipantKeyValue> participantKeyValues = List.of(pkv1);

        Observation obs1 = new Observation(100, 1, "Garmin", "garmin", null, null, null, Instant.now(), Instant.now(), false, false);
        Observation obs2 = new Observation(101, 1, "Garmin", "garmin", null, null, null, Instant.now(), Instant.now(), false, false);
        given(studyRepository.filterObservations(eq(1L), eq(10), any())).willReturn(List.of(obs1, obs2));

        Map<String, Object> data1 = new HashMap<>();
        data1.put("userId", "garmin-user-1");
        data1.put("summaryId", "sum-1");
        data1.put("startTimeInSeconds", 1700000000L);

        List<Map<String, Object>> dataObjects = List.of(data1);

        Method method = GarminService.class.getDeclaredMethod(
                "transformData",
                List.class, String.class, List.class
        );
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<RoutingInfo, List<DataPoint>> result = (Map<RoutingInfo, List<DataPoint>>) method.invoke(
                garminService,
                participantKeyValues, "dailies", dataObjects
        );

        assertEquals(1, result.size());
        RoutingInfo ri = result.keySet().iterator().next();
        List<DataPoint> dataPoints = result.get(ri);

        assertEquals(2, dataPoints.size(), "Expected 2 data points for 2 observations");

        assertTrue(dataPoints.stream().anyMatch(dp -> dp.observationId().equals("100")));
        assertTrue(dataPoints.stream().anyMatch(dp -> dp.observationId().equals("101")));
    }


}
