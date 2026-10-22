package io.redlink.more.data.controller;

import io.redlink.more.data.api.app.v1.model.ObservationDTO;
import io.redlink.more.data.configuration.AuthenticationFacade;
import io.redlink.more.data.model.Contact;
import io.redlink.more.data.model.GatewayUserDetails;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.SimpleParticipant;
import io.redlink.more.data.model.Study;
import io.redlink.more.data.service.GatewayUserDetailService;
import io.redlink.more.data.service.ObservationExecutionService;
import io.redlink.more.data.service.PushNotificationService;
import io.redlink.more.data.service.StudyService;
import io.redlink.more.data.service.milestone.ParticipantMilestoneService;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigurationApiV1ControllerTest {

    @Mock
    private AuthenticationFacade authenticationFacade;
    @Mock
    private PushNotificationService pushNotificationService;
    @Mock
    private StudyService studyService;
    @Mock
    private ObservationExecutionService observationExecutionService;
    @Mock
    private ParticipantMilestoneService participantMilestoneService;

    private ConfigurationApiV1Controller controller;

    private final RoutingInfo routingInfo = new RoutingInfo(1L, 101, OptionalInt.empty(), Set.of(), true, true);

    @BeforeEach
    void setUp() {
        controller = new ConfigurationApiV1Controller(authenticationFacade, pushNotificationService, studyService,
                observationExecutionService, participantMilestoneService);
        when(authenticationFacade.assertAuthority(GatewayUserDetailService.APP_ROLE))
                .thenReturn(new GatewayUserDetails("user", "pass", Set.of(), routingInfo));
    }

    @Test
    void getObservation_returns_requested_observation() {
        mockActiveStudy();

        ResponseEntity<ObservationDTO> response = controller.getObservation(2);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getObservationId()).isEqualTo("2");
        assertThat(response.getBody().getObservationType()).isEqualTo("info-observation");
        assertThat(response.getBody().getConfiguration()).isEqualTo(Map.of("text", "updated"));
    }

    @Test
    void getObservation_returns_404_for_unknown_observation() {
        mockActiveStudy();

        assertThat(controller.getObservation(99).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getObservation_returns_404_if_study_not_preview_or_active() {
        when(studyService.isActive(routingInfo)).thenReturn(false);

        assertThat(controller.getObservation(2).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(studyService, never()).getStudy(any());
    }

    private void mockActiveStudy() {
        Instant now = Instant.now();
        List<Observation> observations = List.of(
                new Observation(1, null, "Survey", "simple-question-observation", "info", Map.of(), null,
                        now, now, false, false, false, Set.of()),
                new Observation(2, null, "Info", "info-observation", "info", Map.of("text", "updated"), null,
                        now, now, false, true, false, Set.of()));
        Study study = new Study(1L, "Study", true, "info", "finish", "active", "consent",
                new Contact("inst", "person", "mail", "phone"), LocalDate.now(), LocalDate.now(), LocalDate.now(),
                observations, now, now, new SimpleParticipant(101, "alias", now, now));

        when(studyService.isActive(routingInfo)).thenReturn(true);
        when(studyService.getStudy(routingInfo)).thenReturn(Optional.of(Pair.of(study, List.of())));
        when(observationExecutionService.getCompletedData(routingInfo)).thenReturn(List.of());
    }
}
