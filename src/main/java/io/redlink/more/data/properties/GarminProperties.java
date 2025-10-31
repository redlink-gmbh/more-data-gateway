package io.redlink.more.data.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "garmin")
public record GarminProperties(
        OAuth oauth
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
