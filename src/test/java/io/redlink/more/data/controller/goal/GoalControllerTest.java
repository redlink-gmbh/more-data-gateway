package io.redlink.more.data.controller.goal;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.redlink.more.data.api.goal.v1.model.AdherenceCheckScheduleEnumDTO;
import io.redlink.more.data.api.goal.v1.model.GoalDataDTO;
import io.redlink.more.data.configuration.AuthenticationFacade;
import io.redlink.more.data.configuration.SecurityConfig;
import io.redlink.more.data.controller.GlobalControllerExceptionHandler;
import io.redlink.more.data.model.GatewayUserDetails;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.goal.Goal;
import io.redlink.more.data.service.GatewayUserDetailService;
import io.redlink.more.data.service.goal.GoalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({
        GoalController.class,
        GlobalControllerExceptionHandler.class
})
@Import(SecurityConfig.class)
@AutoConfigureMockMvc
class GoalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthenticationFacade authenticationFacade;

    @MockitoBean
    private GatewayUserDetailService gatewayUserDetailService;

    @MockitoBean
    private GoalService goalService;

    private static final Long STUDY_ID = 42L;
    private static final int PARTICIPANT_ID = 7;
    private static final Instant NOW = Instant.now();

    // =====================================================================
    // getGoals()
    // =====================================================================

    @Test
    void getGoals_Success() throws Exception {
        RoutingInfo routingInfo = new RoutingInfo(STUDY_ID, PARTICIPANT_ID, -1, Set.of(), true, true);
        GatewayUserDetails userDetails = new GatewayUserDetails("app-user", "app-user-pwd", Set.of(GatewayUserDetailService.APP_ROLE), routingInfo);
        List<Goal> goals = List.of(createTestGoal(1), createTestGoal(2));

        when(authenticationFacade.assertAuthority(GatewayUserDetailService.APP_ROLE)).thenReturn(userDetails);
        when(goalService.listGoals(routingInfo)).thenReturn(goals);

        mockMvc.perform(get("/goals/api/v1/goals")
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].goalId").value("goal_1"))
                .andExpect(jsonPath("$[0].templateId").value("goaltemplate_101"))
                .andExpect(jsonPath("$[0].title").value("Daily Step Goal"))
                .andExpect(jsonPath("$[0].adherenceChecks[0]").value("morning"))
                .andExpect(jsonPath("$[0].properties.target").value(10000))
                .andExpect(jsonPath("$[0].created").exists());
    }

    // =====================================================================
    // getGoal(String goalIdStr)
    // =====================================================================

    @Test
    void getGoal_Success() throws Exception {
        RoutingInfo routingInfo = new RoutingInfo(STUDY_ID, PARTICIPANT_ID, -1, Set.of(), true, true);
        GatewayUserDetails userDetails = new GatewayUserDetails("app-user", "app-user-pwd", Set.of(GatewayUserDetailService.APP_ROLE), routingInfo);
        Goal goal = createTestGoal(5);

        when(authenticationFacade.assertAuthority(GatewayUserDetailService.APP_ROLE)).thenReturn(userDetails);
        when(goalService.getGoal(routingInfo, 5)).thenReturn(goal);

        mockMvc.perform(get("/goals/api/v1/goals/5")
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalId").value("goal_5"))
                .andExpect(jsonPath("$.templateId").value("goaltemplate_101"))
                .andExpect(jsonPath("$.title").value("Daily Step Goal"))
                .andExpect(jsonPath("$.adherenceChecks[0]").value("morning"))
                .andExpect(jsonPath("$.properties.target").value(10000));
    }

    @Test
    void getGoal_NotFound() throws Exception {
        RoutingInfo routingInfo = new RoutingInfo(STUDY_ID, PARTICIPANT_ID, -1, Set.of(), true, true);
        GatewayUserDetails userDetails = new GatewayUserDetails("app-user", "app-user-pwd", Set.of(GatewayUserDetailService.APP_ROLE), routingInfo);
        when(authenticationFacade.assertAuthority(GatewayUserDetailService.APP_ROLE)).thenReturn(userDetails);
        when(goalService.getGoal(routingInfo, 999)).thenReturn(null);

        mockMvc.perform(get("/goals/api/v1/goals/999")
                        .with(user(userDetails)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getGoal_InvalidIdFormat() throws Exception {
        RoutingInfo routingInfo = new RoutingInfo(STUDY_ID, PARTICIPANT_ID, -1, Set.of(), true, true);
        GatewayUserDetails userDetails = new GatewayUserDetails("app-user", "app-user-pwd", Set.of(GatewayUserDetailService.APP_ROLE), routingInfo);
        when(authenticationFacade.assertAuthority(GatewayUserDetailService.APP_ROLE)).thenReturn(userDetails);

        mockMvc.perform(get("/goals/api/v1/goals/abc")
                        .with(user(userDetails)))
                .andExpect(status().isNotFound());
    }

    // =====================================================================
    // goalDeletion(String goalIdStr)
    // =====================================================================

    @Test
    void goalDeletion_Success() throws Exception {
        RoutingInfo routingInfo = new RoutingInfo(STUDY_ID, PARTICIPANT_ID, -1, Set.of(), true, true);
        GatewayUserDetails userDetails = new GatewayUserDetails("app-user", "app-user-pwd", Set.of(GatewayUserDetailService.APP_ROLE), routingInfo);
        when(authenticationFacade.assertAuthority(GatewayUserDetailService.APP_ROLE)).thenReturn(userDetails);

        mockMvc.perform(delete("/goals/api/v1/goals/10")
                        .with(user(userDetails)))
                .andExpect(status().isNoContent());

        verify(goalService).deleteGoal(routingInfo, 10);
    }

    @Test
    void goalDeletion_InvalidId() throws Exception {
        RoutingInfo routingInfo = new RoutingInfo(STUDY_ID, PARTICIPANT_ID, -1, Set.of(), true, true);
        GatewayUserDetails userDetails = new GatewayUserDetails("app-user", "app-user-pwd", Set.of(GatewayUserDetailService.APP_ROLE), routingInfo);
        when(authenticationFacade.assertAuthority(GatewayUserDetailService.APP_ROLE)).thenReturn(userDetails);

        mockMvc.perform(delete("/goals/api/v1/goals/xyz")
                        .with(user(userDetails)))
                .andExpect(status().isNotFound());
    }

    // =====================================================================
    // goalsCreation(List<GoalDataDTO>)
    // =====================================================================

    @Test
    void goalsCreation_Success() throws Exception {
        RoutingInfo routingInfo = new RoutingInfo(STUDY_ID, PARTICIPANT_ID, -1, Set.of(), true, true);
        GatewayUserDetails userDetails = new GatewayUserDetails("app-user", "app-user-pwd", Set.of(GatewayUserDetailService.APP_ROLE), routingInfo);
        GoalDataDTO dto1 = new GoalDataDTO()
                .templateId("goaltemplate_101")
                .title("Morning Walk")
                .adherenceChecks(List.of(AdherenceCheckScheduleEnumDTO.MORNING))
                .properties(java.util.Map.of("target", 8000));

        Goal createdGoal = createTestGoal(100);
        when(authenticationFacade.assertAuthority(GatewayUserDetailService.APP_ROLE)).thenReturn(userDetails);
        when(goalService.createGoal(eq(routingInfo), any(Goal.class))).thenReturn(createdGoal);

        mockMvc.perform(post("/goals/api/v1/goals")
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(dto1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].goalId").value("goal_100"))
                .andExpect(jsonPath("$[0].templateId").value("goaltemplate_101"))
                .andExpect(jsonPath("$[0].title").value("Daily Step Goal"))
                .andExpect(jsonPath("$[0].adherenceChecks[0]").value("morning"));
    }

    @Test
    void goalsCreation_illegalTemplateId() throws Exception {
        RoutingInfo routingInfo = new RoutingInfo(STUDY_ID, PARTICIPANT_ID, -1, Set.of(), true, true);
        GatewayUserDetails userDetails = new GatewayUserDetails("app-user", "app-user-pwd", Set.of(GatewayUserDetailService.APP_ROLE), routingInfo);
        GoalDataDTO dto1 = new GoalDataDTO()
                .templateId("101") //expected 'goaltemplate-101'
                .title("Morning Walk")
                .adherenceChecks(List.of(AdherenceCheckScheduleEnumDTO.MORNING))
                .properties(java.util.Map.of("target", 8000));

        when(authenticationFacade.assertAuthority(GatewayUserDetailService.APP_ROLE)).thenReturn(userDetails);

        mockMvc.perform(post("/goals/api/v1/goals")
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(dto1))))
                .andExpect(status().isConflict());
    }

    // =====================================================================
    // goalsDeletion(List<String> goalIdStrs) - Bulk Delete
    // =====================================================================

    @Test
    void goalsDeletion_Success() throws Exception {
        RoutingInfo routingInfo = new RoutingInfo(STUDY_ID, PARTICIPANT_ID, -1, Set.of(), true, true);
        GatewayUserDetails userDetails = new GatewayUserDetails("app-user", "app-user-pwd", Set.of(GatewayUserDetailService.APP_ROLE), routingInfo);
        when(authenticationFacade.assertAuthority(GatewayUserDetailService.APP_ROLE)).thenReturn(userDetails);

        mockMvc.perform(delete("/goals/api/v1/goals")
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of("5", "7", "12"))))
                .andExpect(status().isNoContent());

        verify(goalService).deleteGoal(routingInfo, 5);
        verify(goalService).deleteGoal(routingInfo, 7);
        verify(goalService).deleteGoal(routingInfo, 12);
    }

    @Test
    void goalsDeletion_InvalidIdInList() throws Exception {
        RoutingInfo routingInfo = new RoutingInfo(STUDY_ID, PARTICIPANT_ID, -1, Set.of(), true, true);
        GatewayUserDetails userDetails = new GatewayUserDetails("app-user", "app-user-pwd", Set.of(GatewayUserDetailService.APP_ROLE), routingInfo);
        when(authenticationFacade.assertAuthority(GatewayUserDetailService.APP_ROLE)).thenReturn(userDetails);

        mockMvc.perform(delete("/goals/api/v1/goals")
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of("5", "abc", "12"))))
                .andExpect(status().isBadRequest());
    }

    // =====================================================================
    // Authorization & Validation Edge Cases
    // =====================================================================

    @Test
    void anyEndpoint_Unauthorized() throws Exception {
        mockMvc.perform(get("/goals/api/v1/goals"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/goals/api/v1/goals/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anyEndpoint_StudyNotActive() throws Exception {
        RoutingInfo routingInfo = new RoutingInfo(STUDY_ID, PARTICIPANT_ID, -1, Set.of(), false, true);
        GatewayUserDetails inactiveStudyUser = new GatewayUserDetails("app-user", "app-user-pwd", Set.of(GatewayUserDetailService.APP_ROLE), routingInfo);

        when(authenticationFacade.assertAuthority(GatewayUserDetailService.APP_ROLE)).thenReturn(inactiveStudyUser);

        mockMvc.perform(get("/goals/api/v1/goals")
                        .with(user(inactiveStudyUser)))
                .andExpect(status().isNotFound());
    }

    @Test
    void anyEndpoint_ParticipantNotActive() throws Exception {
        RoutingInfo routingInfo = new RoutingInfo(STUDY_ID, PARTICIPANT_ID, -1, Set.of(), true, false);
        GatewayUserDetails inactiveUser = new GatewayUserDetails("app-user", "app-user-pwd", Set.of(GatewayUserDetailService.APP_ROLE), routingInfo);

        when(authenticationFacade.assertAuthority(GatewayUserDetailService.APP_ROLE)).thenReturn(inactiveUser);

        mockMvc.perform(get("/goals/api/v1/goals")
                        .with(user(inactiveUser)))
                .andExpect(status().isNotFound());
    }

    // =====================================================================
    // Helper Methods
    // =====================================================================

    private Goal createTestGoal(int goalId) {
        return new Goal()
                .setGoalId(goalId)
                .setStudyId(STUDY_ID)
                .setParticipantId(PARTICIPANT_ID)
                .setTemplateId(101)
                .setTitle("Daily Step Goal")
                .setAdherenceCheckIds(Set.of(0)) // 0 = MORNING
                .setProperties(java.util.Map.of("target", 10000))
                .setCreated(NOW);
    }
}