package io.redlink.more.data.service;

import io.redlink.more.data.api.app.v1.model.GarminDataPointDTO;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.DataType;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.ParticipantKeyValue;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.garmin.GarminTimeData;
import io.redlink.more.data.model.garmin.ParticipantGarminDataPoint;
import io.redlink.more.data.repository.StudyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GarminDataTransformationServiceTest {

    @Mock
    private StudyRepository studyRepository;

    @InjectMocks
    private GarminDataTransformationService service;

    private final ParticipantKeyValue participantKey = new ParticipantKeyValue(1L, 1, "abc", Collections.emptyMap());

    @Test
    @DisplayName("transformData(dailies): builds HEARTRATE DataPoints per participant/observation and inserts gap sentinels")
    void transformData_dailiesWithHrSamples_buildsDataPointsAndGapSentinels() {
        Observation garminObs = new Observation(
                1, 1, "Garmin", "some-" + GarminService.GARMIN_KEY_TYPE + "-type",
                null, null, null, Instant.now(), Instant.now(), false, false
        );
        given(studyRepository.filterObservations(eq(1L), eq(1), any()))
                .willReturn(List.of(garminObs));

        Map<String, Integer> hr = new LinkedHashMap<>();
        hr.put("0", 70);
        hr.put("10", 72);
        hr.put("40", 90);
        GarminDataPointDTO dto = new GarminDataPointDTO("abc", "sum-1", (int) Instant.parse("2024-01-01T00:00:00Z").getEpochSecond(), 0, 0);
        dto.setTimeOffsetHeartRateSamples(hr);

        ParticipantGarminDataPoint pgdp = new ParticipantGarminDataPoint(participantKey, List.of(dto));

        Map<RoutingInfo, List<DataPoint>> result = service.transformData("dailies", List.of(pgdp));

        assertThat(result).hasSize(1);
        List<DataPoint> produced = result.values().iterator().next();

        assertThat(produced).hasSize(3);

        assertThat(produced).allSatisfy(dp -> {
            assertThat(dp).isNotNull();
            assertThat(dp.observationType()).contains(GarminService.GARMIN_KEY_TYPE);
            assertThat(dp.dataType()).isEqualTo(DataType.HEARTRATE.name());
            assertThat(dp.datapointId()).isEqualTo("sum-1");
            assertThat(dp.effectiveDateTime()).isNotNull();
            assertThat(dp.serverTime()).isNotNull();
            assertThat(dp.data()).isInstanceOf(Map.class);
        });

        verify(studyRepository, times(1)).filterObservations(eq(1L), eq(1), any());
    }

    @Test
    @DisplayName("transformData: returns empty when summaryType != dailies or when HR samples are empty")
    void transformData_nonDailiesOrNoHr_yieldsEmptyMap() {
        Observation garminObs = new Observation(
                5, 1, "Garmin", "x-" + GarminService.GARMIN_KEY_TYPE + "-y",
                null, null, null, Instant.now(), Instant.now(), false, false
        );
        given(studyRepository.filterObservations(eq(1L), eq(1), any()))
                .willReturn(List.of(garminObs));

        GarminDataPointDTO dto = new GarminDataPointDTO("abc", "sum-1", (int) Instant.parse("2024-01-01T00:00:00Z").getEpochSecond(), 0, 0);
        ParticipantGarminDataPoint pgdp = new ParticipantGarminDataPoint(participantKey, List.of(dto));

        Map<RoutingInfo, List<DataPoint>> notDailies = service.transformData("summary", List.of(pgdp));
        assertThat(notDailies).isEmpty();

        Map<RoutingInfo, List<DataPoint>> emptyHr = service.transformData("dailies", List.of(pgdp));
        assertThat(emptyHr).isEmpty();
    }


    @Test
    @DisplayName("transformGarminTimeDataToDataPoint: correctly transforms GarminTimeData to DataPoint")
    void transformGarminTimeDataToDataPoint_correctlyTransformsData() throws Exception {
        String observationId = "obs-123";
        String observationType = "garmin-heartrate-type";
        String dataId = "data-456";
        DataType dataType = DataType.HEARTRATE;
        Instant timestamp = Instant.parse("2024-01-15T10:30:00Z");
        Integer heartRateValue = 75;

        GarminTimeData<Integer> garminTimeData = new GarminTimeData<>(timestamp, heartRateValue);

        var method = GarminDataTransformationService.class.getDeclaredMethod(
                "transformGarminTimeDataToDataPoint",
                String.class, String.class, String.class, DataType.class, GarminTimeData.class
        );
        method.setAccessible(true);

        DataPoint result = (DataPoint) method.invoke(
                service,
                observationId,
                observationType,
                dataId,
                dataType,
                garminTimeData
        );

        assertThat(result).isNotNull();
        assertThat(result.datapointId()).isEqualTo(dataId);
        assertThat(result.observationId()).isEqualTo(observationId);
        assertThat(result.observationType()).isEqualTo(observationType);
        assertThat(result.dataType()).isEqualTo(dataType.name());
        assertThat(result.effectiveDateTime()).isEqualTo(timestamp);
        assertThat(result.serverTime()).isNotNull();
        assertThat(result.data()).isNotNull();
        assertThat(result.data()).isInstanceOf(Map.class);

        Map<String, Object> dataMap = result.data();
        assertThat(dataMap.get(dataType.dataType)).isEqualTo(heartRateValue);
    }
}