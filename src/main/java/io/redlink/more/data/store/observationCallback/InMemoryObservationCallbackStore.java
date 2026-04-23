package io.redlink.more.data.store.observationCallback;

import io.redlink.more.data.model.ActiveObservation;
import io.redlink.more.data.model.CompletedData;
import io.redlink.more.data.model.RoutingInfo;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Qualifier("inMemory")
public class InMemoryObservationCallbackStore implements ObservationCallbackStore {
    private final Map<String, Map<ActiveObservation, String>> externalRedirects = new ConcurrentHashMap<>();
    private final Map<String, List<CompletedData>> completedDataMap = new ConcurrentHashMap<>();

    @Override
    public void saveRedirect(RoutingInfo routingInfo, ActiveObservation activeObservation, String redirect) {
        this.externalRedirects.computeIfAbsent(routingInfo.participantHash(), k -> Collections.synchronizedMap(new LinkedHashMap<>()))
                .put(activeObservation, redirect);
    }

    @Override
    public Optional<URI> pullRedirect(RoutingInfo routingInfo, int observationId) {
        var observationWithRedirect = externalRedirects.getOrDefault(routingInfo.participantHash(), Collections.emptyMap());
        var lastActiveObservation = observationWithRedirect.keySet().stream()
                .filter(ao -> Integer.parseInt(ao.observationId()) == observationId)
                .findFirst();
        if (lastActiveObservation.isPresent()) {
            String externalRedirect = observationWithRedirect.remove(lastActiveObservation.get());
            markCompleted(routingInfo, lastActiveObservation.get());
            if (observationWithRedirect.isEmpty()) {
                externalRedirects.remove(routingInfo.participantHash());
            } else {
                externalRedirects.put(routingInfo.participantHash(), observationWithRedirect);
            }
            if (externalRedirect != null && !externalRedirect.isBlank()) {
                return Optional.of(URI.create(externalRedirect));
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean isCompleted(RoutingInfo routingInfo, ActiveObservation activeObservation) {
        return completedDataMap.getOrDefault(routingInfo.participantHash(), Collections.emptyList())
                .contains(CompletedData.fromActiveObservation(activeObservation));
    }

    @Override
    public void markCompleted(RoutingInfo routingInfo, ActiveObservation activeObservation) {
        completedDataMap.computeIfAbsent(routingInfo.participantHash(), k -> Collections.synchronizedList(new LinkedList<>()))
                .add(CompletedData.fromActiveObservation(activeObservation));
    }

    @Override
    public List<CompletedData> getCompletedData(RoutingInfo routingInfo) {
        return completedDataMap.getOrDefault(routingInfo.participantHash(), Collections.emptyList());
    }
}
