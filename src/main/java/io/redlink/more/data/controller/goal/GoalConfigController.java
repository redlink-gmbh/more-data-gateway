package io.redlink.more.data.controller.goal;

import io.redlink.more.data.api.goal.v1.model.GoalTemplateDTO;
import io.redlink.more.data.api.goal.v1.model.StudyGoalConfigDataDTO;
import io.redlink.more.data.api.goal.v1.webservices.GoalTemplatesApi;
import io.redlink.more.data.api.goal.v1.webservices.GoalsConfigApi;
import io.redlink.more.data.configuration.AuthenticationFacade;
import io.redlink.more.data.controller.transformer.GoalTransformer;
import io.redlink.more.data.controller.transformer.StudyTransformer;
import io.redlink.more.data.model.CompletedData;
import io.redlink.more.data.model.GatewayUserDetails;
import io.redlink.more.data.model.goal.StudyGoalConfig;
import io.redlink.more.data.service.GatewayUserDetailService;
import io.redlink.more.data.service.PushNotificationService;
import io.redlink.more.data.service.StudyService;
import io.redlink.more.data.service.goal.GoalService;
import io.redlink.more.data.util.LoggingUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Controller
@RestController
@RequestMapping(value = "/goals/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class GoalConfigController implements GoalsConfigApi, GoalTemplatesApi {

    private final AuthenticationFacade authenticationFacade;
    private final GoalService goalService;


    public GoalConfigController(
            AuthenticationFacade authenticationFacade,
            GoalService goalService) {
        this.authenticationFacade = authenticationFacade;
        this.goalService = goalService;
    }

    @Override
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
