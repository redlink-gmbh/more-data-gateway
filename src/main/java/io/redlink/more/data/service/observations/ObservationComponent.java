package io.redlink.more.data.service.observations;

import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.RoutingInfo;
import org.apache.commons.lang3.tuple.Pair;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public interface ObservationComponent {
    String getObservationType();

    Optional<String> produceUrl(Observation observation, RoutingInfo routingInfo, Instant scheduleStart, Instant scheduleEnd);

    Optional<Pair<RoutingInfo, Integer>> processCallback(Map<String, String> parameters, RoutingInfo routingInfo, Observation observation);
}
