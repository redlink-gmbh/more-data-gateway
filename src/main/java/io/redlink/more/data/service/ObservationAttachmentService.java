/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Österreichische Vereinigung zur
 * Förderung der wissenschaftlichen Forschung).
 * Licensed under the Apache License, Version 2.0.
 */
package io.redlink.more.data.service;

import io.redlink.more.data.model.ObservationAttachment;
import io.redlink.more.data.repository.ObservationAttachmentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only access to files attached to observations. Uploads are handled by the Studymanager Backend, which keeps the
 * metadata in the database and the content on the filesystem at {@code {dir}/{studyId}/{observationId}/{attachmentId}}
 * (paths are built from ids only, never from user input). {@code more.attachments.dir} must point to the same
 * (shared) directory as the Studymanager's.
 */
@Service
public class ObservationAttachmentService {

    private final ObservationAttachmentRepository repository;
    private final Path baseDir;

    public ObservationAttachmentService(ObservationAttachmentRepository repository,
                                        @Value("${more.attachments.dir}") Path baseDir) {
        this.repository = repository;
        this.baseDir = baseDir.toAbsolutePath().normalize();
    }

    public List<ObservationAttachment> listAttachments(long studyId, int observationId) {
        return repository.listAttachments(studyId, observationId);
    }

    public Optional<ObservationAttachment> getAttachment(long studyId, int observationId, UUID attachmentId) {
        return repository.getAttachment(studyId, observationId, attachmentId);
    }

    public Path getFile(ObservationAttachment attachment) {
        return baseDir.resolve(String.valueOf(attachment.studyId()))
                .resolve(String.valueOf(attachment.observationId()))
                .resolve(attachment.attachmentId().toString());
    }
}
