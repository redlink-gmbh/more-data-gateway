package io.redlink.more.data.transformers.garmin;

import io.redlink.more.data.custom.model.GarminDataPoint;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.DataType;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.garmin.GarminSummaryType;
import io.redlink.more.data.model.garmin.transformation.GarminTimeData;
import io.redlink.more.data.schedule.SchedulerUtils;
import org.apache.commons.lang3.Range;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static io.redlink.more.data.util.StringUtils.sha256;

public abstract class AbstractGarminTransformer {
    protected static final String START_TIME_KEY = "startTime";

    public abstract GarminSummaryType getSupportedType();

    protected abstract List<DataPoint> transformToDataPoint(List<Observation> observations, GarminDataPoint garminDataPoint);

    protected abstract List<DataPoint> filterDataPointByTimeRange(List<Range<Instant>> validTimeRanges, List<DataPoint> dataBulk);

    public List<DataPoint> transform(List<Observation> observations, GarminDataPoint garminDataPoint, Instant participantStart, Instant participantEnd) {
        var validObservations = filterObservations(observations, garminDataPoint, participantStart, participantEnd);
        if (validObservations.isEmpty()) {
            return Collections.emptyList();
        }
        var range = observations
                .stream()
                .flatMap(observation -> SchedulerUtils.parseToObservationSchedules(observation.observationSchedule(), participantStart, participantEnd).stream()).toList();
        var dataPoints = transformToDataPoint(validObservations, garminDataPoint);
        return filterDataPointByTimeRange(range, dataPoints);
    }

    protected List<DataPoint> transformGarminTimeDataToDataPoint(List<Observation> observations, String summaryId, DataType dataType, GarminTimeData<?> data) {
        if (data != null) {
            return observations.stream().map(observation ->
                            new DataPoint(
                                    uniqueSummaryId(summaryId, dataType, data.timestamp()),
                                    String.valueOf(observation.observationId()),
                                    observation.type(),
                                    dataType.name(),
                                    Instant.now(),
                                    data.timestamp(),
                                    data.dataToMap(dataType.dataType, summaryId)
                            )
                    )
                    .toList();
        }
        return Collections.emptyList();
    }

    protected OffsetDateTime recordingTimestamp(GarminDataPoint garminDataPoint) {
        Instant unixTimestamp = Instant.ofEpochSecond(garminDataPoint.getStartTimeInSeconds());
        ZoneOffset offset = ZoneOffset.ofTotalSeconds(garminDataPoint.getStartTimeOffsetInSeconds());
        return unixTimestamp.atOffset(offset);
    }

    protected OffsetDateTime endDateTime(GarminDataPoint garminDataPoint) {
        int endTimestamp = garminDataPoint.getStartTimeInSeconds() + garminDataPoint.getDurationInSeconds();
        Instant unixTimestamp = Instant.ofEpochSecond(endTimestamp);
        ZoneOffset offset = ZoneOffset.ofTotalSeconds(garminDataPoint.getStartTimeOffsetInSeconds());
        return unixTimestamp.atOffset(offset);
    }

    protected Range<Instant> getGarminDataPointTimeRange(GarminDataPoint garminDataPoint) {
        return Range.of(recordingTimestamp(garminDataPoint).toInstant(), endDateTime(garminDataPoint).toInstant());
    }

    private List<Observation> filterObservations(List<Observation> observations, GarminDataPoint garminDataPoint, Instant participantStart, Instant participantEnd) {
        Range<Instant> garminDataTimeRange = getGarminDataPointTimeRange(garminDataPoint);
        return observations
                .stream()
                .filter(observation -> {
                    var instantRanges = SchedulerUtils.parseToObservationSchedules(observation.observationSchedule(), participantStart, participantEnd);
                    return instantRanges.stream().anyMatch(range -> range.isOverlappedBy(garminDataTimeRange));
                })
                .toList();
    }

    private String uniqueSummaryId(String summaryId, DataType dataType, Instant timestamp) {
        String base = summaryId + "_" + dataType.name() + "_" + timestamp.toEpochMilli();
        String uuid = UUID.randomUUID().toString();

        return sha256(base + uuid);
    }
}
