package io.redlink.more.data.controller;

import io.redlink.more.data.api.participant.v1.model.AcceptConsentRequestDTO;
import io.redlink.more.data.api.participant.v1.model.ObservationConsentDTO;
import io.redlink.more.data.api.participant.v1.model.ParticipantLogin200ResponseDTO;
import io.redlink.more.data.api.participant.v1.model.RetrieveConsentData200ResponseDTO;
import io.redlink.more.data.api.participant.v1.model.RetrieveConsentData200ResponseObservationsInnerDTO;
import io.redlink.more.data.api.participant.v1.webservices.AuthorizationApi;
import io.redlink.more.data.exception.ForbiddenException;
import io.redlink.more.data.exception.NotAutherizedException;
import io.redlink.more.data.model.ParticipantConsent;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.service.LoginTokenService;
import io.redlink.more.data.service.RegistrationService;
import io.redlink.more.data.util.RoutingInfoUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

@Controller
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.TEXT_HTML_VALUE)
public class ParticipantPortalAuthController implements AuthorizationApi {

    private final LoginTokenService loginTokenService;
    private final RegistrationService registrationService;

    ParticipantPortalAuthController(LoginTokenService loginTokenService, RegistrationService registrationService) {
        this.loginTokenService = loginTokenService;
        this.registrationService = registrationService;
    }

    @Override
    public ResponseEntity<ParticipantLogin200ResponseDTO> participantLogin(Long moreStudyId, String moreUserDataReference, String moreLoginCode) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        final HttpServletRequest request;
        if (requestAttributes instanceof ServletRequestAttributes) {
            request = ((ServletRequestAttributes)requestAttributes).getRequest();
        } else {
            throw new IllegalStateException("Request is not a ServletRequest");
        }

        var routingInfo = loginTokenService.validateToken(moreLoginCode, moreStudyId, "participant-portal")
                .orElseThrow(NotAutherizedException::new);
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
    public ResponseEntity<RetrieveConsentData200ResponseObservationsInnerDTO> acceptConsent(AcceptConsentRequestDTO acceptConsentRequestDTO) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        RoutingInfo routingInfo;
        if(authentication.getDetails() instanceof RoutingInfo) {
            routingInfo = (RoutingInfo) authentication.getDetails();
        } else {
            throw new IllegalStateException("Unexpected Authentication Object");
        }
        final ParticipantConsent consent = convert(acceptConsentRequestDTO);
        return null;
    }


    @Override
    public ResponseEntity<RetrieveConsentData200ResponseDTO> retrieveConsentData() {
        return null;
    }


    private static ParticipantConsent convert(AcceptConsentRequestDTO dto) {
        return new ParticipantConsent(
                dto.getConsent(),
                obfuscate(dto.getDeviceId()),
                dto.getConsentInfoMD5(),
                null,
                convert(dto.getObservations())
        );
    }

    private static List<ParticipantConsent.ObservationConsent> convert(List<ObservationConsentDTO> observations) {
        return observations.stream()
                .map(ParticipantPortalAuthController::convert)
                .toList();
    }

    private static ParticipantConsent.ObservationConsent convert(ObservationConsentDTO observations) {
        return new ParticipantConsent.ObservationConsent(
                Integer.parseInt(observations.getObservationId()),
                null
        );
    }

    /**
     * Device-ID has the format "[MODEL]#[SERIAL], we obfuscate to [MODEL]#[SERIAL:0:6]...[SERIAL:-6:-0]
     */
    private static String obfuscate(String string) {
        final String unknown = "unknown";
        if (string == null) return unknown;

        final int keepChars = 6;
        final int delim = string.indexOf('#');
        return "%s#%s...%s".formatted(
                StringUtils.defaultIfEmpty(StringUtils.left(string, delim), unknown),
                StringUtils.mid(string, delim + 1, keepChars),
                StringUtils.right(string, keepChars)
        );
    }

}
