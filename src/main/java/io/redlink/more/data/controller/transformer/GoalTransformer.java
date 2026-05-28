/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Österreichische Vereinigung zur
 * Förderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.controller.transformer;

import io.redlink.more.data.api.goal.v1.model.AdherenceCheckScheduleEnumDTO;
import io.redlink.more.data.api.goal.v1.model.CategoriesDTO;
import io.redlink.more.data.api.goal.v1.model.GoalTemplateDTO;
import io.redlink.more.data.api.goal.v1.model.GoalTopicDTO;
import io.redlink.more.data.api.goal.v1.model.StudyGoalConfigConsentsDTO;
import io.redlink.more.data.api.goal.v1.model.StudyGoalConfigDataDTO;
import io.redlink.more.data.api.goal.v1.model.StudyGoalConfigScheduleInnerDTO;
import io.redlink.more.data.model.goal.AdherenceCheck;
import io.redlink.more.data.model.goal.GoalTemplate;
import io.redlink.more.data.model.goal.GoalTopic;
import io.redlink.more.data.model.goal.StudyGoalConfig;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class GoalTransformer {

    private GoalTransformer() {}

// ========================== MODEL → DTO ==========================

    public static StudyGoalConfigDataDTO toStudyGoalConfigDataDTO_V1(
            StudyGoalConfig config) {

        final StudyGoalConfigConsentsDTO consents = toStudyGoalConfigConsentsDTO_V1(config);

        final List<StudyGoalConfigScheduleInnerDTO> schedule = config.getAdherenceChecks().stream()
                .map(GoalTransformer::toStudyGoalConfigScheduleInnerDTO_V1)
                .toList();

        final List<GoalTopicDTO> topicDtos = config.getGoalTopics().stream()
                .map(GoalTransformer::toGoalTopicDTO_V1)
                .toList();

        return new StudyGoalConfigDataDTO()
                .consents(consents)
                .schedule(schedule)
                .topics(topicDtos);
    }

    public static StudyGoalConfigConsentsDTO toStudyGoalConfigConsentsDTO_V1(StudyGoalConfig config) {
        return new StudyGoalConfigConsentsDTO()
                .commitment(config.getCommitment())
                .achievability(config.getAchievability())
                .understandable(config.getUnderstandability());
    }

    public static StudyGoalConfigScheduleInnerDTO toStudyGoalConfigScheduleInnerDTO_V1(AdherenceCheck check) {
        return new StudyGoalConfigScheduleInnerDTO()
                .key(mapTitleToScheduleEnum(check.getTitle()))
                .time(check.getTime());           // LocalTime → LocalTime (no conversion needed)
    }

    public static GoalTopicDTO toGoalTopicDTO_V1(GoalTopic topic) {
        return new GoalTopicDTO()
                .key(topic.getKey())
                .title(topic.getTitle())
                .description(topic.getDescription());
    }


    public static GoalTemplateDTO toGoalTemplateDTO_V1(
            GoalTemplate template) {
        if (template == null) return null;

        List<AdherenceCheckScheduleEnumDTO> adherenceChecks = template.getAdherenceCheckIds() != null ?
                template.getAdherenceCheckIds().stream()
                    .map(GoalTransformer::mapOrdinalToAdherenceEnum)
                    .filter(Objects::nonNull)
                    .toList() :
                List.of();

        return new GoalTemplateDTO(
                Objects.toString(template.getTemplateId()),
                template.getParticipantTitle() != null ?
                        template.getParticipantTitle() : //NOTE: the ParticipantTitle is mapped to the title
                        template.getTitle(), //the internal title is used as fallback
                template.getType(),
                new CategoriesDTO()
                        .kind(mapKindToEnum(template.getKind()))
                        .topics(template.getTopicKeys() != null ? List.copyOf(template.getTopicKeys()) : List.of()))
                .info(template.getParticipantInfo())
                .adherenceChecks(adherenceChecks)
                .properties(template.getProperties() != null ? template.getProperties() : Map.of())
                .created(template.getCreated())
                .modified(template.getModified());
    }

    // ========================== HELPER METHODS ==========================

    private static AdherenceCheckScheduleEnumDTO mapTitleToScheduleEnum(String title) {
        if (title == null) return null;
        String normalized = title.trim().toLowerCase();
        try {
            return AdherenceCheckScheduleEnumDTO.fromValue(normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Maps internal ordinal (stored in adherenceCheckIds) to DTO enum.
     */
    private static AdherenceCheckScheduleEnumDTO mapOrdinalToAdherenceEnum(Integer ordinal) {
        if (ordinal == null) return null;
        AdherenceCheckScheduleEnumDTO[] values = AdherenceCheckScheduleEnumDTO.values();
        if (ordinal >= 0 && ordinal < values.length) {
            return values[ordinal];
        }
        return null;
    }

    private static CategoriesDTO.KindEnum mapKindToEnum(String kind) {
        if (kind == null) return null;
        try {
            return CategoriesDTO.KindEnum.fromValue(kind.toLowerCase().trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

}