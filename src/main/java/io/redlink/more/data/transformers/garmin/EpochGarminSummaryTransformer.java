package io.redlink.more.data.transformers.garmin;

import io.redlink.more.data.custom.model.GarminDataPoint;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.DataType;
import io.redlink.more.data.model.garmin.GarminSummaryType;
import io.redlink.more.data.model.garmin.transformation.GarminActivityModel;
import io.redlink.more.data.model.garmin.transformation.GarminTimeData;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

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
            return Collections.singletonList(
                    transformGarminTimeDataToDataPoint(
                            observationId,
                            observationType,
                            garminDataPoint.getSummaryId(),
                            DataType.ACTIVITY,
                            data
                    )
            );
        }
        return Collections.emptyList();
    }

    private GarminTimeData<GarminActivityModel> extractActivityData(GarminDataPoint garminDataPoint) {
        var activityModel = new GarminActivityModel(garminDataPoint.getActivityType(), garminDataPoint.getMet(), garminDataPoint.getIntensity());
        var startTime = recordingTimestamp(garminDataPoint);
        var endTime = endDateTime(garminDataPoint);
        return new GarminTimeData<>(
                startTime.toInstant(),
                Optional.of(endTime.toInstant()),
                activityModel
        );
    }
}
