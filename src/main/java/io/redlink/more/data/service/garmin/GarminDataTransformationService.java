package io.redlink.more.data.service.garmin;

import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.ParticipantKeyValue;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.garmin.GarminSummaryType;
import io.redlink.more.data.model.garmin.ParticipantGarminDataPoint;
import io.redlink.more.data.repository.StudyRepository;
import io.redlink.more.data.transformers.garmin.AbstractGarminTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Collectors;

@Service
public class GarminDataTransformationService {
    private static final Logger LOG = LoggerFactory.getLogger(GarminDataTransformationService.class);
    private final StudyRepository studyRepository;
    private final Map<GarminSummaryType, List<AbstractGarminTransformer>> transformersByType;

    public GarminDataTransformationService(
            StudyRepository studyRepository,
            List<AbstractGarminTransformer> transformers
    ) {
        this.studyRepository = studyRepository;
        this.transformersByType = transformers.stream()
                .collect(Collectors.groupingBy(AbstractGarminTransformer::getSupportedType));
    }

    public Map<RoutingInfo, List<DataPoint>> transformData(
            GarminSummaryType summaryType,
            List<ParticipantGarminDataPoint> participantGarminDataPoints) {

        LOG.debug("Transforming Garmin summary type {} to data points", summaryType);
        List<AbstractGarminTransformer> transformers = transformersByType.get(summaryType);

        if (transformers == null || transformers.isEmpty()) {
            LOG.debug("No transformer found for type {}", summaryType);
            return Collections.emptyMap();
        }

        return participantGarminDataPoints
                .stream()
                .map(participantGarminDataPoint -> {
                    var routingInfo = participantKeyValueToRoutingInfo(participantGarminDataPoint.participantKeyValue());

                    var simpleParticipant = studyRepository.findParticipant(routingInfo);
                    if (simpleParticipant.isEmpty()) {
                        LOG.warn("No participant found for {}", routingInfo);
                        return Map.entry(routingInfo, Collections.<DataPoint>emptyList());
                    }
                    var participantStart = simpleParticipant.get().start();
                    var participantEnd = simpleParticipant.get().end();

                    if (participantStart == null || participantEnd == null || participantStart.toEpochMilli() >= participantEnd.toEpochMilli()) {
                        LOG.warn("Participant start or end time is invalid: {}", simpleParticipant);
                        return Map.entry(routingInfo, Collections.<DataPoint>emptyList());
                    }

                    List<Observation> observations =
                            studyRepository.filterObservations(
                                    routingInfo,
                                    false,
                                    observation -> observation.type().contains(GarminService.GARMIN_KEY_TYPE)
                            );

                    List<DataPoint> dataPoints = participantGarminDataPoint.garminDataPoints()
                            .parallelStream()
                            .flatMap(data -> transformers
                                    .stream()
                                    .flatMap(t -> {
                                        List<Observation> filteredObservations = observations.stream()
                                                .filter(o -> o.type().equals(t.getRequiredObservationType()))
                                                .toList();
                                        return t.transform(filteredObservations, data, participantStart, participantEnd, routingInfo.studyId(), routingInfo.participantId()).stream();
                                    })
                            )
                            .toList();

                    return Map.entry(
                            routingInfo,
                            dataPoints
                    );
                })
                .filter(entry -> !entry.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private RoutingInfo participantKeyValueToRoutingInfo(ParticipantKeyValue participantKeyValue) {
        return new RoutingInfo(
                participantKeyValue.studyId(),
                participantKeyValue.participantId(),
                OptionalInt.empty(),
                true,
                true
        );
    }
}