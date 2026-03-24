package io.redlink.more.data.controller.participantPortal;

import io.redlink.more.data.api.participant.v1.model.StudyConsentDTO;
import io.redlink.more.data.api.participant.v1.model.StudyDTO;
import io.redlink.more.data.api.participant.v1.webservices.AuthorizationApi;
import io.redlink.more.data.controller.transformer.ParticipantPortalTransformer;
import io.redlink.more.data.exception.NotAuthorizedException;
import io.redlink.more.data.model.ParticipantConsent;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.service.ApplicationAccessService;
import io.redlink.more.data.service.StudyService;
import io.redlink.more.data.util.ParticipantUtils;
import io.redlink.more.data.util.RoutingInfoUserDetails;
import io.redlink.more.data.util.SecurityUtils;
import io.redlink.more.data.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Controller
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class ParticipantPortalAuthController implements AuthorizationApi {
    private static final Logger LOG = LoggerFactory.getLogger(ParticipantPortalAuthController.class);
    private final ApplicationAccessService applicationAccessService;
    private final StudyService studyService;

    ParticipantPortalAuthController(ApplicationAccessService applicationAccessService, StudyService studyService) {
        this.applicationAccessService = applicationAccessService;
        this.studyService = studyService;
    }

    @Override
    public ResponseEntity<Void> participantLogin(Long moreStudyId, String moreUserDataReference, String moreLoginCode) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        final HttpServletRequest request;
        if (requestAttributes instanceof ServletRequestAttributes) {
            request = ((ServletRequestAttributes) requestAttributes).getRequest();
        } else {
            throw new IllegalStateException("Request is not a ServletRequest");
        }

        final String decodedToken;
        try {
            decodedToken = StringUtils.base64Decode(moreLoginCode);
        } catch (RuntimeException e) {
            LOG.warn("Could not decode more login code", e);
            throw new NotAuthorizedException("Login Code is not valid");
        }

        RoutingInfo routingInfo = applicationAccessService.validateLogin(moreStudyId, moreUserDataReference, decodedToken)
                .orElseThrow(NotAuthorizedException::new);
        RoutingInfoUserDetails userDetails = new RoutingInfoUserDetails(
                routingInfo,
                null);
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        HttpSession session = request.getSession(true);
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                context
        );
        // change session id to prevent session fixation
        request.changeSessionId();
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> acceptConsent(StudyConsentDTO studyConsentDTO) {
        RoutingInfo routingInfo = SecurityUtils.routingInfoFromSecurityContext();
        if (applicationAccessService.hasConsent(routingInfo)) {
            return ResponseEntity.status(HttpStatus.ALREADY_REPORTED).build();
        }
        final ParticipantConsent consent = ParticipantUtils.convert(studyConsentDTO);
        applicationAccessService.validateAndStoreConsent(routingInfo, consent);
        return ResponseEntity.noContent().build();
    }


    @Override
    public ResponseEntity<StudyDTO> retrieveConsentData() {
        RoutingInfo routingInfo = studyService
                .getCompleteRoutingInfo(SecurityUtils.routingInfoFromSecurityContext())
                .orElseThrow(NotAuthorizedException::new);
        if (applicationAccessService.hasConsent(routingInfo)) {
            return ResponseEntity.status(HttpStatus.ALREADY_REPORTED).build();
        }
        var studyData = studyService.getStudy(routingInfo);
        return studyData
                .map(studyListPair ->
                        ResponseEntity.ok(ParticipantPortalTransformer.toDTO(studyListPair.getLeft(), studyListPair.getRight())))
                .orElseGet(() ->
                        ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Void> handleIllegalState(IllegalStateException ex) {
        LOG.error("Illegal state: {}", ex.getMessage());
        return ResponseEntity.internalServerError().build();
    }

}
