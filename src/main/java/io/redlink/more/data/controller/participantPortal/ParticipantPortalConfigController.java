package io.redlink.more.data.controller.participantPortal;

import io.redlink.more.data.api.participant.v1.model.StudyDTO;
import io.redlink.more.data.api.participant.v1.webservices.ConfigurationApi;
import io.redlink.more.data.controller.transformer.ParticipantPortalTransformer;
import io.redlink.more.data.exception.NotAuthorizedException;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.service.StudyService;
import io.redlink.more.data.util.RoutingInfoUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Controller
@RestController
@RequestMapping(value = "/participant-portal/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class ParticipantPortalConfigController implements ConfigurationApi {
    private final StudyService studyService;

    public ParticipantPortalConfigController(StudyService studyService) {
        this.studyService = studyService;
    }

    @Override
    public ResponseEntity<StudyDTO> getStudyConfiguration() {
        RoutingInfo routingInfo = studyService
                .getCompleteRoutingInfo(RoutingInfoUserDetails.getCurrent().getRoutingInfo())
                .orElseThrow(NotAuthorizedException::new);

        var studyData = studyService.getStudy(routingInfo);
        return studyData
                .map(studyListPair ->
                        ResponseEntity.ok(ParticipantPortalTransformer.toDTO(studyListPair.getLeft(), studyListPair.getRight())))
                .orElseGet(() ->
                        ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}
