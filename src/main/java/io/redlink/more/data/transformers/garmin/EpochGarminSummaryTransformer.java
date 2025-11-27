package io.redlink.more.data.transformers.garmin;

import io.redlink.more.data.custom.model.GarminDataPoint;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.DataType;
import io.redlink.more.data.model.garmin.GarminSummaryType;
import io.redlink.more.data.model.garmin.transformation.GarminActivityModel;
import io.redlink.more.data.model.garmin.transformation.GarminTimeData;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class EpochGarminSummaryTransformer extends AbstractGarminTransformer {
    @Override
    public GarminSummaryType getSupportedType() {
        return GarminSummaryType.EPOCHS;
    }

    @Override
    public List<DataPoint> transform(String observationId, String observationType, GarminDataPoint garminDataPoint) {
        if (garminDataPoint.getActivityType() != null
                && garminDataPoint.getMet() != null
                && garminDataPoint.getMet() >= 0
                && garminDataPoint.getIntensity() != null) {
            var data = extractActivityData(garminDataPoint);
            return data.entrySet()
                    .stream()
                    .map(entry -> transformGarminTimeDataToDataPoint(
                            observationId,
                            observationType,
                            garminDataPoint.getSummaryId(),
                            entry.getKey(),
                            entry.getValue()
                    )).toList();
        }
        return Collections.emptyList();
    }

    private Map<DataType, GarminTimeData<GarminActivityModel>> extractActivityData(GarminDataPoint garminDataPoint) {
        var activityModel = new GarminActivityModel(
                garminDataPoint.getActivityType(),
                garminDataPoint.getMet(),
                garminDataPoint.getIntensity(),
                garminDataPoint.getActiveTimeInSeconds(),
                garminDataPoint.getMeanMotionIntensity(),
                garminDataPoint.getMaxMotionIntensity());
        var startTime = recordingTimestamp(garminDataPoint);
        var endTime = endDateTime(garminDataPoint);
        var startPoint = new GarminTimeData<>(
                startTime.toInstant(),
                activityModel
        );
        Map<String, Object> additionalData = new HashMap<>();
        additionalData.put("startTime", startTime.toInstant());
        var endPoint = new GarminTimeData<>(endTime.toInstant(), activityModel, additionalData);
        return Map.of(DataType.ACTIVITY_START, startPoint, DataType.ACTIVITY_END, endPoint);
    }
}
