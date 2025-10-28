package io.redlink.more.data.controller;

import io.redlink.more.data.api.app.v1.model.DeregistrationRequestDTO;
import io.redlink.more.data.api.app.v1.model.UpdatePermissionsRequestDTO;
import io.redlink.more.data.api.app.v1.webservices.GarminUserManagementApi;
import io.redlink.more.data.properties.MoreProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Controller
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class GarminApiV1Controller implements GarminUserManagementApi {
    @Override
    public ResponseEntity<Void> deleteGarminUser(DeregistrationRequestDTO deregistrationRequestDTO) {
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> updateGarminUserPermissions(UpdatePermissionsRequestDTO updatePermissionsRequestDTO) {
        return ResponseEntity.ok().build();
    }
}
