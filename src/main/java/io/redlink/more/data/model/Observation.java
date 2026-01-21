/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.model;

import io.redlink.more.data.api.app.v1.model.ObservationDTO;
import io.redlink.more.data.api.app.v1.model.ObservationScheduleDTO;
import io.redlink.more.data.controller.transformer.BaseTransformers;
import io.redlink.more.data.model.scheduler.ScheduleEvent;
import io.redlink.more.data.util.SchedulerUtils;
import org.apache.commons.lang3.Range;

import java.time.Instant;

public record Observation(
        int observationId,
        Integer groupId,
        String title,
        String type,
        String participantInfo,
        Object properties,
        ScheduleEvent observationSchedule,
        Instant created,
        Instant modified,
        boolean hidden,
        boolean noSchedule
) {
    public Observation withProperties(Object properties) {
        return new Observation(
                observationId, groupId, title, type, participantInfo, properties, observationSchedule, created, modified, hidden, noSchedule
        );
    }

    public ObservationDTO toDTO(Instant start, Instant end, Long studyId, Integer participantId) {
        ObservationDTO dto = new ObservationDTO()
                .observationId(String.valueOf(observationId()))
                .observationType(type())
                .observationTitle(title())
                .participantInfo(participantInfo())
                ._configuration(properties())
                .version(BaseTransformers.toVersionTag(modified()))
                .hidden(hidden())
                .noSchedule(noSchedule());
        if (observationSchedule() != null && start != null) {
            dto.schedule(SchedulerUtils
                    .parseToObservationSchedules(observationSchedule(), start, end, studyId, participantId, observationId)
                    .stream()
                    .map(this::toObservationScheduleDTO)
                    .toList());
        }
        return dto;
    }

    public ObservationScheduleDTO toObservationScheduleDTO(Range<Instant> schedule) {
        Instant instant = schedule.getMaximum();
        Instant instant1 = schedule.getMinimum();
        return new ObservationScheduleDTO()
                .start(instant1)
                .end(instant)
                ;
    }
}
