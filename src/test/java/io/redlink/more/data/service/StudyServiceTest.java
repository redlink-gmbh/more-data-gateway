package io.redlink.more.data.service;

import io.redlink.more.data.model.Contact;
import io.redlink.more.data.model.ParticipantObservationSeed;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.SimpleParticipant;
import io.redlink.more.data.model.Study;
import io.redlink.more.data.repository.StudyRepository;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class StudyServiceTest {
    @Mock
    private StudyRepository studyRepository;

    private StudyService studyService;

    @BeforeEach
    void init() {
        studyService = new StudyService(studyRepository);
    }

    @Test
    void testGetCompleteRoutingInfo() {
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);
        when(studyRepository.getRoutingInfo(1L, 1)).thenReturn(Optional.of(routingInfo));

        Optional<RoutingInfo> result = studyService.getCompleteRoutingInfo(routingInfo);

        assertTrue(result.isPresent());
        assertEquals(routingInfo, result.get());
        verify(studyRepository).getRoutingInfo(1L, 1);
    }

    @Test
    void testGetStudySuccess() {
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);
        Study study = new Study(1L, "Title", true, "Info", "Finish", "active", "Consent",
                new Contact("Inst", "Person", "email", "phone"),
                LocalDate.now(), LocalDate.now(), LocalDate.now().plusDays(10),
                Collections.emptyList(), Instant.now(), Instant.now(),
                new SimpleParticipant(1, "alias", Instant.now(), Instant.now().plus(Duration.ofDays(10))));

        when(studyRepository.findStudy(routingInfo)).thenReturn(Optional.of(study));
        when(studyRepository.getAllParticpantObservationProperties(1L, 1)).thenReturn(Collections.emptyList());

        Optional<Pair<Study, List<ParticipantObservationSeed>>> result = studyService.getStudy(routingInfo);

        assertTrue(result.isPresent());
        assertEquals(study, result.get().getLeft());
        assertTrue(result.get().getRight().isEmpty());
    }

    @Test
    void testGetStudyInactive() {
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);
        Study study = new Study(1L, "Title", false, "Info", "Finish", "active", "Consent",
                new Contact("Inst", "Person", "email", "phone"),
                LocalDate.now(), LocalDate.now(), LocalDate.now().plusDays(10),
                Collections.emptyList(), Instant.now(), Instant.now(),
                new SimpleParticipant(1, "alias", Instant.now(), Instant.now().plus(Duration.ofDays(10))));

        when(studyRepository.findStudy(routingInfo)).thenReturn(Optional.of(study));

        Optional<Pair<Study, List<ParticipantObservationSeed>>> result = studyService.getStudy(routingInfo);

        assertTrue(result.isPresent());
        assertEquals(study, result.get().getLeft());
        assertTrue(result.get().getRight().isEmpty());
    }

    @Test
    void testGetStudyNullRoutingInfo() {
        Optional<Pair<Study, List<ParticipantObservationSeed>>> result = studyService.getStudy(null);
        assertFalse(result.isPresent());
    }

    @Test
    void testGetStudyNotFound() {
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);
        when(studyRepository.findStudy(routingInfo)).thenReturn(Optional.empty());

        Optional<Pair<Study, List<ParticipantObservationSeed>>> result = studyService.getStudy(routingInfo);

        assertFalse(result.isPresent());
    }

    @Test
    void testGetParticipantObservationSeedsEmpty() {
        when(studyRepository.getAllParticpantObservationProperties(1L, 1)).thenReturn(Collections.emptyList());

        List<ParticipantObservationSeed> result = studyService.getParticipantObservationSeeds(1L, 1);

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetParticipantObservationSeedsSuccess() {
        io.redlink.more.data.model.ParticipantWithObservationProperties props = new io.redlink.more.data.model.ParticipantWithObservationProperties(
                1, 1L, 100, java.util.Map.of("observation_schedule_seed", 12345L)
        );
        when(studyRepository.getAllParticpantObservationProperties(1L, 1)).thenReturn(List.of(props));

        List<ParticipantObservationSeed> result = studyService.getParticipantObservationSeeds(1L, 1);

        assertEquals(1, result.size());
        assertEquals(100, result.get(0).observationId());
        assertEquals(12345L, result.get(0).seed());
    }

    @Test
    void testGetParticipantObservationSeedsFilterNoSeed() {
        io.redlink.more.data.model.ParticipantWithObservationProperties props = new io.redlink.more.data.model.ParticipantWithObservationProperties(
                1, 1L, 100, java.util.Map.of()
        );
        when(studyRepository.getAllParticpantObservationProperties(1L, 1)).thenReturn(List.of(props));

        List<ParticipantObservationSeed> result = studyService.getParticipantObservationSeeds(1L, 1);

        assertTrue(result.isEmpty());
    }

}
