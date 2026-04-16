package io.redlink.more.data.controller;

import io.redlink.more.data.api.execution.v1.webservices.ExecutionApi;
import io.redlink.more.data.configuration.AuthenticationFacade;
import io.redlink.more.data.exception.BadRequestException;
import io.redlink.more.data.exception.ForbiddenException;
import io.redlink.more.data.exception.NotAuthorizedException;
import io.redlink.more.data.exception.NotFoundException;
import io.redlink.more.data.exception.RegistrationNotPossibleException;
import io.redlink.more.data.model.ActiveObservation;
import io.redlink.more.data.model.GatewayUserDetails;
import io.redlink.more.data.model.NonMissingData;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.StudyParticipantUserDetails;
import io.redlink.more.data.service.ObservationExecutionService;
import io.redlink.more.data.service.StudyService;
import io.redlink.more.data.util.DateTimeUtils;
import io.redlink.more.data.util.SessionUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping(value = "/api/v1/execution", produces = MediaType.APPLICATION_JSON_VALUE)
public class ObservationExecutionController implements ExecutionApi {
    private static final Logger LOG = LoggerFactory.getLogger(ObservationExecutionController.class);
    private final AuthenticationFacade authenticationFacade;
    private final StudyService studyService;
    private final ObservationExecutionService observationExecutionService;

    public ObservationExecutionController(AuthenticationFacade authenticationFacade, StudyService studyService, ObservationExecutionService observationExecutionService) {
        this.authenticationFacade = authenticationFacade;
        this.studyService = studyService;
        this.observationExecutionService = observationExecutionService;
    }

    @Override
    public ResponseEntity<Void> execObservation(String observationId, String scheduleStart, String scheduleEnd, String redirect) {
        try {
            Instant start = DateTimeUtils.parseInstant(scheduleStart);
            Instant end = DateTimeUtils.parseInstant(scheduleEnd);
            final RoutingInfo routingInfo = getRoutingInfo();

            ActiveObservation activeObservation = new ActiveObservation(observationId, start, end);
            if (redirect != null && !redirect.isBlank()) {
                SessionUtils.addRedirect(activeObservation, redirect);
            }

            if (!SessionUtils.getActiveObservations().contains(activeObservation)) {
                ArrayList<ActiveObservation> mutableList = new ArrayList<>(SessionUtils.getActiveObservations());
                mutableList.add(activeObservation);
                SessionUtils.setActiveObservations(mutableList);
            }

            if (SessionUtils.getNonMissingData().contains(NonMissingData.fromActiveObservation(activeObservation))) {
                Optional<String> sessionRedirect = SessionUtils.getRedirect(activeObservation);
                String target = sessionRedirect.orElseGet(() ->
                        ServletUriComponentsBuilder.fromCurrentContextPath()
                                .path("/api/v1/execution/callback/end.htm")
                                .toUriString()
                );
                URI redirectUrl = UriComponentsBuilder.fromUriString(target)
                        .replaceQueryParam("status", 200)
                        .build().toUri();
                return ResponseEntity.status(HttpStatus.FOUND).location(redirectUrl).build();
            }

            String url = observationExecutionService.executeObservation(observationId, start, end, routingInfo);
            LOG.info("Opening url `{}` for routinginfo {} and observation schedule {}", url, routingInfo, activeObservation);
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
        } catch (Exception e) {
            LOG.error("Error executing observation {}: {}", observationId, e.getMessage(), e);
            String target = (redirect != null && !redirect.isBlank()) ? redirect :
                    ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/api/v1/execution/callback/end.htm")
                    .toUriString();
            URI redirectUrl = UriComponentsBuilder.fromUriString(target)
                    .queryParam("status", mapToStatus(e))
                    .build().toUri();
            return ResponseEntity.status(HttpStatus.FOUND).location(redirectUrl).build();
        }
    }


