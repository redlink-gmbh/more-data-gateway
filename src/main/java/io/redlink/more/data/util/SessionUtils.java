package io.redlink.more.data.util;

import io.redlink.more.data.model.ActiveObservation;
import io.redlink.more.data.model.NonMissingData;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SessionUtils {
    private static final String nonMissingKey = "nonMissing";
    private static final String activeObservationsKey = "activeObservations";
    private static final String redirectKey = "redirectMap";

    public static Map<ActiveObservation, String> getRedirectMap() {
        return SessionUtils.<Map<ActiveObservation, String>>getAttribute(redirectKey)
                .orElseGet(Collections::emptyMap);
    }

    public static void setRedirectMap(Map<ActiveObservation, String> redirectMap) {
        setAttribute(redirectKey, redirectMap);
    }

    public static void addRedirect(ActiveObservation activeObservation, String redirect) {
        Map<ActiveObservation, String> redirectMap = new HashMap<>(getRedirectMap());
        redirectMap.put(activeObservation, redirect);
        setRedirectMap(redirectMap);
    }

    public static Optional<String> getRedirect(ActiveObservation activeObservation) {
        return Optional.ofNullable(getRedirectMap().get(activeObservation));
    }

    public static void removeRedirect(ActiveObservation activeObservation) {
        Map<ActiveObservation, String> redirectMap = new HashMap<>(getRedirectMap());
        redirectMap.remove(activeObservation);
        setRedirectMap(redirectMap);
    }

    public static List<NonMissingData> getNonMissingData() {
        return SessionUtils.<List<NonMissingData>>getAttribute(nonMissingKey)
                .orElseGet(Collections::emptyList);
    }

    public static void setNonMissingData(List<NonMissingData> nonMissingData) {
        setAttribute(nonMissingKey, nonMissingData);
    }

    public static List<ActiveObservation> getActiveObservations() {
        return SessionUtils.<List<ActiveObservation>>getAttribute(activeObservationsKey)
                .orElseGet(Collections::emptyList);
    }

    public static void setActiveObservations(List<ActiveObservation> activeObservations) {
        setAttribute(activeObservationsKey, activeObservations);
    }

    public static void setAttribute(String key, Object data) {
        getSession().ifPresent(httpSession -> httpSession.setAttribute(key, data));
    }

    public static <T> Optional<T> getAttribute(String key) {
        return getSession().flatMap(httpSession -> Optional.ofNullable((T) httpSession.getAttribute(key)));
    }

    public static Optional<HttpSession> getSession() {
        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attr != null) {
            return Optional.ofNullable(attr.getRequest().getSession(false));
        }
        return Optional.empty();
    }
}
