package io.redlink.more.data.controller;

import io.redlink.more.data.api.app.v1.model.ObservationAttachmentDTO;
import io.redlink.more.data.api.app.v1.webservices.AttachmentsApi;
import io.redlink.more.data.configuration.AuthenticationFacade;
import io.redlink.more.data.controller.transformer.ObservationAttachmentTransformer;
import io.redlink.more.data.exception.NotFoundException;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.service.GatewayUserDetailService;
import io.redlink.more.data.service.ObservationAttachmentService;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class ObservationAttachmentApiV1Controller implements AttachmentsApi {
    private final AuthenticationFacade authenticationFacade;
    private final ObservationAttachmentService service;

    public ObservationAttachmentApiV1Controller(AuthenticationFacade authenticationFacade, ObservationAttachmentService service) {
        this.authenticationFacade = authenticationFacade;
        this.service = service;
    }

    @Override
    public ResponseEntity<List<ObservationAttachmentDTO>> listObservationAttachments(Integer observationId) {
        final RoutingInfo routingInfo = routingInfo();
        return ResponseEntity.ok(service.listAttachments(routingInfo.studyId(), observationId).stream()
                .map(ObservationAttachmentTransformer::toAppDTO)
                .toList());
    }

    @Override
    public ResponseEntity<Resource> downloadObservationAttachment(Integer observationId, UUID attachmentId) {
        final RoutingInfo routingInfo = routingInfo();
        return service.getAttachment(routingInfo.studyId(), observationId, attachmentId)
                .map(a -> ObservationAttachmentTransformer.toFileResponse(a, service.getFile(a)))
                .orElseThrow(() -> new NotFoundException("Attachment " + attachmentId + " not found"));
    }

    private RoutingInfo routingInfo() {
        return authenticationFacade.assertAuthority(GatewayUserDetailService.APP_ROLE).getRoutingInfo();
    }
}
