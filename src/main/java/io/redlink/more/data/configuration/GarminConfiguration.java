package io.redlink.more.data.configuration;

import io.redlink.more.data.properties.GarminProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Configuration
@EnableConfigurationProperties({GarminProperties.class})
public class GarminConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(GarminConfiguration.class);
    private final GarminProperties garminProperties;
    private final TaskScheduler scheduler;

    GarminConfiguration(
            GarminProperties garminProperties,
            TaskScheduler scheduler
    ) {
        this.garminProperties = garminProperties;
        this.scheduler = scheduler;
    }

    @PostConstruct
    protected void initTokenRefresh(){
        LOG.info("Initializing Garmin token refresh task (cron: {})", garminProperties.tokenRefresh());
        scheduler.schedule(this::todoImplementTokenRefresh, garminProperties.tokenRefresh());
    }

    private void todoImplementTokenRefresh() {
        LOG.info("Refresh Garmin Tokens for {}", Instant.now().truncatedTo(ChronoUnit.MINUTES).toString());
        //iterate over all garmin users
        //  check the remaining validity of the token
        //  if to short
        //    refresh the token and store the new token in the database
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
}
