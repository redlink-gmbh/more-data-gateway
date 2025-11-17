package io.redlink.more.data.service;

import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.DataType;
import io.redlink.more.data.model.garmin.transformation.GarminTimeData;
import io.redlink.more.data.transformers.garmin.AbstractGarminTransformer;
import io.redlink.more.data.transformers.garmin.DailiesGarminSummaryTransformer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DailiesGarminSummaryTransformerTest {

    private final DailiesGarminSummaryTransformer transformer = new DailiesGarminSummaryTransformer();

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


        var method = AbstractGarminTransformer.class.getDeclaredMethod(
                "transformGarminTimeDataToDataPoint",
                String.class,
                String.class,
                String.class,
                DataType.class,
                GarminTimeData.class
        );
        method.setAccessible(true);

        DataPoint result = (DataPoint) method.invoke(
                transformer, // instance of the subclass
                observationId,
                observationType,
                dataId,
                dataType,
                garminTimeData
        );

        assertThat(result).isNotNull();
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