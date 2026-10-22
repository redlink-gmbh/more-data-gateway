package io.redlink.more.data.controller.transformer;

import io.redlink.more.data.exception.NotFoundException;
import io.redlink.more.data.model.ObservationAttachment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ObservationAttachmentTransformer {

    private ObservationAttachmentTransformer() {
    }

    public static io.redlink.more.data.api.app.v1.model.ObservationAttachmentDTO toAppDTO(ObservationAttachment attachment) {
        return new io.redlink.more.data.api.app.v1.model.ObservationAttachmentDTO()
                .attachmentId(attachment.attachmentId())
                .filename(attachment.filename())
                .contentType(attachment.contentType())
                .size(attachment.size())
                .created(attachment.created());
    }

    public static io.redlink.more.data.api.participant.v1.model.ObservationAttachmentDTO toParticipantDTO(ObservationAttachment attachment) {
        return new io.redlink.more.data.api.participant.v1.model.ObservationAttachmentDTO()
                .attachmentId(attachment.attachmentId())
                .filename(attachment.filename())
                .contentType(attachment.contentType())
                .size(attachment.size())
                .created(attachment.created());
    }

    public static ResponseEntity<Resource> toFileResponse(ObservationAttachment attachment, Path file) {
        if (!Files.isReadable(file)) {
            throw new NotFoundException("Content of attachment " + attachment.attachmentId() + " not found");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.contentType()))
                .contentLength(attachment.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(attachment.filename(), StandardCharsets.UTF_8)
                        .build().toString())
                .body(new FileSystemResource(file));
    }
}
