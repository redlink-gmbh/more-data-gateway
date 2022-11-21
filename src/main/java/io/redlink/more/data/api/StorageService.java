package io.redlink.more.data.api;

import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.RoutingInfo;

import java.util.List;

public interface StorageService {
    /**
     *
     * @param dataBulk a list of {@link DataPoint}
     * @param routingInfo study and participant id
     * @return a list of successfully stored datapoint ids
     */
    List<String> storeDataPoints(final List<DataPoint> dataBulk, final RoutingInfo routingInfo);
}
