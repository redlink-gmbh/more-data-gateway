/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.model;

import java.io.Serializable;
import java.util.OptionalInt;
import java.util.Set;

/**
 * API routing Info where the study and the observation are given and the participant is provided by the data
 * @param studyId the study
 * @param observationId the observation
 * @param observationType the type of the observation
 * @param rawStudyGroupId the study group assigned to the observation (if any)
 * @param rawObservationGroupId the observation group assigned to the observation (if any)
 * @param studyActive if the study is active
 * @param secret the secret
 */
public record ApiRoutingInfo(
        Long studyId,
        Integer observationId,
        String observationType,
        int rawStudyGroupId,
        int rawObservationGroupId,
        boolean studyActive,
        String secret
) implements Serializable {

    public ApiRoutingInfo(Long studyId,
                          Integer observationId,
                          String observationType,
                          @SuppressWarnings("OptionalUsedAsFieldOrParameterType") OptionalInt studyGroupId,
                          @SuppressWarnings("OptionalUsedAsFieldOrParameterType") OptionalInt observationGroupId,
                          boolean studyActive,
                          String secret
    ) {
        this(studyId, observationId, observationType, studyGroupId.orElse(Integer.MIN_VALUE), observationGroupId.orElse(Integer.MIN_VALUE), studyActive, secret);
    }

    public OptionalInt studyGroupId() {
        if (this.rawStudyGroupId < 0) {
            return OptionalInt.empty();
        } else {
            return OptionalInt.of(rawStudyGroupId);
        }
    }

    public OptionalInt observationGroupId() {
        if (this.rawObservationGroupId < 0) {
            return OptionalInt.empty();
        } else {
            return OptionalInt.of(rawObservationGroupId);
        }
    }

}
