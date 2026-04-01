package io.redlink.more.data.service;

import io.redlink.more.data.controller.transformer.StudyTransformer;
import io.redlink.more.data.model.ParticipantObservationSeed;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.Study;
import io.redlink.more.data.repository.StudyRepository;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class StudyService {
    private final StudyRepository studyRepository;

    public StudyService(StudyRepository studyRepository) {
        this.studyRepository = studyRepository;
    }

    public Optional<RoutingInfo> getCompleteRoutingInfo(RoutingInfo routingInfo) {
        return studyRepository.getRoutingInfo(routingInfo.studyId(), routingInfo.participantId());
    }

    public Optional<String> getStudyState(RoutingInfo routingInfo) {
        return studyRepository.getStudyState(routingInfo.studyId());
    }

    public Optional<Pair<Study, List<ParticipantObservationSeed>>> getStudy(RoutingInfo routingInfo) {
        if (routingInfo == null) {
            return Optional.empty();
        }
        Optional<Study> study = studyRepository.findStudy(routingInfo);
        return study
                .map(value -> Pair.of(
                        value,
                        value.active()
                                ? getParticipantObservationSeeds(routingInfo.studyId(), routingInfo.participantId())
                                : Collections.emptyList()))
                ;
    }


    public List<ParticipantObservationSeed> getParticipantObservationSeeds(Long studyId, Integer participantId) {
        var properties = studyRepository.getAllParticpantObservationProperties(studyId, participantId);
        if (properties == null || properties.isEmpty()) {
            return Collections.emptyList();
        }
        return properties
                .stream()
                .map(StudyTransformer::toParticipantObservationSeed)
                .filter(seed -> seed.seed() != null)
                .toList();
    }
}
