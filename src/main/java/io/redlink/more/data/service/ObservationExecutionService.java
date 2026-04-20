package io.redlink.more.data.service;

import io.redlink.more.data.exception.ForbiddenException;
import io.redlink.more.data.exception.NotFoundException;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.ParticipantObservationSeed;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.Study;
import io.redlink.more.data.service.observations.ObservationComponent;
import io.redlink.more.data.util.SchedulerUtils;
import org.apache.commons.lang3.Range;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ObservationExecutionService {
    private final StudyService studyService;
    private final Map<String, ObservationComponent> observationComponents;

    public ObservationExecutionService(StudyService studyService, List<ObservationComponent> observationComponents) {
        this.studyService = studyService;
        this.observationComponents = observationComponents.stream()
                .collect(Collectors.toMap(ObservationComponent::getObservationType, c -> c));
    }

    public String executeObservation(String observationId, Instant scheduleStart, Instant scheduleEnd, RoutingInfo routingInfo) {
        if (observationId.isBlank() || !StringUtils.isNumeric(observationId)) {
            throw new NotFoundException("Observation not found!");
        }
        Optional<Pair<Study, List<ParticipantObservationSeed>>> studyResult = studyService.getStudy(routingInfo);
        if (studyResult.isEmpty()) {
            throw new NotFoundException("Study not found for " + routingInfo);
        }

        Study study = studyResult.get().getLeft();
        List<ParticipantObservationSeed> seeds = studyResult.get().getRight();

        String state = study.studyState();
        if (!"active".equalsIgnoreCase(state) && !"preview".equalsIgnoreCase(state)) {
            throw new ForbiddenException("Study is not active or in preview");
        }

        int observationIdAsInt = Integer.parseInt(observationId);

        Optional<Observation> studyObservation = study.observations().stream()
                .filter(o -> o.observationId() == observationIdAsInt)
                .findFirst();

        if (studyObservation.isEmpty()) {
            throw new NotFoundException("Observation " + observationId + " not found in study " + study.studyId());
        }
        Observation observation = studyObservation.get();

        ObservationComponent component = observationComponents.get(observation.type());
        if (component == null) {
            throw new NotFoundException("Component for observation type " + observation.type() + " not found");
        }

        ParticipantObservationSeed seed = seeds.stream()
                .filter(s -> String.valueOf(s.observationId()).equals(observationId))
                .findFirst()
                .orElse(null);

        ZoneId zoneId = ZoneId.systemDefault();
        Instant start = study.startDate() != null ? study.startDate().atStartOfDay(zoneId).toInstant() : scheduleStart.atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant();
        Instant end = study.endDate() != null ? study.endDate().plusDays(1).atStartOfDay(zoneId).toInstant() : scheduleEnd.atZone(zoneId).toLocalDate().plusDays(1).atStartOfDay(zoneId).toInstant();

        List<Range<Instant>> validRanges = SchedulerUtils.parseToObservationSchedules(seed, observation.observationSchedule(), start, end);
        boolean isValidSchedule = validRanges.stream()
                .anyMatch(r -> (r.getMinimum().equals(scheduleStart) && r.getMaximum().equals(scheduleEnd))
                        || r.contains(scheduleStart) && r.contains(scheduleEnd));

        if (!isValidSchedule) {
            throw new ForbiddenException("Provided schedule is not valid for observation " + observationId);
        }

        return component.produceUrl(observation, routingInfo, scheduleStart, scheduleEnd)
                .orElseThrow(() -> new NotFoundException("Could not produce URL for observation " + observationId));
    }

    public Optional<RoutingInfo> processCallback(String observationId, Instant scheduleStart, Instant scheduleEnd, Optional<RoutingInfo> routingInfo, Map<String, String> parameters) {
        if (routingInfo == null || routingInfo.isEmpty()) {
            for (ObservationComponent component : observationComponents.values()) {
                Optional<RoutingInfo> result = component.processCallback(observationId, parameters, null, null, scheduleStart, scheduleEnd);
                if (result.isPresent()) {
                    return result;
                }
            }
            return Optional.empty();
        }

        Optional<Pair<Study, List<ParticipantObservationSeed>>> studyResult = studyService.getStudy(routingInfo.get());
        if (studyResult.isEmpty()) {
            return Optional.empty();
        }
        Study study = studyResult.get().getLeft();

        Optional<Observation> studyObservation = study.observations().stream()
                .filter(o -> String.valueOf(o.observationId()).equals(observationId))
                .findFirst();

        if (studyObservation.isPresent()) {
            Observation observation = studyObservation.get();
            ObservationComponent component = observationComponents.get(observation.type());
            if (component != null) {
                return component.processCallback(observationId, parameters, routingInfo.get(), observation, scheduleStart, scheduleEnd);
            }
        }
        return Optional.empty();
    }
}
