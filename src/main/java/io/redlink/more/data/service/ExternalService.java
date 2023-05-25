package io.redlink.more.data.service;

import io.redlink.more.data.exception.BadRequestException;
import io.redlink.more.data.exception.NotFoundException;
import io.redlink.more.data.model.ApiRoutingInfo;
import io.redlink.more.data.repository.StudyRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.OptionalInt;

@Service
public class ExternalService {
    private final StudyRepository repository;
    private final PasswordEncoder passwordEncoder;

    public ExternalService(StudyRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }
    public Optional<ApiRoutingInfo> getRoutingInfo(
            Long studyId, Integer observationId, Integer tokenId, String apiSecret
    ) {
        return repository.getApiRoutingInfo(studyId, observationId, tokenId)
                .stream().filter(route ->
                        passwordEncoder.matches(apiSecret, route.secret()))
                .findFirst();
    }

    public ApiRoutingInfo validateRoutingInfo(ApiRoutingInfo routingInfo, Integer participantId) {
        Optional<OptionalInt> participantOptional = repository.getParticipantStudyGroupId(routingInfo.studyId(), participantId);
        if(participantOptional.isEmpty()) {
            throw NotFoundException.Participant(participantId);
        }
        OptionalInt observationStudyGroup = routingInfo.studyGroupId();
        OptionalInt participantStudyGroup = participantOptional.get();

        if(observationStudyGroup.isPresent() && participantStudyGroup.isPresent() && observationStudyGroup.getAsInt() != participantStudyGroup.getAsInt()){
            throw BadRequestException.StudyGroup(observationStudyGroup.getAsInt(), participantStudyGroup.getAsInt());
        }
        return routingInfo.withParticipantStudyGroup(participantStudyGroup);
    }
}
