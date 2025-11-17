package io.redlink.more.data.service;

import io.redlink.more.data.custom.model.GarminDataPoint;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.garmin.GarminSummaryType;
import io.redlink.more.data.transformers.garmin.EpochGarminSummaryTransformer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;


class EpochGarminSummaryTransformerTest {

    private final EpochGarminSummaryTransformer transformer = new EpochGarminSummaryTransformer();

    @Test
    @DisplayName("getSupportedType returns EPOCHS")
    void getSupportedType_returnsEpochs() {
        assertThat(transformer.getSupportedType()).isEqualTo(GarminSummaryType.EPOCHS);
    }

    @Test
    @DisplayName("transform: returns single ACTIVITY DataPoint when all required fields are present and valid")
    void transform_returnsDataPoint_whenValidEpoch() {
        String observationId = "obs-1";
        String observationType = "garmin_epochs";

        GarminDataPoint garminDataPoint = mock(GarminDataPoint.class);
        when(garminDataPoint.getActivityType()).thenReturn(GarminDataPoint.ActivityTypeEnum.SEDENTARY);
        when(garminDataPoint.getMet()).thenReturn(1.5);
        when(garminDataPoint.getIntensity()).thenReturn(GarminDataPoint.IntensityEnum.SEDENTARY);

        when(garminDataPoint.getSummaryId()).thenReturn("summary-123");
        when(garminDataPoint.getStartTimeInSeconds()).thenReturn(1_600_000_000);
        when(garminDataPoint.getStartTimeOffsetInSeconds()).thenReturn(0);    // UTC
        when(garminDataPoint.getDurationInSeconds()).thenReturn(600);        // 10 min

        List<DataPoint> result = transformer.transform(observationId, observationType, garminDataPoint);

        assertThat(result).hasSize(2);

        verify(garminDataPoint, atLeastOnce()).getActivityType();
        verify(garminDataPoint, atLeastOnce()).getMet();
        verify(garminDataPoint, atLeastOnce()).getIntensity();
    }

    @Test
    @DisplayName("transform: returns empty list when activityType is null")
    void transform_returnsEmpty_whenActivityTypeNull() {
        GarminDataPoint garminDataPoint = mock(GarminDataPoint.class);
        when(garminDataPoint.getActivityType()).thenReturn(null);
        when(garminDataPoint.getMet()).thenReturn(1.5);
        when(garminDataPoint.getIntensity()).thenReturn(GarminDataPoint.IntensityEnum.ACTIVE);

        List<DataPoint> result = transformer.transform("obs", "type", garminDataPoint);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("transform: returns empty list when MET is null")
    void transform_returnsEmpty_whenMetNull() {
        GarminDataPoint garminDataPoint = mock(GarminDataPoint.class);
        when(garminDataPoint.getActivityType()).thenReturn(GarminDataPoint.ActivityTypeEnum.WALKING);
        when(garminDataPoint.getMet()).thenReturn(null);
        when(garminDataPoint.getIntensity()).thenReturn(GarminDataPoint.IntensityEnum.ACTIVE);

        List<DataPoint> result = transformer.transform("obs", "type", garminDataPoint);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("transform: returns empty list when MET is negative")
    void transform_returnsEmpty_whenMetNegative() {
        GarminDataPoint garminDataPoint = mock(GarminDataPoint.class);
        when(garminDataPoint.getActivityType()).thenReturn(GarminDataPoint.ActivityTypeEnum.RUNNING);
        when(garminDataPoint.getMet()).thenReturn(-0.1);
        when(garminDataPoint.getIntensity()).thenReturn(GarminDataPoint.IntensityEnum.HIGHLY_ACTIVE);

        List<DataPoint> result = transformer.transform("obs", "type", garminDataPoint);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("transform: returns empty list when intensity is null")
    void transform_returnsEmpty_whenIntensityNull() {
        GarminDataPoint garminDataPoint = mock(GarminDataPoint.class);
        when(garminDataPoint.getActivityType()).thenReturn(GarminDataPoint.ActivityTypeEnum.WALKING);
        when(garminDataPoint.getMet()).thenReturn(2.0);
        when(garminDataPoint.getIntensity()).thenReturn(null);

        List<DataPoint> result = transformer.transform("obs", "type", garminDataPoint);

        assertThat(result).isEmpty();
    }
}