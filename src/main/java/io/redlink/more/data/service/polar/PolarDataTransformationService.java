package io.redlink.more.data.service.polar;

import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.polar.PolarDataType;
import io.redlink.more.data.repository.StudyRepository;
import io.redlink.more.data.transformers.polar.AbstractPolarTransformer;
import org.apache.commons.lang3.Range;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PolarDataTransformationService {
    private static final Logger LOG = LoggerFactory.getLogger(PolarDataTransformationService.class);

    private final StudyRepository studyRepository;
    private final Map<PolarDataType, List<AbstractPolarTransformer>> transformersByType;

    public PolarDataTransformationService(
            StudyRepository studyRepository,
            List<AbstractPolarTransformer> transformers
    ) {
        this.studyRepository = studyRepository;
        this.transformersByType = transformers.stream()
                .collect(Collectors.groupingBy(AbstractPolarTransformer::getSupportedType));
    }

    /**
     * Transform a raw Polar batch of a specific data type into per-participant {@link DataPoint}
     * lists keyed by {@link RoutingInfo}.
     *
     * @param dataType     the Polar data type of the incoming batch (HR, PPI, TEMPERATURE, …)
     * @param routingInfo  participant routing info
     * @param rawDataItems the flat list of raw data items from the Polar push
     */
    public Map<RoutingInfo, List<DataPoint>> transformData(
            PolarDataType dataType,
            RoutingInfo routingInfo,
            List<Map<String, Object>> rawDataItems
    ) {
        if (rawDataItems == null || rawDataItems.isEmpty()) {
            return Collections.emptyMap();
        }

        List<AbstractPolarTransformer> transformers = transformersByType.get(dataType);
        if (transformers == null || transformers.isEmpty()) {
            LOG.debug("No transformer found for PolarDataType={}", dataType);
            return Collections.emptyMap();
        }

        Range<Instant> participantTimeRange = getParticipantTimeRange(routingInfo);
        if (participantTimeRange == null) {
            return Collections.emptyMap();
        }

        Set<String> observationTypes = transformers.stream()
                .map(AbstractPolarTransformer::getObservationType)
                .collect(Collectors.toSet());

        List<Observation> observations = studyRepository.getObservationsByTypes(
                routingInfo,
                observationTypes,
                routingInfo.observationGroupIds()
        );

        List<DataPoint> dataPoints = rawDataItems.stream()
                .flatMap(item -> transformers.stream()
                        .flatMap(transformer -> transformer.transform(
                                filterObservationsByType(observations, transformer.getObservationType()),
                                item,
                                participantTimeRange.getMinimum(),
                                participantTimeRange.getMaximum()
                        ).stream())
                )
                .toList();

        if (dataPoints.isEmpty()) {
            LOG.debug("No data points produced for dataType={}, routingInfo={}", dataType, routingInfo);
            return Collections.emptyMap();
        }

        LOG.debug("Produced {} data point(s) for dataType={}, routingInfo={}", dataPoints.size(), dataType, routingInfo);
        return Map.of(routingInfo, dataPoints);
    }

    private Range<Instant> getParticipantTimeRange(RoutingInfo routingInfo) {
        var participant = studyRepository.findParticipant(routingInfo);
        if (participant.isEmpty()) {
            LOG.warn("No participant found for {}", routingInfo);
            return null;
        }
        var start = participant.get().start();
        var end = participant.get().end();
        if (start.isAfter(end)) {
            LOG.warn("Participant {} has start {} after end {}", participant.get(), start, end);
            return null;
        }
        return Range.of(start, end);
    }

    private List<Observation> filterObservationsByType(List<Observation> observations, String type) {
        return observations.stream()
                .filter(o -> o.type().equals(type))
                .toList();
    }
}
