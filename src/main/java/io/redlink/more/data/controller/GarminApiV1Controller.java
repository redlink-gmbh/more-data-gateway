package io.redlink.more.data.controller;

import io.redlink.more.data.api.app.v1.model.DailySummariesRequestDTO;
import io.redlink.more.data.api.app.v1.model.DeregistrationRequestDTO;
import io.redlink.more.data.api.app.v1.model.EpochSummariesRequestDTO;
import io.redlink.more.data.api.app.v1.model.HrvDataRequestDTO;
import io.redlink.more.data.api.app.v1.model.PulseOxSummariesRequestDTO;
import io.redlink.more.data.api.app.v1.model.SleepSummariesRequestDTO;
import io.redlink.more.data.api.app.v1.model.StressSummariesRequestDTO;
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
    private static final Logger LOG = LoggerFactory.getLogger(GarminApiV1Controller.class);

    private final GarminService garminService;

    public GarminApiV1Controller(GarminService garminService) {
        this.garminService = garminService;
    }

    @Override
    public ResponseEntity<Void> deleteGarminUser(String userAgent, String garminClientId, DeregistrationRequestDTO deregistrationRequestDTO) {
        if (garminService.garminRequestIsValid(userAgent, garminClientId)) {
            deregistrationRequestDTO.getDeregistrations().forEach(deregistrationItemDTO -> {
                LOG.info("Handling Garmin user deregistration request for user: {}", deregistrationItemDTO.getUserId());
                garminService.deleteUserIdAndToken(deregistrationItemDTO.getUserId());
            });
        }
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> updateGarminUserPermissions(String userAgent, String garminClientId, UpdatePermissionsRequestDTO updatePermissionsRequestDTO) {
        if (garminService.garminRequestIsValid(userAgent, garminClientId)) {
            updatePermissionsRequestDTO.getUserPermissionsChange()
                    .forEach(userPermissionChangeDTO -> {
                        LOG.info("Handling Garmin user permissions update request for user: {}", userPermissionChangeDTO.getUserId());
                        garminService.updateUserPermissions(userPermissionChangeDTO.getUserId(), userPermissionChangeDTO.getPermissions());
                    });
        }
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> submitDailySummaries(String userAgent, String garminClientId, DailySummariesRequestDTO dailySummariesRequestDTO) {
        if (garminService.garminRequestIsValid(userAgent, garminClientId)) {
            LOG.info("Daily summary received: user agent: {}; client id: {}; dailySummariesRequestDTO: {}", userAgent, garminClientId, dailySummariesRequestDTO);
        } else {
            LOG.warn("Invalid Garmin request: user agent: {}; client id: {}; dailySummariesRequestDTO: {}", userAgent, garminClientId, dailySummariesRequestDTO);
        }
        return ResponseEntity.ok().build();
    }


    @Override
    public ResponseEntity<Void> submitEpochSummaries(String userAgent, String garminClientId, EpochSummariesRequestDTO epochSummariesRequestDTO) {
        if (garminService.garminRequestIsValid(userAgent, garminClientId)) {
            LOG.info("Epoch summary received: user agent: {}; client id: {}; epochSummariesRequestDTO: {}", userAgent, garminClientId, epochSummariesRequestDTO);
        } else {
            LOG.warn("Invalid Garmin request: user agent: {}; client id: {}; epochSummariesRequestDTO: {}", userAgent, garminClientId, epochSummariesRequestDTO);
        }
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> submitHrvData(String userAgent, String garminClientId, HrvDataRequestDTO hrvDataRequestDTO) {
        if (garminService.garminRequestIsValid(userAgent, garminClientId)) {
            LOG.info("HRV data received: user agent: {}; client id: {}; hrvDataRequestDTO: {}", userAgent, garminClientId, hrvDataRequestDTO);
        } else {
            LOG.warn("Invalid Garmin request: user agent: {}; client id: {}; hrvDataRequestDTO: {}", userAgent, garminClientId, hrvDataRequestDTO);
        }
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> submitPulseOxSummaries(String userAgent, String garminClientId, PulseOxSummariesRequestDTO pulseOxSummariesRequestDTO) {
        if (garminService.garminRequestIsValid(userAgent, garminClientId)) {
            LOG.info("Pulse Ox summary received: user agent: {}; client id: {}; pulseOxSummariesRequestDTO: {}", userAgent, garminClientId, pulseOxSummariesRequestDTO);
        } else {
            LOG.warn("Invalid Garmin request: user agent: {}; client id: {}; pulseOxSummariesRequestDTO: {}", userAgent, garminClientId, pulseOxSummariesRequestDTO);
        }
        return ResponseEntity.ok().build();
    }


    @Override
    public ResponseEntity<Void> submitSleepSummaries(String userAgent, String garminClientId, SleepSummariesRequestDTO sleepSummariesRequestDTO) {
        if (garminService.garminRequestIsValid(userAgent, garminClientId)) {
            LOG.info("Sleep summary received: user agent: {}; client id: {}; sleepSummariesRequestDTO: {}", userAgent, garminClientId, sleepSummariesRequestDTO);
        } else {
            LOG.warn("Invalid Garmin request: user agent: {}; client id: {}; sleepSummariesRequestDTO: {}", userAgent, garminClientId, sleepSummariesRequestDTO);
        }
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> submitStressSummaries(String userAgent, String garminClientId, StressSummariesRequestDTO stressSummariesRequestDTO) {
        if (garminService.garminRequestIsValid(userAgent, garminClientId)) {
            LOG.info("Stress summary received: user agent: {}; client id: {}; stressSummariesRequestDTO: {}", userAgent, garminClientId, stressSummariesRequestDTO);
        } else {
            LOG.warn("Invalid Garmin request: user agent: {}; client id: {}; stressSummariesRequestDTO: {}", userAgent, garminClientId, stressSummariesRequestDTO);
        }
        return ResponseEntity.ok().build();
    }
}
