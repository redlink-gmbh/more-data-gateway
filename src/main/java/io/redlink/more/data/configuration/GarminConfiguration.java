package io.redlink.more.data.configuration;

import io.redlink.more.data.properties.GarminProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.Base64;

@Configuration
@EnableConfigurationProperties({GarminProperties.class})
public class GarminConfiguration {
    private final GarminProperties garminProperties;

    GarminConfiguration(GarminProperties garminProperties) {
        this.garminProperties = garminProperties;
    }

    public URI basicOAuthUri() {
        if (garminProperties.oauth() == null || garminProperties.oauth().garminOauthUrl() == null || garminProperties.oauth().garminOauthUrl().isEmpty()) {
            throw new IllegalStateException("Missing Garmin OAuth URL");
        }
        if (garminProperties.oauth().clientId() == null || garminProperties.oauth().clientId().isEmpty()) {
            throw new IllegalStateException("Missing Garmin Client ID");
        }
        String baseUri = garminProperties.oauth().garminOauthUrl()
                + "?client_id=" + garminProperties.oauth().clientId()
                + "&response_type=code";
        if (garminProperties.oauth().garminCallbackRedirectUri() != null && !garminProperties.oauth().garminCallbackRedirectUri().isEmpty()) {
            baseUri += "&redirect_uri=" + garminProperties.oauth().garminCallbackRedirectUri();
        }
        return URI.create(baseUri);
    }

    public URI garminTokenUri() {
        if (garminProperties.oauth() == null || garminProperties.oauth().garminAuthorizeUrl() == null || garminProperties.oauth().garminAuthorizeUrl().isEmpty()) {
            throw new IllegalStateException("Missing Garmin Token URL");
        }
        return URI.create(garminProperties.oauth().garminAuthorizeUrl());
    }

    public String getRedirectUri() {
        return garminProperties.oauth().redirectUrl();
    }

    public String authorizationHeader() {
        return "Basic " + Base64.getEncoder().encodeToString((garminProperties.oauth().clientId() + ":" + garminProperties.oauth().clientSecret()).getBytes());
    }

    public Boolean clientIdsMatch(String cliendId) {
        return garminProperties.oauth().clientId().equalsIgnoreCase(cliendId);
    }
}
