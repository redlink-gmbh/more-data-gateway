package io.redlink.more.data.util.garmin;

import io.redlink.more.data.custom.model.GarminDataPoint;
import io.redlink.more.data.health.model.StepData;
import io.redlink.more.data.model.garmin.transformation.GarminTimeData;
import io.redlink.more.data.util.MapperUtils;

import java.time.Instant;

public class GarminStepDataUtils {
    public static GarminTimeData<StepData> getStepData(Instant endDateTime, GarminDataPoint garminDataPoint) {
        StepData stepData = MapperUtils.convertValue(garminDataPoint, StepData.class);
        return new GarminTimeData<>(endDateTime, stepData);
    }
}
