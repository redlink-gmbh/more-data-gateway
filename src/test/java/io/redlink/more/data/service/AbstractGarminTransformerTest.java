package io.redlink.more.data.service;

import io.redlink.more.data.custom.model.GarminDataPoint;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.DataType;
import io.redlink.more.data.model.garmin.GarminSummaryType;
import io.redlink.more.data.model.garmin.transformation.GarminTimeData;
import io.redlink.more.data.transformers.garmin.AbstractGarminTransformer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AbstractGarminTransformerTest {

    /**
     * Simple concrete subclass to expose the protected / private behaviour for testing.
     */
    private static class TestGarminTransformer extends AbstractGarminTransformer {

        @Override
        public GarminSummaryType getSupportedType() {
            return null;
        }

        @Override
        public List<DataPoint> transform(String observationId, String observationType, GarminDataPoint garminDataPoint) {
            return List.of();
        }

        DataPoint exposeTransformTimeData(String observationId,
                                          String observationType,
                                          String summaryId,
                                          DataType dataType,
                                          GarminTimeData<?> timeData) {
            return transformGarminTimeDataToDataPoint(observationId, observationType, summaryId, dataType, timeData);
        }

        OffsetDateTime exposeRecordingTimestamp(GarminDataPoint garminDataPoint) {
            return recordingTimestamp(garminDataPoint);
        }

        OffsetDateTime exposeEndDateTime(GarminDataPoint garminDataPoint) {
            return endDateTime(garminDataPoint);
        }
    }

    private final TestGarminTransformer transformer = new TestGarminTransformer();

    @Test
    @DisplayName("recordingTimestamp: builds OffsetDateTime from startTimeInSeconds and offset")
    void recordingTimestamp_buildsCorrectOffsetDateTime() {
        GarminDataPoint garminDataPoint = mock(GarminDataPoint.class);

        int startTimeInSeconds = 1_600_000_000;
        int offsetSeconds = 3600; // +01:00

        when(garminDataPoint.getStartTimeInSeconds()).thenReturn(startTimeInSeconds);
        when(garminDataPoint.getStartTimeOffsetInSeconds()).thenReturn(offsetSeconds);

        OffsetDateTime result = transformer.exposeRecordingTimestamp(garminDataPoint);

        Instant expectedInstant = Instant.ofEpochSecond(startTimeInSeconds);
        ZoneOffset expectedOffset = ZoneOffset.ofTotalSeconds(offsetSeconds);

        assertThat(result.toInstant()).isEqualTo(expectedInstant);
        assertThat(result.getOffset()).isEqualTo(expectedOffset);
    }

    @Test
    @DisplayName("endDateTime: adds durationInSeconds to startTimeInSeconds and uses same offset")
    void endDateTime_buildsCorrectOffsetDateTime() {
        GarminDataPoint garminDataPoint = mock(GarminDataPoint.class);

        int startTimeInSeconds = 1_600_000_000;
        int durationInSeconds = 600;
        int offsetSeconds = 0; // UTC

        when(garminDataPoint.getStartTimeInSeconds()).thenReturn(startTimeInSeconds);
        when(garminDataPoint.getDurationInSeconds()).thenReturn(durationInSeconds);
        when(garminDataPoint.getStartTimeOffsetInSeconds()).thenReturn(offsetSeconds);

        OffsetDateTime result = transformer.exposeEndDateTime(garminDataPoint);

        Instant expectedInstant = Instant.ofEpochSecond(startTimeInSeconds + durationInSeconds);
        ZoneOffset expectedOffset = ZoneOffset.ofTotalSeconds(offsetSeconds);

        assertThat(result.toInstant()).isEqualTo(expectedInstant);
        assertThat(result.getOffset()).isEqualTo(expectedOffset);
    }

    @Test
    @DisplayName("transformGarminTimeDataToDataPoint: builds DataPoint with correct fields and unique id")
    void transformGarminTimeDataToDataPoint_buildsCorrectDataPoint() throws Exception {
        String observationId = "obs-123";
        String observationType = "garmin_test";
        String summaryId = "summary-xyz";

        DataType dataType = DataType.HEARTRATE;

        Instant timestamp = Instant.parse("2023-01-01T12:00:00Z");

        @SuppressWarnings("unchecked")
        GarminTimeData<Object> timeData = (GarminTimeData<Object>) mock(GarminTimeData.class);
        when(timeData.timestamp()).thenReturn(timestamp);
        when(timeData.dataToMap(any())).thenReturn(Map.of("someKey", "someValue"));

        Instant before = Instant.now();
        DataPoint dataPoint1 = transformer.exposeTransformTimeData(
                observationId, observationType, summaryId, dataType, timeData
        );
        Instant after = Instant.now();

        Field idField = DataPoint.class.getDeclaredField("datapointId");
        Field obsIdField = DataPoint.class.getDeclaredField("observationId");
        Field obsTypeField = DataPoint.class.getDeclaredField("observationType");
        Field dataTypeField = DataPoint.class.getDeclaredField("dataType");
        Field createdAtField = DataPoint.class.getDeclaredField("serverTime");
        Field timestampField = DataPoint.class.getDeclaredField("effectiveDateTime");
        Field dataField = DataPoint.class.getDeclaredField("data");

        idField.setAccessible(true);
        obsIdField.setAccessible(true);
        obsTypeField.setAccessible(true);
        dataTypeField.setAccessible(true);
        createdAtField.setAccessible(true);
        timestampField.setAccessible(true);
        dataField.setAccessible(true);

        String id1 = (String) idField.get(dataPoint1);
        String obsId = (String) obsIdField.get(dataPoint1);
        String obsType = (String) obsTypeField.get(dataPoint1);
        String storedDataType = (String) dataTypeField.get(dataPoint1);
        Instant createdAt = (Instant) createdAtField.get(dataPoint1);
        Instant storedTimestamp = (Instant) timestampField.get(dataPoint1);
        @SuppressWarnings("unchecked")
        Map<String, Object> storedData = (Map<String, Object>) dataField.get(dataPoint1);

        assertThat(obsId).isEqualTo(observationId);
        assertThat(obsType).isEqualTo(observationType);
        assertThat(storedDataType).isEqualTo(dataType.name());
        assertThat(storedTimestamp).isEqualTo(timestamp);
        assertThat(storedData).containsEntry("someKey", "someValue");

        assertThat(createdAt).isBetween(before.minusSeconds(5), after.plusSeconds(5));
        
        DataPoint dataPoint2 = transformer.exposeTransformTimeData(
                observationId, observationType, summaryId, dataType, timeData
        );
        String id2 = (String) idField.get(dataPoint2);

        assertThat(id2).isNotEqualTo(id1);
    }
}