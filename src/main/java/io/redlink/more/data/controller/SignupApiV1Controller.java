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
import io.redlink.more.data.configuration.AuthenticationFacade;
import io.redlink.more.data.properties.MoreProperties;
import io.redlink.more.data.service.RegistrationService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Controller
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.TEXT_HTML_VALUE)
@EnableConfigurationProperties(MoreProperties.class)
public class SignupApiV1Controller implements SignupApi {
    private final MoreProperties moreProperties;
    private final RegistrationService registrationService;
    private final AuthenticationFacade authenticationFacade;

    public SignupApiV1Controller(MoreProperties moreProperties, RegistrationService registrationService, AuthenticationFacade authenticationFacade) {
        this.moreProperties = moreProperties;
        this.registrationService = registrationService;
        this.authenticationFacade = authenticationFacade;
    }

    @Override
    public ResponseEntity<String> signupInfo(String token) {
        return null;
    }
}
