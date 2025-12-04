package io.redlink.more.data.transformers.garmin;

import io.redlink.more.data.custom.model.GarminDataPoint;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.DataType;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.garmin.GarminSummaryType;
import io.redlink.more.data.model.garmin.transformation.GarminActivityModel;
import io.redlink.more.data.model.garmin.transformation.GarminTimeData;
import org.apache.commons.lang3.Range;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class EpochGarminSummaryTransformer extends AbstractGarminTransformer {
    private static final String START_TIME_KEY = "startTime";

    @Override
    public GarminSummaryType getSupportedType() {
        return GarminSummaryType.EPOCHS;
    }

    @Override
    public List<DataPoint> transformToDataPoint(List<Observation> observations, GarminDataPoint garminDataPoint) {
        if (garminDataPoint.getActivityType() != null
                && garminDataPoint.getMet() != null
                && garminDataPoint.getMet() >= 0
                && garminDataPoint.getIntensity() != null) {
            var data = extractActivityData(garminDataPoint);
            return data.entrySet()
                    .stream()
                    .flatMap(entry -> transformGarminTimeDataToDataPoint(
                            observations,
                            garminDataPoint.getSummaryId(),
                            entry.getKey(),
                            entry.getValue()
                    ).stream()).toList();
        }
        return Collections.emptyList();
    }

    @Override
    protected List<DataPoint> filterDataPointByTimeRange(List<Range<Instant>> validTimeRanges, List<DataPoint> dataBulk) {
        var observationIdsWithOverlap = dataBulk.stream()
                .filter(dataPoint -> dataPoint.data().containsKey(START_TIME_KEY))
                .filter(dataPoint -> {
                    Instant start = (Instant) dataPoint.data().get(START_TIME_KEY);
                    var dataRange = Range.of(start, dataPoint.effectiveDateTime());
                    return validTimeRanges.stream().anyMatch(range -> range.isOverlappedBy(dataRange));
                })
                .toList();

        return dataBulk.stream()
                .filter(dataPoint ->
                        observationIdsWithOverlap.stream()
                                .anyMatch(filteredDataPoint ->
                                        dataPoint.datapointId().equals(filteredDataPoint.datapointId())
                                                || dataPoint.observationId().equals(filteredDataPoint.observationId())
                                                && (dataPoint.effectiveDateTime() == filteredDataPoint.data().get(START_TIME_KEY))))
                .toList();
    }


    private Map<DataType, GarminTimeData<GarminActivityModel>> extractActivityData(GarminDataPoint garminDataPoint) {
        var activityModel = new GarminActivityModel(
                garminDataPoint.getActivityType(),
                garminDataPoint.getMet(),
                garminDataPoint.getIntensity(),
                garminDataPoint.getActiveTimeInSeconds(),
                garminDataPoint.getMeanMotionIntensity(),
                garminDataPoint.getMaxMotionIntensity());
        var range = super.getGarminDataPointTimeRange(garminDataPoint);
        var startPoint = new GarminTimeData<>(
                range.getMinimum(),
                activityModel
        );
        Map<String, Object> additionalData = new HashMap<>();
        additionalData.put(START_TIME_KEY, range.getMinimum());
        var endPoint = new GarminTimeData<>(range.getMaximum(), activityModel, additionalData);
        return Map.of(DataType.ACTIVITY_START, startPoint, DataType.ACTIVITY_END, endPoint);
    }
}
