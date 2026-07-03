package io.redlink.more.data.controller.goal;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.redlink.more.data.api.goal.v1.model.AdherenceCheckScheduleEnumDTO;
import io.redlink.more.data.configuration.AuthenticationFacade;
import io.redlink.more.data.configuration.SecurityConfig;
import io.redlink.more.data.controller.GlobalControllerExceptionHandler;
import io.redlink.more.data.model.GatewayUserDetails;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.goal.AdherenceCheck;
import io.redlink.more.data.model.goal.GoalTemplate;
import io.redlink.more.data.model.goal.GoalTopic;
import io.redlink.more.data.model.goal.StudyGoalConfig;
import io.redlink.more.data.service.ApplicationAccessService;
import io.redlink.more.data.service.GatewayUserDetailService;
import io.redlink.more.data.service.goal.GoalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({
        GoalConfigController.class,
        GlobalControllerExceptionHandler.class
})
@Import(SecurityConfig.class)
@AutoConfigureMockMvc
class GoalConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthenticationFacade authenticationFacade;

    @MockitoBean
    private GoalService goalService;

    @MockitoBean
    private GatewayUserDetailService gatewayUserDetailService;


    private static final Long STUDY_ID = 42L;
    private static final int PARTICIPANT_ID = 7;


    // =====================================================================
    // getGoalConfig()
    // =====================================================================

    @Test
    void getGoalConfig_Success() throws Exception {
        RoutingInfo routingInfo = new RoutingInfo(STUDY_ID, PARTICIPANT_ID, -1, Set.of(), true, true);
        GatewayUserDetails userDetails = new GatewayUserDetails("app-user", "app-user-pwd", Set.of(GatewayUserDetailService.APP_ROLE), routingInfo);
        StudyGoalConfig config = createTestStudyGoalConfig();

        when(authenticationFacade.assertAuthority(GatewayUserDetailService.APP_ROLE))
                .thenReturn(userDetails);
        when(goalService.getStudyGoalConfig(routingInfo)).thenReturn(config);

        mockMvc.perform(get("/goals/api/v1/config")
                        .with(user(userDetails))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consents.commitment").value("I commit to..."))
                .andExpect(jsonPath("$.consents.achievability").value("This is achievable"))
                .andExpect(jsonPath("$.consents.understandable").value("I understand"))
                .andExpect(jsonPath("$.schedule[0].key").value("morning"))
                .andExpect(jsonPath("$.schedule[0].time").value("08:00:00"))
                .andExpect(jsonPath("$.topics[0].key").value("physical-activity"))
                .andExpect(jsonPath("$.topics[0].title").value("Physical Activity"));
    }

    @Test
    void getGoalConfig_StudyNotActive_ReturnsNotFound() throws Exception {
        RoutingInfo routingInfo = new RoutingInfo(STUDY_ID, PARTICIPANT_ID, -1, Set.of(), false, true);
        GatewayUserDetails userDetails = new GatewayUserDetails("app-user", "app-user-pwd", Set.of(GatewayUserDetailService.APP_ROLE), routingInfo);

        when(authenticationFacade.assertAuthority(GatewayUserDetailService.APP_ROLE))
                .thenReturn(userDetails);

        mockMvc.perform(get("/goals/api/v1/config")
                        .with(user(userDetails)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getGoalConfig_ParticipantNotActive_ReturnsNotFound() throws Exception {
        RoutingInfo routingInfo = new RoutingInfo(STUDY_ID, PARTICIPANT_ID, -1, Set.of(), true, false);
        GatewayUserDetails userDetails = new GatewayUserDetails("app-user", "app-user-pwd", Set.of(GatewayUserDetailService.APP_ROLE), routingInfo);

        when(authenticationFacade.assertAuthority(GatewayUserDetailService.APP_ROLE))
                .thenReturn(userDetails);

        mockMvc.perform(get("/goals/api/v1/config")
                        .with(user(userDetails)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getGoalConfig_Unauthorized() throws Exception {
        mockMvc.perform(get("/goals/api/v1/config"))
                .andExpect(status().isUnauthorized());
    }

    // =====================================================================
    // listGoalTemplates()
    // =====================================================================

    @Test
    void listGoalTemplates_Success() throws Exception {
        RoutingInfo routingInfo = new RoutingInfo(STUDY_ID, PARTICIPANT_ID, -1, Set.of(), true, true);
        GatewayUserDetails userDetails = new GatewayUserDetails("app-user", "app-user-pwd", Set.of(GatewayUserDetailService.APP_ROLE), routingInfo);
        List<GoalTemplate> templates = List.of(createTestGoalTemplate());

        when(authenticationFacade.assertAuthority(GatewayUserDetailService.APP_ROLE))
                .thenReturn(userDetails);
        when(goalService.getGoalTemplates(routingInfo)).thenReturn(templates);

        mockMvc.perform(get("/goals/api/v1/templates")
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].templateId").value("goaltemplate_1"))
                .andExpect(jsonPath("$[0].title").value("Daily Step Goal"))
                .andExpect(jsonPath("$[0].info").value("Some info"))
                .andExpect(jsonPath("$[0].type").value("steps"))
                .andExpect(jsonPath("$[0].categories.kind").value("behavioral"))
                .andExpect(jsonPath("$[0].categories.topics[0]").value("physical-activity"))
                .andExpect(jsonPath("$[0].adherenceChecks").isArray())
                .andExpect(jsonPath("$[0].adherenceChecks", containsInAnyOrder("morning", "evening")))
                .andExpect(jsonPath("$[0].properties.target").value(10000));
    }

    @Test
    void listGoalTemplates_StudyNotActive_ReturnsNotFound() throws Exception {
        RoutingInfo routingInfo = new RoutingInfo(STUDY_ID, PARTICIPANT_ID, -1, Set.of(), false, true);
        GatewayUserDetails userDetails = new GatewayUserDetails("app-user", "app-user-pwd", Set.of(GatewayUserDetailService.APP_ROLE), routingInfo);
        when(authenticationFacade.assertAuthority(GatewayUserDetailService.APP_ROLE))
                .thenReturn(userDetails);

        mockMvc.perform(get("/goals/api/v1/templates")
                        .with(user(userDetails)))
                .andExpect(status().isNotFound());
    }
    void listGoalTemplates_ParticipantNotActive_ReturnsNotFound() throws Exception {
        RoutingInfo routingInfo = new RoutingInfo(STUDY_ID, PARTICIPANT_ID, -1, Set.of(), true, false);
        GatewayUserDetails userDetails = new GatewayUserDetails("app-user", "app-user-pwd", Set.of(GatewayUserDetailService.APP_ROLE), routingInfo);
        when(authenticationFacade.assertAuthority(GatewayUserDetailService.APP_ROLE))
                .thenReturn(userDetails);

        mockMvc.perform(get("/goals/api/v1/templates")
                        .with(user(userDetails)))
                .andExpect(status().isNotFound());
    }

    @Test
    void listGoalTemplates_Unauthorized() throws Exception {
        mockMvc.perform(get("/goals/api/v1/templates"))
                .andExpect(status().isUnauthorized());
    }

    // =====================================================================
    // Helper Methods
    // =====================================================================

    private StudyGoalConfig createTestStudyGoalConfig() {
        StudyGoalConfig config = new StudyGoalConfig();
        config.setCommitment("I commit to...");
        config.setAchievability("This is achievable");
        config.setUnderstandability("I understand");

        config.setAdherenceChecks(List.of(
                new AdherenceCheck()
                        .setTitle(AdherenceCheckScheduleEnumDTO.MORNING.getValue())
                        .setCheckId(AdherenceCheckScheduleEnumDTO.MORNING.ordinal())
                        .setTime(LocalTime.of(8, 0))));

        config.setGoalTopics(List.of(
                new GoalTopic()
                        .setKey("physical-activity")
                        .setTitle("Physical Activity")
                        .setDescription("Description...")));

        return config;
    }

    private GoalTemplate createTestGoalTemplate() {
        GoalTemplate template = new GoalTemplate();
        template.setTemplateId(1);
        template.setTitle("Daily Step Goal");
        template.setParticipantTitle("Daily Step Goal");
        template.setType("steps");
        template.setKind("behavioral");
        template.setTopicKeys(Set.of("physical-activity"));
        template.setAdherenceCheckIds(Set.of(AdherenceCheckScheduleEnumDTO.MORNING.ordinal(), AdherenceCheckScheduleEnumDTO.EVENING.ordinal()));
        template.setParticipantInfo("Some info");
        template.setProperties(Map.of("target", 10000));
        return template;
    }
}