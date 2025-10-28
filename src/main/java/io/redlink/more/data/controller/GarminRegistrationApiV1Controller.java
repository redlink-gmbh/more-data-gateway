package io.redlink.more.data.controller;

import io.redlink.more.data.api.app.v1.model.GarminRedirectDTO;
import io.redlink.more.data.api.app.v1.webservices.GarminRegistrationApi;
import io.redlink.more.data.configuration.AuthenticationFacade;
import io.redlink.more.data.properties.MoreProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Controller
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class GarminRegistrationApiV1Controller implements GarminRegistrationApi {
    @Override
    public ResponseEntity<Void> getGarminOauthUrl() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "redirect-url")
                .build();
    }

    @Override
    public ResponseEntity<Void> handleGarminCallback(String code, String state) {
        return null;
    }
}
