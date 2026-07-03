/*
 * Copyright (c) 2022 Redlink GmbH.
 */
package io.redlink.more.data.controller.transformer;

import io.redlink.more.data.api.app.v1.model.DataBulkDTO;
import io.redlink.more.data.api.app.v1.model.EndpointDataBulkDTO;
import io.redlink.more.data.api.app.v1.model.ExternalDataDTO;
import io.redlink.more.data.api.app.v1.model.ObservationDataDTO;
import io.redlink.more.data.model.ApiRoutingInfo;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.util.MapperUtils;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class DataTransformer {

    private DataTransformer() {
    }

    public static List<DataPoint> createDataPoints(DataBulkDTO bulk) {
        final Instant recordingTime = Instant.now();
        return bulk.getDataPoints().stream()
                .map(dp -> createDataPoint(dp, recordingTime))
                .toList();
    }

    public static DataPoint createDataPoint(ObservationDataDTO dataPoint, Instant recordingTime) {
        Instant dateTime = dataPoint.getTimestamp();
        return new DataPoint(
                dataPoint.getDataId(),
                dataPoint.getObservationId(),
                dataPoint.getInstanceId(),
                dataPoint.getObservationType(),
                StringUtils.isBlank(dataPoint.getDataType()) ? dataPoint.getObservationType() : dataPoint.getDataType(),
                recordingTime,
                dateTime,
                MapperUtils.convertValue(dataPoint.getDataValue(), Map.class)
        );
    }

    public static List<DataPoint> createDataPoints(EndpointDataBulkDTO bulk, ApiRoutingInfo routingInfo, Integer observationId) {
        final Instant recordingTime = Instant.now();
        return bulk.getDataPoints().stream()
                .map(dp -> createDataPoint(dp, routingInfo, recordingTime, observationId))
                .toList();
    }

    /**
     * @deprecated use the String observationId with `{type}_{id}` (e.g. `observation_123`)
     */
    @Deprecated
    public static DataPoint createDataPoint(ExternalDataDTO dataPoint, ApiRoutingInfo routingInfo, Instant recordingTime, Integer observationId) {
        return createDataPoint(dataPoint, routingInfo, recordingTime, "observation_" + Objects.requireNonNull(observationId), null);
    }
    public static DataPoint createDataPoint(ExternalDataDTO dataPoint, ApiRoutingInfo routingInfo, Instant recordingTime, String observationId, String instanceId) {
        Instant dateTime = dataPoint.getTimestamp();
        return new DataPoint(
                UUID.randomUUID().toString(),
                Objects.requireNonNull(observationId),
                instanceId,
                routingInfo.observationType(),
                StringUtils.isBlank(dataPoint.getDataType()) ? routingInfo.observationType(): dataPoint.getDataType(),
                Objects.requireNonNull(recordingTime),
                dateTime,
                dataPoint.getDataValue());
    }
}
