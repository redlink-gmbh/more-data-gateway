package io.redlink.more.data.model;

import java.time.Instant;
import java.util.UUID;

public record ObservationAttachment(
        UUID attachmentId,
        long studyId,
        int observationId,
        String filename,
        String contentType,
        long size,
        Instant created
) {
}
