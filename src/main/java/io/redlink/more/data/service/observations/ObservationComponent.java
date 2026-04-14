package io.redlink.more.data.service.observations;

import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.RoutingInfo;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public interface ObservationComponent {
    String getObservationType();

    Optional<String> produceUrl(Observation observation, RoutingInfo routingInfo, Instant scheduleStart, Instant scheduleEnd);

    boolean processCallback(Map<String, String> parameters, RoutingInfo routingInfo, Observation observation, Instant scheduleStart, Instant scheduleEnd);
}
