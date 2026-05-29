package io.redlink.more.data.controller.goal;

import io.redlink.more.data.api.goal.v1.model.GoalDTO;
import io.redlink.more.data.api.goal.v1.model.GoalRequestDTO;
import io.redlink.more.data.api.goal.v1.model.GoalTemplateDTO;
import io.redlink.more.data.api.goal.v1.model.StudyGoalConfigDataDTO;
import io.redlink.more.data.api.goal.v1.webservices.GoalTemplatesApi;
import io.redlink.more.data.api.goal.v1.webservices.GoalsApi;
import io.redlink.more.data.api.goal.v1.webservices.GoalsConfigApi;
import io.redlink.more.data.configuration.AuthenticationFacade;
import io.redlink.more.data.controller.transformer.GoalTransformer;
import io.redlink.more.data.exception.NotFoundException;
import io.redlink.more.data.model.GatewayUserDetails;
import io.redlink.more.data.service.GatewayUserDetailService;
import io.redlink.more.data.service.goal.GoalService;
import io.redlink.more.data.util.LoggingUtils;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
public class GoalController implements GoalsApi {

    private final AuthenticationFacade authenticationFacade;
    private final GoalService goalService;


    public GoalController(
            AuthenticationFacade authenticationFacade,
            GoalService goalService) {
        this.authenticationFacade = authenticationFacade;
        this.goalService = goalService;
    }

    @Override
    public ResponseEntity<GoalDTO> getGoal(String goalId) {
        final GatewayUserDetails userDetails = authenticationFacade
                .assertAuthority(GatewayUserDetailService.APP_ROLE);
        try (LoggingUtils.LoggingContext ctx = LoggingUtils.createContext(userDetails.getRoutingInfo())) {
            if(!userDetails.getRoutingInfo().studyActive() || !userDetails.getRoutingInfo().participantActive()) {
                return ResponseEntity.notFound().build();
            }
            var goal = goalService.getGoal(userDetails.getRoutingInfo(),
                    toGaolId(goalId).orElseThrow( () -> new NotFoundException("Goal with id=%s not found".formatted(goalId))));
            if(goal == null){
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(GoalTransformer.toGoalDTO_V1(goal));
        }
    }

    private Optional<Integer> toGaolId(String goalId){
        try {
            return Optional.of(Integer.parseInt(goalId));
        } catch(NumberFormatException e){
            return Optional.empty();
        }
    }


    @Override
    public ResponseEntity<List<GoalDTO>> getGoals() {
        return null;
    }

    @Override
    public ResponseEntity<Void> goalDeletion(String goalId) {
        return null;
    }

    @Override
    public ResponseEntity<List<GoalDTO>> goalsCreation(List<@Valid GoalRequestDTO> goalRequestDTO) {
        return null;
    }

    @Override
    public ResponseEntity<List<String>> goalsDeletion() {
        return null;
    }

    public ResponseEntity<StudyGoalConfigDataDTO> getGoalConfig() {
        final GatewayUserDetails userDetails = authenticationFacade
                .assertAuthority(GatewayUserDetailService.APP_ROLE);
        try (LoggingUtils.LoggingContext ctx = LoggingUtils.createContext(userDetails.getRoutingInfo())) {
            if(!userDetails.getRoutingInfo().studyActive() || !userDetails.getRoutingInfo().participantActive()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(GoalTransformer.toStudyGoalConfigDataDTO_V1(
                    goalService.getStudyGoalConfig(userDetails.getRoutingInfo())));
        }
    }

    @Override
    public ResponseEntity<List<GoalTemplateDTO>> listGoalTemplates() {
        final GatewayUserDetails userDetails = authenticationFacade
                .assertAuthority(GatewayUserDetailService.APP_ROLE);
        try (LoggingUtils.LoggingContext ctx = LoggingUtils.createContext(userDetails.getRoutingInfo())) {
            if(!userDetails.getRoutingInfo().studyActive() || !userDetails.getRoutingInfo().participantActive()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(goalService.getGoalTemplates(userDetails.getRoutingInfo()).stream()
                    .map(GoalTransformer::toGoalTemplateDTO_V1)
                    .toList());
        }
    }

}
