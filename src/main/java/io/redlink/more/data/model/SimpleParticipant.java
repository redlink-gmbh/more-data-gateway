/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.model;

import io.redlink.more.data.api.app.v1.model.SimpleParticipantDTO;

import java.time.Instant;

public record SimpleParticipant(
        int id,
        String alias,
        Instant start,
        Instant end
) {
    public SimpleParticipantDTO toDTO() {
        return new SimpleParticipantDTO()
                .id(id)
                .alias(alias);
    }
}
