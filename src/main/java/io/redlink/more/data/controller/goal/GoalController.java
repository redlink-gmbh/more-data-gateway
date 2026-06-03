package io.redlink.more.data.controller.goal;

import io.redlink.more.data.api.goal.v1.model.GoalDTO;
import io.redlink.more.data.api.goal.v1.model.GoalDataDTO;
import io.redlink.more.data.api.goal.v1.webservices.GoalsApi;
import io.redlink.more.data.configuration.AuthenticationFacade;
import io.redlink.more.data.controller.transformer.GoalTransformer;
import io.redlink.more.data.exception.BadRequestException;
import io.redlink.more.data.exception.NotFoundException;
import io.redlink.more.data.model.GatewayUserDetails;
import io.redlink.more.data.service.GatewayUserDetailService;
import io.redlink.more.data.service.goal.GoalService;
import io.redlink.more.data.util.LoggingUtils;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RestController
@RequestMapping(value = "/goals/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
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
    public ResponseEntity<GoalDTO> getGoal(String goalIdStr) {
        final GatewayUserDetails userDetails = getAndValidateUser();
        final Integer goalId = toGaolId(goalIdStr)
                .orElseThrow( () -> new NotFoundException("Goal with id=%s not found".formatted(goalIdStr)));
        try (LoggingUtils.LoggingContext ctx = LoggingUtils.createContext(userDetails.getRoutingInfo())) {
            var goal = goalService.getGoal(userDetails.getRoutingInfo(), goalId);
            if(goal == null){
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(GoalTransformer.toGoalDTO_V1(goal));
        }
    }

    @Override
    public ResponseEntity<List<GoalDTO>> getGoals() {
        final GatewayUserDetails userDetails = getAndValidateUser();
        try (LoggingUtils.LoggingContext ctx = LoggingUtils.createContext(userDetails.getRoutingInfo())) {
            return ResponseEntity.ok(goalService.listGoals(userDetails.getRoutingInfo()).stream()
                    .map(GoalTransformer::toGoalDTO_V1)
                    .toList());
        }
    }

    @Override
    public ResponseEntity<Void> goalDeletion(String goalIdStr) {
        final GatewayUserDetails userDetails = getAndValidateUser();
        final Integer goalId = toGaolId(goalIdStr)
                .orElseThrow( () -> new NotFoundException("Goal with id=%s not found".formatted(goalIdStr)));
        try (LoggingUtils.LoggingContext ctx = LoggingUtils.createContext(userDetails.getRoutingInfo())) {
            goalService.deleteGoal(userDetails.getRoutingInfo(), goalId);
            return ResponseEntity.noContent().build();
        }
    }

    @Override
    public ResponseEntity<List<GoalDTO>> goalsCreation(List<@Valid GoalDataDTO> goalDataDTO) {
        final GatewayUserDetails userDetails = getAndValidateUser();
        return ResponseEntity.ok(goalDataDTO.stream()
                .map(gd -> GoalTransformer.toGoal(gd, userDetails.getRoutingInfo().studyId(), userDetails.getRoutingInfo().participantId()))
                .map(g -> goalService.createGoal(userDetails.getRoutingInfo(), g))
                .map(GoalTransformer::toGoalDTO_V1)
                .toList());
    }

    @Override
    public ResponseEntity<Void> goalsDeletion(List<String> goalIdStrs) {
        final GatewayUserDetails userDetails = getAndValidateUser();
        goalIdStrs.stream()
                .map(strId -> {
                    try {
                        return Integer.parseInt(strId);
                    } catch(NumberFormatException e){
                        throw new BadRequestException("The parsed array of GoalIds %s contained an illegal formatted id=%s"
                                .formatted(goalIdStrs, strId));
                    }
                })
                .collect(Collectors.toSet()) //collect first to ensure all can be converted
                .forEach(goalId -> goalService.deleteGoal(userDetails.getRoutingInfo(), goalId));
        return ResponseEntity.noContent().build();
    }

    private GatewayUserDetails getAndValidateUser() {
        final GatewayUserDetails userDetails = authenticationFacade
                .assertAuthority(GatewayUserDetailService.APP_ROLE);
        if(!userDetails.getRoutingInfo().studyActive() || !userDetails.getRoutingInfo().participantActive()) {
            throw new NotFoundException(null);
        }
        return userDetails;
    }

    private Optional<Integer> toGaolId(String goalId){
        try {
            return Optional.of(Integer.parseInt(goalId));
        } catch(NumberFormatException e){
            return Optional.empty();
        }
    }

}
