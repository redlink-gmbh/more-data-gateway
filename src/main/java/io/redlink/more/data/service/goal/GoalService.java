package io.redlink.more.data.service.goal;

import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.goal.GoalTemplate;
import io.redlink.more.data.model.goal.StudyGoalConfig;
import io.redlink.more.data.repository.GoalRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class GoalService {

    private final GoalRepository goalRepository;

    public GoalService(GoalRepository goalRepository) {
        this.goalRepository = goalRepository;
    }

    @Transactional(readOnly = true)
    public StudyGoalConfig getStudyGoalConfig(RoutingInfo routingInfo) {
        return goalRepository.getStudyGoalConfig(routingInfo.studyId());
    }

    public final List<GoalTemplate> getGoalTemplates(RoutingInfo routingInfo) {
        return goalRepository.listGoalTemplatesForGroups(
                routingInfo.studyId(),
                routingInfo.studyGroupId().isPresent() ? routingInfo.studyGroupId().getAsInt() : null,
                routingInfo.observationGroupIds());
    }

}
