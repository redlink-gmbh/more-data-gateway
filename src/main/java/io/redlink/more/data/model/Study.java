/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.model;

import io.redlink.more.data.api.app.v1.model.StudyDTO;
import io.redlink.more.data.controller.transformer.BaseTransformers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record Study(
        long studyId,
        String title,
        boolean active,
        String participantInfo,
        String finishText,
        String studyState,
        String consentInfo,
        Contact contact,
        LocalDate startDate,
        LocalDate plannedStartDate,
        LocalDate endDate,
        List<Observation> observations,
        Instant created,
        Instant modified,
        SimpleParticipant participant
) {
    public StudyDTO toDTO() {
        return new StudyDTO()
                .active(active)
                .studyTitle(title)
                .participantInfo(participantInfo)
                .consentInfo(consentInfo)
                .finishText(finishText)
                .studyState(toStudyStateDTO(studyState))
                .participant(participant.toDTO())
                .contact(contact.toDTO())
                .start(startDate)
                .end(endDate)
                .observations(observations.stream().map(o -> o.toDTO(participant.start(), participant.end(), studyId, participant.id())).toList())
                .version(BaseTransformers.toVersionTag(modified))
                ;
    }

    private StudyDTO.StudyStateEnum toStudyStateDTO(String studyState) {
        return switch (studyState) {
            case "active", "preview" -> StudyDTO.StudyStateEnum.ACTIVE;
            case "paused", "paused-preview" -> StudyDTO.StudyStateEnum.PAUSED;
            default -> StudyDTO.StudyStateEnum.CLOSED;
        };
    }
}
