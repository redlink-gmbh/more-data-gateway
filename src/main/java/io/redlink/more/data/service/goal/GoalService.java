package io.redlink.more.data.service.goal;

import io.redlink.more.data.exception.BadRequestException;
import io.redlink.more.data.exception.ConflictException;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.goal.Goal;
import io.redlink.more.data.model.goal.GoalTemplate;
import io.redlink.more.data.model.goal.StudyGoalConfig;
import io.redlink.more.data.repository.GoalRepository;
import io.redlink.more.data.service.ElasticService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class GoalService {
    private static final Logger log = LoggerFactory.getLogger(GoalService.class);

    public static final String DATA_TYPE_GAOL_CONFIG = "goal-configuration";

    private final String PROPERTY_CUSTOM_ADHERENCE_CHECKS_STATE = "custom-adherence-checks-state";
    private final String PROPERTY_CUSTOM_TITLE_STATE = "goal-title-state";

    private final GoalRepository goalRepository;
    private final ElasticService elasticService;


    public GoalService(GoalRepository goalRepository, ElasticService elasticService) {
        this.goalRepository = goalRepository;
        this.elasticService = elasticService;
    }

    @Transactional(readOnly = true)
    public StudyGoalConfig getStudyGoalConfig(RoutingInfo routingInfo) {
        StudyGoalConfig config = goalRepository.getStudyGoalConfig(routingInfo.studyId());
        if (config != null) {
            config.setGoalTopics(goalRepository.listGoalTopics(routingInfo.studyId()));
            config.setAdherenceChecks(goalRepository.listGoalAdherenceChecks(routingInfo.studyId()));
        }
        return config;
    }

    public List<GoalTemplate> getGoalTemplates(RoutingInfo routingInfo) {
        return goalRepository.listGoalTemplatesForGroups(
                routingInfo.studyId(),
                routingInfo.studyGroupId().isPresent() ? routingInfo.studyGroupId().getAsInt() : null,
                routingInfo.observationGroupIds());
    }

    public GoalTemplate getGoalTemplate(RoutingInfo routingInfo, int templateId) {
        return goalRepository.getGoalTemplateById(routingInfo.studyId(), templateId);
    }

    @Transactional(readOnly = false)
    public Goal createGoal(RoutingInfo routingInfo, Goal goal) {
        //we need the goal template to check the adherence checks for the goal
        var template = getGoalTemplate(routingInfo, goal.getTemplateId());
        if (template == null) {
            throw new BadRequestException(String.format(
                    "GoalTemplate[studyId: %s, templateId: %s] referenced by the parsed Goal does not exist",
                    routingInfo.studyId(), goal.getTemplateId()));
        }

        //----------
        //GOAL TITLE
        //----------
        //Copy over the title from the template if not set
        if (goal.getTitle() == null || goal.getTitle().isBlank()) {
            goal.setTitle(template.getTitle());
        }
        //the 'goal-title-state' decides if a user can give custom titles to a goal
        if (!getProperty(template, PROPERTY_CUSTOM_TITLE_STATE, Boolean.class, false) &&
                !goal.getTitle().equals(template.getTitle())) {
            throw new ConflictException(String.format(
                    "The title of the parsed Goal[templateId: %s, title: %s] is not compatible with the " +
                            "title of the GoalTemplate[studyId: %s, templateId: %s, title: %s]",
                    goal.getTemplateId(), goal.getTitle(), template.getStudyId(), template.getTemplateId(),
                    template.getTitle()));
        }

        //---------------------
        //GOAL ADHERENCE CHECKS
        //---------------------
        //copy over the adherence checks form the template if not set
        if (goal.getAdherenceCheckIds() == null || goal.getAdherenceCheckIds().isEmpty()) {
            goal.setAdherenceCheckIds(template.getAdherenceCheckIds());
        }
        //the 'custom-adherence-checks-state' decides if adherence checks for the goal
        //can be customized or not
        if (!getProperty(template, PROPERTY_CUSTOM_ADHERENCE_CHECKS_STATE, Boolean.class, false) &&
                !goal.getAdherenceCheckIds().equals(template.getAdherenceCheckIds())) {
            throw new ConflictException(String.format(
                    "The adherence checks of the parsed Goal[templateId: %s, adherenceChecksIds: %s] are not compatible with the " +
                            "adherence checks defined for the GoalTemplate[studyId: %s, templateId: %s, adherenceChecksIds: %s]",
                    goal.getTemplateId(), goal.getAdherenceCheckIds(), template.getStudyId(), template.getTemplateId(),
                    template.getAdherenceCheckIds()));
        }
        try {
            return writeGoalToElastic(goalRepository.insertGoal(goal), template, routingInfo, "CREATE");
        } catch (IOException ex) {
            //NOTE: Failing to write the Goal to the Elastic Index will rollback the database as we do not want to have
            //      goals in the database that are not recorded in the index!
            log.error("Error while writing {} for action=CREATE to Elastic", goal, ex);
            throw new IllegalStateException("Unable to create Goal(s) because time series index (elastic) is not available (" +
                    ex.getClass().getSimpleName() + ": " + ex.getMessage() + ")", ex);
        }
    }

    @Transactional(readOnly = false)
    public void deleteGoal(RoutingInfo routingInfo, int goalId) {
        Goal goal = goalRepository.getGoalById(routingInfo.studyId(), goalId);
        if (goal == null) {
            return;
        }
        goalRepository.deleteGoal(routingInfo.studyId(), goalId);
        GoalTemplate template = goalRepository.getGoalTemplateById(routingInfo.studyId(), goal.getTemplateId());
        if (template == null) {
            //NOTE: This should never happen as we have a releational database
            log.error("Goal template with id {} referenced by {} does not exist", goal.getTemplateId(), goal);
        }
        try {
            writeGoalToElastic(goal, template, routingInfo, "DELETE");
        } catch (IOException ex) {
            //NOTE: Failing to write the Goal to the Elastic Index will rollback the database as we do not want to have
            //      goals in the database that are not recorded in the index!
            log.error("Error while writing {} for action=DELETE to Elastic", goal, ex);
            throw new IllegalStateException("Unable to delete Goal(s) because time series index (elastic) is not available (" +
                    ex.getClass().getSimpleName() + ": " + ex.getMessage() + ")", ex);
        }
    }

    /**
     * Lists all goals for the study and participant referenced by the routing info
     *
     * @param routingInfo the routing info
     * @return all goals for the study and participant
     */
    public List<Goal> listGoals(RoutingInfo routingInfo) {
        return goalRepository.listGoals(routingInfo.studyId(), routingInfo.participantId(), null);
    }

    /**
     * Lists all goals for the parsed template for the study and participant referenced by the routing info
     *
     * @param routingInfo the routing info
     * @param templateId  the id of the template
     * @return all goals for the study and participant
     */
    public List<Goal> listGoals(RoutingInfo routingInfo, int templateId) {
        return goalRepository.listGoals(routingInfo.studyId(), routingInfo.participantId(), templateId);
    }

    public Goal getGoal(RoutingInfo routingInfo, int goalId) {
        return goalRepository.getGoalById(routingInfo.studyId(), goalId);
    }

    private Goal writeGoalToElastic(Goal goal, GoalTemplate template, RoutingInfo routingInfo, String action) throws IOException {
        DataPoint dataPoint = new DataPoint(
                UUID.randomUUID().toString(),
                Objects.requireNonNull(goal).getExternalTemplateId(),
                Objects.requireNonNull(goal).getExternalGoalId(),
                template != null ? template.getType() : null,
                DATA_TYPE_GAOL_CONFIG,
                Instant.now(),
                goal.getModified(),
                toDataMap(goal, Objects.requireNonNull(action)));
        elasticService.storeDataPoints(List.of(dataPoint), Objects.requireNonNull(routingInfo));
        return goal;
    }

    private Map<String, Object> toDataMap(Goal goal, String action) {
        Map<String, Object> data = new HashMap<>();
        data.put("action", action);
        data.put("title", goal.getTitle());
        data.put("adherence_checks", goal.getAdherenceCheckIds());
        if (goal.getProperties() instanceof Map<?, ?>) {
            ((Map<?, ?>) goal.getProperties())
                    .forEach((key, value) -> data.put("property_" + key.toString(), value));
        }
        return data;
    }

    /**
     * Helper method to get the value of a goal template property with a given type and an optional default value
     */
    private <T> T getProperty(GoalTemplate template, String property, Class<T> type, T defaultValue) {
        var properties = template.getProperties();
        if (properties instanceof Map<?, ?>) {
            var value = ((Map<?, ?>) properties).get(property);
            if (type.isInstance(value)) {
                return type.cast(value);
            }
        }
        return defaultValue;
    }
}
