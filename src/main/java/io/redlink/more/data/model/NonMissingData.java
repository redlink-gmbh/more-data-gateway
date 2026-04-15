package io.redlink.more.data.model;

import java.io.Serializable;
import java.time.Instant;

public record NonMissingData(
        String observationId,
        Instant scheduleStart,
        Instant scheduleEnd
) implements Serializable {
    public static NonMissingData fromActiveObservation(ActiveObservation activeObservation) {
        return new NonMissingData(activeObservation.observationId(), activeObservation.scheduleStart(), activeObservation.scheduleEnd());
    }
}
