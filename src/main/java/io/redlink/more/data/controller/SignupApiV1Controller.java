/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.controller;

import io.redlink.more.data.api.app.v1.webservices.SignupApi;
import io.redlink.more.data.controller.transformer.StudyHTMLTransformer;
import io.redlink.more.data.service.RegistrationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Controller
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.TEXT_HTML_VALUE)
public class SignupApiV1Controller implements SignupApi {
    private final RegistrationService registrationService;

    public SignupApiV1Controller(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @Override
    public ResponseEntity<String> getSignupInfo(String token) {
        return registrationService.loadStudyByRegistrationToken(token)
                .map(StudyHTMLTransformer::toString)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
