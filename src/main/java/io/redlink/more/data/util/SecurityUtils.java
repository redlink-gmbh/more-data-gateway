package io.redlink.more.data.util;

import io.redlink.more.data.exception.NotAuthorizedException;
import io.redlink.more.data.model.RoutingInfo;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtils {
    public static RoutingInfo routingInfoFromSecurityContext() throws NotAuthorizedException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication.getPrincipal() instanceof RoutingInfoUserDetails userDetails) {
            return userDetails.getRoutingInfo();
        } else {
            throw new NotAuthorizedException("Authentication invalid!");
        }
    }
}