    @Override
    public ResponseEntity<String> callback() {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((key, value) -> params.put(key, value[0]));

        Map<String, String> pathVars = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (pathVars != null) {
            params.putAll(pathVars);
        }

        UriComponentsBuilder builder = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/end.htm");
        params.forEach((k, v) -> {
            if (k.equalsIgnoreCase("savedId")) {
                builder.replaceQueryParam("savedid", v);
            } else {
                builder.replaceQueryParam(k, v);
            }
        });

        URI redirectUrl = builder.build().toUri();
        Integer status = 200;
        RoutingInfo routingInfo = null;

        try {
            try {
                routingInfo = getRoutingInfo();
            } catch (Exception e) {
                LOG.debug("No routing info from authentication: {}", e.getMessage());
            }

            List<ActiveObservation> activeObservations = SessionUtils.getActiveObservations();
            ActiveObservation last = null;
            String observationId = params.get("observationId");
            if (observationId == null) {
                observationId = params.get("observationid");
            }
            if (observationId == null) {
                observationId = params.get("observation-id");
            }
            Instant scheduleStart = null;
            Instant scheduleEnd = null;

            if (activeObservations != null && !activeObservations.isEmpty()) {
                last = activeObservations.get(activeObservations.size() - 1);
                observationId = last.observationId();
                scheduleStart = last.scheduleStart();
                scheduleEnd = last.scheduleEnd();

                Optional<String> sessionRedirect = SessionUtils.getRedirect(last);
                if (sessionRedirect.isPresent() && !sessionRedirect.get().isBlank()) {
                    redirectUrl = URI.create(sessionRedirect.get());
                }
            }

            if (observationId != null) {
                boolean processSuccess = observationExecutionService.processCallback(observationId, scheduleStart, scheduleEnd, routingInfo, params);

                if (processSuccess) {
                    if (last != null) {
                        NonMissingData nonMissingData = NonMissingData.fromActiveObservation(last);
                        if (!SessionUtils.getNonMissingData().contains(nonMissingData)) {
                            ArrayList<NonMissingData> mutableNonMissingDataList = new ArrayList<>(SessionUtils.getNonMissingData());
                            mutableNonMissingDataList.add(nonMissingData);
                            SessionUtils.setNonMissingData(mutableNonMissingDataList);
                        }
                    }
                } else {
                    status = HttpStatus.INTERNAL_SERVER_ERROR.value();
                }
                if (last != null) {
                    ArrayList<ActiveObservation> mutableActiveObservationList = new ArrayList<>(activeObservations);
                    mutableActiveObservationList.remove(last);
                    SessionUtils.setActiveObservations(mutableActiveObservationList);
                    SessionUtils.removeRedirect(last);
                }
            } else {
                LOG.error("Observation not found for observationId {}!", observationId);
                status = 404;
            }
        } catch (Exception e) {
            LOG.error("Error processing callback: {}", e.getMessage(), e);
            status = mapToStatus(e);
        }

        redirectUrl = UriComponentsBuilder.fromUri(redirectUrl)
                .replaceQueryParam("status", status)
                .build().toUri();

        LOG.info("Redirecting participant {} to url `{}`", routingInfo, redirectUrl);
        return ResponseEntity.status(HttpStatus.FOUND).location(redirectUrl).build();
    }

    @Override
    public ResponseEntity<String> callbackEndHtm() {
        String html = "<html><body><h1>Completed! Thank you!</h1></body></html>";
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }


    private RoutingInfo getRoutingInfo() {
        Authentication authentication = authenticationFacade.getAuthentication();
        if (authentication == null) {
            throw new AccessDeniedException("Authentication required");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof GatewayUserDetails userDetails) {
            return userDetails.getRoutingInfo();
        } else if (principal instanceof StudyParticipantUserDetails participantDetails) {
            var ref = participantDetails.getStudyParticipantReference();
            return studyService.getRoutingInfo(ref.studyId(), (int) ref.participantId())
                    .orElseThrow(() -> new AccessDeniedException("Routing info not found"));
        }
        throw new AccessDeniedException("Unexpected principal type");
    }

    private int mapToStatus(Exception e) {
        if (e instanceof ForbiddenException || e instanceof AccessDeniedException || e instanceof IllegalArgumentException) {
            return HttpStatus.FORBIDDEN.value();
        } else if (e instanceof NotFoundException) {
            return HttpStatus.NOT_FOUND.value();
        } else if (e instanceof NotAuthorizedException) {
            return HttpStatus.UNAUTHORIZED.value();
        } else if (e instanceof BadRequestException) {
            return HttpStatus.BAD_REQUEST.value();
        } else if (e instanceof RegistrationNotPossibleException) {
            return HttpStatus.CONFLICT.value();
        }
        return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }
}
