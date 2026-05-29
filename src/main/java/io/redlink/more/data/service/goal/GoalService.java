package io.redlink.more.data.service.goal;

import io.redlink.more.data.exception.BadRequestException;
import io.redlink.more.data.exception.ConflictException;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.goal.Goal;
import io.redlink.more.data.model.goal.GoalTemplate;
import io.redlink.more.data.model.goal.StudyGoalConfig;
import io.redlink.more.data.repository.GoalRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class GoalService {

    private final String PROPERTY_CUSTOM_ADHERENCE_CHECKS_STATE = "custom-adherence-checks-state";

    private final GoalRepository goalRepository;

    public GoalService(GoalRepository goalRepository) {
        this.goalRepository = goalRepository;
    }

    @Transactional(readOnly = true)
    public StudyGoalConfig getStudyGoalConfig(RoutingInfo routingInfo) {
        return goalRepository.getStudyGoalConfig(routingInfo.studyId());
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

    public Goal createGoal(RoutingInfo routingInfo, Goal goal) {
        //we need the goal template to check the adherence checks for the goal
        var template = getGoalTemplate(routingInfo, goal.getTemplateId());
        if (template == null) {
            throw new BadRequestException(String.format(
                    "GoalTemplate[studyId: %s, templateId: %s] referenced by the parsed Goal does not exist",
                    routingInfo.studyId(), goal.getTemplateId()));
        }
        if(goal.getAdherenceCheckIds() == null || goal.getAdherenceCheckIds().isEmpty()) {
            goal.setAdherenceCheckIds(template.getAdherenceCheckIds());
        }
        //the 'custom-adherence-checks-state' decides if adherence checks for the goal
        //can be customized or not
        if(!getProperty(template, PROPERTY_CUSTOM_ADHERENCE_CHECKS_STATE, Boolean.class, false) &&
                !goal.getAdherenceCheckIds().equals(template.getAdherenceCheckIds())){
            throw new ConflictException(String.format(
                    "The adherence checks of the parsed Goal[adherenceChecksIds: %s] are not compatible with the " +
                            "adherence checks defined for the GoalTemplate[studyId: %s, templateId: %s, adherenceChecksIds: %s]",
                    goal.getAdherenceCheckIds(), routingInfo.studyId(), goal.getTemplateId(), goal.getAdherenceCheckIds()));
        }
        return goalRepository.insertGoal(goal);
    }

    public void deleteGoal(RoutingInfo routingInfo, int goalId) {
        goalRepository.deleteGoal(routingInfo.studyId(), goalId);
    }

    /**
     * Lists all goals for the study and participant referenced by the routing info
     * @param routingInfo the routing info
     * @return all goals for the study and participant
     */
    public List<Goal> listGoals(RoutingInfo routingInfo) {
        return goalRepository.listGoals(routingInfo.studyId(), routingInfo.participantId(), null);
    }

    /**
     * Lists all goals for the parsed template for the study and participant referenced by the routing info
     * @param routingInfo the routing info
     * @param templateId the id of the template
     * @return all goals for the study and participant
     */
    public List<Goal> listGoals(RoutingInfo routingInfo, int templateId) {
        return goalRepository.listGoals(routingInfo.studyId(), routingInfo.participantId(), templateId);
    }
    public Goal getGoal(RoutingInfo routingInfo, int goalId) {
        return goalRepository.getGoalById(routingInfo.studyId(), goalId);
    }

    /**
     * Helper method to get the value of a goal template property with a given type and an optional default value
     */
    private <T> T getProperty(GoalTemplate template, String property, Class<T> type, T defaultValue) {
        var properties = template.getProperties();
        if(properties instanceof Map<?,?>) {
            var value = ((Map<?,?>)properties).get(property);
            if(type.isInstance(value)){
                return type.cast(value);
            }
        }
        return defaultValue;
    }
}
