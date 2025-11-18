package io.redlink.more.data.transformers.garmin;

import io.redlink.more.data.custom.model.GarminDataPoint;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.DataType;
import io.redlink.more.data.model.garmin.GarminSummaryType;
import io.redlink.more.data.model.garmin.transformation.GarminTimeData;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class DailiesGarminSummaryTransformer extends AbstractGarminTransformer {

    @Override
    public GarminSummaryType getSupportedType() {
        return GarminSummaryType.DAILIES;
    }

    @Override
    public List<DataPoint> transform(String observationId, String observationType, GarminDataPoint garminDataPoint) {
        var recordedDateTime = super.recordingTimestamp(garminDataPoint);

        if (garminDataPoint.getTimeOffsetHeartRateSamples() != null
                && !garminDataPoint.getTimeOffsetHeartRateSamples().isEmpty()) {
            return extractHrDataWithThreshold(recordedDateTime, garminDataPoint.getTimeOffsetHeartRateSamples())
                    .stream()
                    .map(data -> super.transformGarminTimeDataToDataPoint(
                            observationId,
                            observationType,
                            garminDataPoint.getSummaryId(),
                            DataType.HEARTRATE,
                            data))
                    .toList();
        }
        return Collections.emptyList();
    }

    private List<GarminTimeData<Integer>> extractHrDataWithThreshold(OffsetDateTime startDateTime, Map<String, Integer> hrTimeOffset) {
        if (hrTimeOffset == null) {
            return Collections.emptyList();
        }

        return hrTimeOffset.entrySet()
                .stream()
                .filter(entry -> entry.getValue() != null)
                .map(entry -> new GarminTimeData<>(
                        Instant.ofEpochSecond(startDateTime.toEpochSecond()
                                + Integer.parseInt(entry.getKey())),
                        entry.getValue()))
                .collect(Collectors.toCollection(ArrayList::new));
    }
}