package io.redlink.more.data.controller;

import io.redlink.more.data.api.app.v1.model.ObservationAttachmentDTO;
import io.redlink.more.data.configuration.AuthenticationFacade;
import io.redlink.more.data.exception.NotFoundException;
import io.redlink.more.data.model.GatewayUserDetails;
import io.redlink.more.data.model.ObservationAttachment;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.service.GatewayUserDetailService;
import io.redlink.more.data.service.ObservationAttachmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObservationAttachmentApiV1ControllerTest {

    private static final long STUDY_ID = 42L;
    private static final int OBSERVATION_ID = 7;

    @Mock
    private AuthenticationFacade authenticationFacade;

    @Mock
    private ObservationAttachmentService service;

    @TempDir
    Path tempDir;

    private ObservationAttachmentApiV1Controller controller;

    private final ObservationAttachment attachment = new ObservationAttachment(
            UUID.randomUUID(), STUDY_ID, OBSERVATION_ID, "info sheet.pdf", "application/pdf", 5,
            Instant.parse("2026-10-01T10:00:00Z"));

    @BeforeEach
    void setUp() {
        controller = new ObservationAttachmentApiV1Controller(authenticationFacade, service);
        RoutingInfo routingInfo = new RoutingInfo(STUDY_ID, 101, OptionalInt.empty(), Set.of(), true, true);
        when(authenticationFacade.assertAuthority(GatewayUserDetailService.APP_ROLE))
                .thenReturn(new GatewayUserDetails("user", "pass", Set.of(), routingInfo));
    }

    @Test
    void listObservationAttachments_maps_attachments_of_participants_study() {
        when(service.listAttachments(STUDY_ID, OBSERVATION_ID)).thenReturn(List.of(attachment));

        ResponseEntity<List<ObservationAttachmentDTO>> response = controller.listObservationAttachments(OBSERVATION_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ObservationAttachmentDTO dto = response.getBody().get(0);
        assertThat(dto.getAttachmentId()).isEqualTo(attachment.attachmentId());
        assertThat(dto.getFilename()).isEqualTo("info sheet.pdf");
        assertThat(dto.getContentType()).isEqualTo("application/pdf");
        assertThat(dto.getSize()).isEqualTo(5L);
        assertThat(dto.getCreated()).isEqualTo(attachment.created());
    }

    @Test
    void downloadObservationAttachment_returns_file_with_headers() throws Exception {
        Path file = Files.writeString(tempDir.resolve("content"), "hello");
        when(service.getAttachment(STUDY_ID, OBSERVATION_ID, attachment.attachmentId())).thenReturn(Optional.of(attachment));
        when(service.getFile(attachment)).thenReturn(file);

        ResponseEntity<Resource> response = controller.downloadObservationAttachment(OBSERVATION_ID, attachment.attachmentId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().getContentLength()).isEqualTo(5L);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("inline").contains("info");
        assertThat(response.getBody().getContentAsByteArray()).isEqualTo("hello".getBytes());
    }

    @Test
    void downloadObservationAttachment_unknown_attachment_is_not_found() {
        UUID unknown = UUID.randomUUID();
        when(service.getAttachment(STUDY_ID, OBSERVATION_ID, unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.downloadObservationAttachment(OBSERVATION_ID, unknown))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void downloadObservationAttachment_missing_file_is_not_found() {
        when(service.getAttachment(STUDY_ID, OBSERVATION_ID, attachment.attachmentId())).thenReturn(Optional.of(attachment));
        when(service.getFile(attachment)).thenReturn(tempDir.resolve("missing"));

        assertThatThrownBy(() -> controller.downloadObservationAttachment(OBSERVATION_ID, attachment.attachmentId()))
                .isInstanceOf(NotFoundException.class);
    }
}
