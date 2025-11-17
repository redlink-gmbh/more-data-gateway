package io.redlink.more.data.transformers.garmin;

import io.redlink.more.data.custom.model.GarminDataPoint;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.DataType;
import io.redlink.more.data.model.garmin.GarminSummaryType;
import io.redlink.more.data.model.garmin.transformation.GarminTimeData;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static io.redlink.more.data.util.StringUtils.sha256;

public abstract class AbstractGarminTransformer {
    public abstract GarminSummaryType getSupportedType();

    public abstract List<DataPoint> transform(String observationId, String observationType, GarminDataPoint garminDataPoint);

    protected DataPoint transformGarminTimeDataToDataPoint(String observationId, String observationType, String summaryId, DataType dataType, GarminTimeData<?> data) {
        return new DataPoint(
                uniqueSummaryId(summaryId, dataType, data.timestamp()),
                observationId,
                observationType,
                dataType.name(),
                Instant.now(),
                data.timestamp(),
                data.dataToMap(dataType.dataType)
        );
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

    private String uniqueSummaryId(String summaryId, DataType dataType, Instant timestamp) {
        String base = summaryId + "_" + dataType.name() + "_" + timestamp.toEpochMilli();
        String uuid = UUID.randomUUID().toString();
        String hash = sha256(uuid);

        return base + "_" + hash;
    }
}
