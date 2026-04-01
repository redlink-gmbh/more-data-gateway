package io.redlink.more.data.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.redlink.more.data.api.participant.v1.model.StudyConsentDTO;
import io.redlink.more.data.configuration.SecurityConfig;
import io.redlink.more.data.controller.participantPortal.ParticipantPortalController;
import io.redlink.more.data.model.Contact;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.SimpleParticipant;
import io.redlink.more.data.model.Study;
import io.redlink.more.data.service.ApplicationAccessService;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Collections;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ParticipantPortalController.class)
@Import(SecurityConfig.class)
@AutoConfigureMockMvc
class ParticipantPortalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ApplicationAccessService applicationAccessService;

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
    void testParticipantLoginSetsSession() throws Exception {
        Long studyId = 1L;
        String userDataRef = "user-ref";
        String loginCode = "login-code";
        String encodedCode = Base64.getEncoder().encodeToString(loginCode.getBytes(StandardCharsets.UTF_8));
        RoutingInfo routingInfo = new RoutingInfo(studyId, 1, OptionalInt.empty(), Set.of(), true, true);

        when(applicationAccessService.validateLogin(studyId, userDataRef, loginCode))
                .thenReturn(Optional.of(routingInfo));
        when(studyService.getStudyState(routingInfo)).thenReturn(Optional.of("active"));

        MvcResult result = mockMvc.perform(post("/participant-portal/api/v1/login/{studyId}/{userDataRef}", studyId, userDataRef)
                        .with(csrf())
                        .header("more-login-code", encodedCode))
                .andExpect(status().isNoContent())
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        assertNotNull(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
    }

    @Test
    void testParticipantLoginInvalidBase64() throws Exception {
        Long studyId = 1L;
        String userDataRef = "user-ref";
        String invalidEncodedCode = "!!! not base64 !!!";

        mockMvc.perform(post("/participant-portal/api/v1/login/{studyId}/{userDataRef}", studyId, userDataRef)
                        .with(csrf())
                        .header("more-login-code", invalidEncodedCode))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testParticipantLoginInvalidCredentials() throws Exception {
        Long studyId = 1L;
        String userDataRef = "user-ref";
        String loginCode = "wrong-code";
        String encodedCode = Base64.getEncoder().encodeToString(loginCode.getBytes(StandardCharsets.UTF_8));

        when(applicationAccessService.validateLogin(studyId, userDataRef, loginCode))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/participant-portal/api/v1/login/{studyId}/{userDataRef}", studyId, userDataRef)
                        .with(csrf())
                        .header("more-login-code", encodedCode))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testParticipantLogout() throws Exception {
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);
        RoutingInfoUserDetails userDetails = new RoutingInfoUserDetails(routingInfo, null);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, "some-context");

        mockMvc.perform(post("/participant-portal/api/v1/logout")
                        .with(user(userDetails))
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertTrue(session.isInvalid());
    }

    @Test
    void testParticipantLogoutNoSession() throws Exception {
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);
        RoutingInfoUserDetails userDetails = new RoutingInfoUserDetails(routingInfo, null);

        mockMvc.perform(post("/participant-portal/api/v1/logout")
                        .with(user(userDetails))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void testParticipantLogoutUnauthenticated() throws Exception {
        mockMvc.perform(post("/participant-portal/api/v1/logout")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testAcceptConsentSuccess() throws Exception {
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);
        RoutingInfoUserDetails userDetails = new RoutingInfoUserDetails(routingInfo, null);

        StudyConsentDTO consentDTO = new StudyConsentDTO();
        consentDTO.setConsent(true);
        consentDTO.setDeviceId("MODEL#SERIAL");

        when(applicationAccessService.hasConsent(routingInfo)).thenReturn(false);
        when(studyService.getCompleteRoutingInfo(routingInfo)).thenReturn(Optional.of(routingInfo));
        when(studyService.getStudyState(routingInfo)).thenReturn(Optional.of("active"));

        mockMvc.perform(post("/participant-portal/api/v1/consent")
                        .with(csrf())
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(consentDTO)))
                .andExpect(status().isNoContent());

        verify(applicationAccessService).validateAndStoreConsent(eq(routingInfo), any());
    }

    @Test
    void testAcceptConsentAlreadyReported() throws Exception {
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);
        RoutingInfoUserDetails userDetails = new RoutingInfoUserDetails(routingInfo, null);

        when(applicationAccessService.hasConsent(routingInfo)).thenReturn(true);
        when(studyService.getCompleteRoutingInfo(routingInfo)).thenReturn(Optional.of(routingInfo));
        when(studyService.getStudyState(routingInfo)).thenReturn(Optional.of("active"));

        mockMvc.perform(post("/participant-portal/api/v1/consent")
                        .with(csrf())
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void testRetrieveConsentDataSuccess() throws Exception {
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);
        RoutingInfoUserDetails userDetails = new RoutingInfoUserDetails(routingInfo, null);
        Study study = new Study(1L, "Title", true, "Info", "Finish", "active", "Consent",
                new Contact("Inst", "Person", "email", "phone"),
                LocalDate.now(), LocalDate.now(), LocalDate.now().plusDays(10),
                Collections.emptyList(), Instant.now(), Instant.now(),
                new SimpleParticipant(1, "alias", Instant.now(), Instant.now().plus(Duration.ofDays(10))));

        when(studyService.getCompleteRoutingInfo(routingInfo)).thenReturn(Optional.of(routingInfo));
        when(applicationAccessService.hasConsent(routingInfo)).thenReturn(false);
        when(studyService.getStudyState(routingInfo)).thenReturn(Optional.of("active"));
        when(studyService.getStudy(routingInfo)).thenReturn(Optional.of(Pair.of(study, Collections.emptyList())));

        mockMvc.perform(get("/participant-portal/api/v1/consent")
                        .with(user(userDetails)))
                .andExpect(status().isOk());
    }

    @Test
    void testRetrieveConsentDataUnauthorized() throws Exception {
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);
        RoutingInfoUserDetails userDetails = new RoutingInfoUserDetails(routingInfo, null);

        when(studyService.getCompleteRoutingInfo(routingInfo)).thenReturn(Optional.empty());

        mockMvc.perform(get("/participant-portal/api/v1/consent")
                        .with(user(userDetails)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testRetrieveConsentDataAlreadyReported() throws Exception {
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);
        RoutingInfoUserDetails userDetails = new RoutingInfoUserDetails(routingInfo, null);

        when(studyService.getCompleteRoutingInfo(routingInfo)).thenReturn(Optional.of(routingInfo));
        when(studyService.getStudyState(routingInfo)).thenReturn(Optional.of("active"));
        when(applicationAccessService.hasConsent(routingInfo)).thenReturn(true);

        mockMvc.perform(get("/participant-portal/api/v1/consent")
                        .with(user(userDetails)))
                .andExpect(status().isConflict());
    }

    @Test
    void testRetrieveConsentDataStudyNotFound() throws Exception {
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);
        RoutingInfoUserDetails userDetails = new RoutingInfoUserDetails(routingInfo, null);

        when(studyService.getCompleteRoutingInfo(routingInfo)).thenReturn(Optional.of(routingInfo));
        when(applicationAccessService.hasConsent(routingInfo)).thenReturn(false);
        when(studyService.getStudyState(routingInfo)).thenReturn(Optional.of("active"));
        when(studyService.getStudy(routingInfo)).thenReturn(Optional.empty());

        mockMvc.perform(get("/participant-portal/api/v1/consent")
                        .with(user(userDetails)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void testAcceptConsentWithMockUserFails() throws Exception {
        // This should fail because the principal is not RoutingInfoUserDetails
        // It's now handled by the ExceptionHandler and returns 500
        mockMvc.perform(post("/participant-portal/api/v1/consent")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

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
        when(studyService.getStudyState(routingInfo)).thenReturn(Optional.of("active"));
        when(studyService.getStudy(routingInfo)).thenReturn(Optional.of(Pair.of(study, Collections.emptyList())));

        mockMvc.perform(get("/participant-portal/api/v1/config/study")
                        .with(user(userDetails)))
                .andExpect(status().isOk());
    }

    @Test
    void testGetStudyConfigurationUnauthorized() throws Exception {
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);
        RoutingInfoUserDetails userDetails = new RoutingInfoUserDetails(routingInfo, null);

        when(studyService.getCompleteRoutingInfo(routingInfo)).thenReturn(Optional.empty());

        mockMvc.perform(get("/participant-portal/api/v1/config/study")
                        .with(user(userDetails)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testGetStudyConfigurationNotFound() throws Exception {
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);
        RoutingInfoUserDetails userDetails = new RoutingInfoUserDetails(routingInfo, null);

        when(studyService.getCompleteRoutingInfo(routingInfo)).thenReturn(Optional.of(routingInfo));
        when(studyService.getStudyState(routingInfo)).thenReturn(Optional.of("active"));
        when(studyService.getStudy(routingInfo)).thenReturn(Optional.empty());

        mockMvc.perform(get("/participant-portal/api/v1/config/study")
                        .with(user(userDetails)))
                .andExpect(status().isNotFound());
    }

}
