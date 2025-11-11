package io.redlink.more.data.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronTrigger;

@ConfigurationProperties(prefix = "garmin")
public record GarminProperties(
        OAuth oauth,
        CronTrigger tokenRefresh
) {
    public record OAuth(
            String garminOauthUrl,
            String garminAuthorizeUrl,
            String clientId,
            String clientSecret,
            String garminCallbackRedirectUri,
            String redirectUrl
    ) {
    }
}
