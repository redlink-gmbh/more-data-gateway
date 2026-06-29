package io.redlink.more.data.controller;

import io.redlink.more.data.api.app.v1.model.GetApplicationUrl200ResponseDTO;
import io.redlink.more.data.api.app.v1.webservices.AppApplicationsApi;
import io.redlink.more.data.service.ApplicationAccessService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Controller
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class ApplicationApiV1Controller implements AppApplicationsApi {
    private final ApplicationAccessService applicationAccessService;

    public ApplicationApiV1Controller(ApplicationAccessService applicationAccessService) {
        this.applicationAccessService = applicationAccessService;
    }


    @Override
    public ResponseEntity<GetApplicationUrl200ResponseDTO> getApplicationUrl(String applicationName) {
        var url = applicationAccessService.getApplicationUrl(applicationName);
        return url.map(s -> ResponseEntity.ok(new GetApplicationUrl200ResponseDTO().url(s)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
