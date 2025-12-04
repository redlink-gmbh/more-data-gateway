package io.redlink.more.data.transformers.garmin;

import io.redlink.more.data.custom.model.GarminDataPoint;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.DataType;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.garmin.GarminSummaryType;
import io.redlink.more.data.model.garmin.transformation.GarminSleepData;
import io.redlink.more.data.model.garmin.transformation.GarminTimeData;
import io.redlink.more.data.util.MapperUtils;
import org.apache.commons.lang3.Range;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class SleepSummaryTransformer extends AbstractGarminTransformer {
    @Override
    public GarminSummaryType getSupportedType() {
        return GarminSummaryType.SLEEPS;
    }

    @Override
    protected List<DataPoint> transformToDataPoint(List<Observation> observations, GarminDataPoint garminDataPoint) {
        var sleepData = dataToSleep(garminDataPoint);
        return sleepData.entrySet().stream().flatMap(entry ->
                        transformGarminTimeDataToDataPoint(
                                observations,
                                garminDataPoint.getSummaryId(),
                                entry.getKey(),
                                entry.getValue()
                        ).stream())
                .toList();
    }

    @Override
    protected List<DataPoint> filterDataPointByTimeRange(List<Range<Instant>> validTimeRanges, List<DataPoint> dataBulk) {
        return dataBulk;
    }

    private Map<DataType, GarminTimeData<GarminSleepData>> dataToSleep(GarminDataPoint dataPoint) {
        var sleepData = MapperUtils.convertValue(dataPoint, GarminSleepData.class);
        var range = super.getGarminDataPointTimeRange(dataPoint);
        var start = new GarminTimeData<>(range.getMinimum(), sleepData);
        var end = new GarminTimeData<>(range.getMaximum(), sleepData, Map.of(START_TIME_KEY, range.getMinimum()));
        return Map.of(DataType.SLEEP_START, start, DataType.SLEEP_END, end);
    }
}
