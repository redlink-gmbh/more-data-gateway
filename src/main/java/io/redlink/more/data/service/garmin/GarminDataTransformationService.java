package io.redlink.more.data.service.garmin;

import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.ParticipantKeyValue;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.garmin.GarminSummaryType;
import io.redlink.more.data.model.garmin.ParticipantGarminDataPoint;
import io.redlink.more.data.repository.StudyRepository;
import io.redlink.more.data.transformers.garmin.AbstractGarminTransformer;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Collectors;

@Service
public class GarminDataTransformationService {

    private final StudyRepository studyRepository;
    private final Map<GarminSummaryType, AbstractGarminTransformer> transformerByType;

    public GarminDataTransformationService(
            StudyRepository studyRepository,
            List<AbstractGarminTransformer> transformers
    ) {
        this.studyRepository = studyRepository;
        this.transformerByType = transformers.stream()
                .collect(Collectors.toMap(AbstractGarminTransformer::getSupportedType, t -> t));
    }

    public Map<RoutingInfo, List<DataPoint>> transformData(
            GarminSummaryType summaryType,
            List<ParticipantGarminDataPoint> participantGarminDataPoints) {

        AbstractGarminTransformer transformer = transformerByType.get(summaryType);
        if (transformer == null) {
            return Collections.emptyMap();
        }

        return participantGarminDataPoints
                .stream()
                .map(participantGarminDataPoint -> {
                    var participant = participantGarminDataPoint.participantKeyValue();

                    List<Observation> observations =
                            studyRepository.filterObservations(
                                    participant.studyId(),
                                    participant.participantId(),
                                    observation -> observation.type().contains(GarminService.GARMIN_KEY_TYPE)
                            );

                    List<DataPoint> dataPoints = participantGarminDataPoint.garminDataPoints()
                            .parallelStream()
                            .flatMap(data -> observations.stream()
                                    .flatMap(observation ->
                                            transformer.transform(
                                                    String.valueOf(observation.observationId()),
                                                    observation.type(),
                                                    data
                                            ).stream()
                                    )
                            )
                            .toList();

                    return Map.entry(
                            participantKeyValueToRoutingInfo(participant),
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