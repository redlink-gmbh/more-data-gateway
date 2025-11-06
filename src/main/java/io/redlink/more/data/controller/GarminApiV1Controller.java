package io.redlink.more.data.controller;

import io.redlink.more.data.api.app.v1.model.DeregistrationRequestDTO;
import io.redlink.more.data.api.app.v1.model.UpdatePermissionsRequestDTO;
import io.redlink.more.data.api.app.v1.webservices.GarminUserManagementApi;
import io.redlink.more.data.service.GarminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Controller
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class GarminApiV1Controller implements GarminUserManagementApi {
    private static final Logger LOG = LoggerFactory.getLogger(GarminRegistrationApiV1Controller.class);

    private final GarminService garminService;

    public GarminApiV1Controller(GarminService garminService) {
        this.garminService = garminService;
    }

    @Override
    public ResponseEntity<Void> deleteGarminUser(DeregistrationRequestDTO deregistrationRequestDTO) {
        deregistrationRequestDTO.getDeregistrations().forEach(deregistrationItemDTO -> {
            LOG.info("Handling Garmin user deregistration request for user: {}", deregistrationItemDTO.getUserId());
            garminService.deleteUserIdAndToken(deregistrationItemDTO.getUserId());
        });
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> updateGarminUserPermissions(UpdatePermissionsRequestDTO updatePermissionsRequestDTO) {
        updatePermissionsRequestDTO.getUserPermissionsChange()
                .forEach(userPermissionChangeDTO -> {
                    LOG.info("Handling Garmin user permissions update request for user: {}", userPermissionChangeDTO.getUserId());
                    garminService.updateUserPermissions(userPermissionChangeDTO.getUserId(), userPermissionChangeDTO.getPermissions());
                });
        return ResponseEntity.ok().build();
    }
}
