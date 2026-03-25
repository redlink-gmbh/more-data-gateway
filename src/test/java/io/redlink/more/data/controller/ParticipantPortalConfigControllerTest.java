package io.redlink.more.data.controller;

import io.redlink.more.data.configuration.SecurityConfig;
import io.redlink.more.data.controller.participantPortal.ParticipantPortalConfigController;
import io.redlink.more.data.model.Contact;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.SimpleParticipant;
import io.redlink.more.data.model.Study;
import io.redlink.more.data.service.GatewayUserDetailService;
import io.redlink.more.data.service.LoginTokenService;
import io.redlink.more.data.service.RegistrationService;
import io.redlink.more.data.service.StudyService;
import io.redlink.more.data.util.RoutingInfoUserDetails;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ParticipantPortalConfigController.class)
@Import(SecurityConfig.class)
@AutoConfigureMockMvc
class ParticipantPortalConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudyService studyService;

    @MockitoBean
    private LoginTokenService loginTokenService;

    @MockitoBean
    private RegistrationService registrationService;

    @MockitoBean
    private GatewayUserDetailService gatewayUserDetailService;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @Test
    void testGetStudyConfigurationSuccess() throws Exception {
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);
        RoutingInfoUserDetails userDetails = new RoutingInfoUserDetails(routingInfo, null);
        Study study = new Study(1L, "Title", true, "Info", "Finish", "active", "Consent",
                new Contact("Inst", "Person", "email", "phone"),
                LocalDate.now(), LocalDate.now(), LocalDate.now().plusDays(10),
                Collections.emptyList(), Instant.now(), Instant.now(),
                new SimpleParticipant(1, "alias", Instant.now(), Instant.now().plus(Duration.ofDays(10))));

        when(studyService.getCompleteRoutingInfo(routingInfo)).thenReturn(Optional.of(routingInfo));
        when(studyService.getStudy(routingInfo)).thenReturn(Optional.of(Pair.of(study, Collections.emptyList())));

        mockMvc.perform(get("/participantPortal/api/v1/config/study")
                        .with(user(userDetails)))
                .andExpect(status().isOk());
    }

    @Test
    void testGetStudyConfigurationUnauthorized() throws Exception {
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);
        RoutingInfoUserDetails userDetails = new RoutingInfoUserDetails(routingInfo, null);

        when(studyService.getCompleteRoutingInfo(routingInfo)).thenReturn(Optional.empty());

        mockMvc.perform(get("/participantPortal/api/v1/config/study")
                        .with(user(userDetails)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testGetStudyConfigurationNotFound() throws Exception {
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);
        RoutingInfoUserDetails userDetails = new RoutingInfoUserDetails(routingInfo, null);

        when(studyService.getCompleteRoutingInfo(routingInfo)).thenReturn(Optional.of(routingInfo));
        when(studyService.getStudy(routingInfo)).thenReturn(Optional.empty());

        mockMvc.perform(get("/participantPortal/api/v1/config/study")
                        .with(user(userDetails)))
                .andExpect(status().isNotFound());
    }
}
