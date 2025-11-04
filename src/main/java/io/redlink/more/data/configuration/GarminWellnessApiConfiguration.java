package io.redlink.more.data.configuration;

import io.redlink.more.data.garmin.wellness.ApiClient;
import io.redlink.more.data.garmin.wellness.client.UserApiApi;
import io.redlink.more.data.garmin.wellness.client.UserControllerApi;
import io.redlink.more.data.model.garmin.UserAccessTokenWithData;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

@Configuration
public class GarminWellnessApiConfiguration {
    public UserApiApi getUserApi(UserAccessTokenWithData userAccessTokenWithData) {
        ApiClient apiClient = new ApiClient();
        apiClient.addDefaultHeader(HttpHeaders.AUTHORIZATION, StringUtils.capitalize(userAccessTokenWithData.accessToken().tokenType()) + " " + userAccessTokenWithData.accessToken().accessToken());
        return new UserApiApi(apiClient);
    }

    public UserControllerApi getUserControllerApi(UserAccessTokenWithData userAccessTokenWithData) {
        ApiClient apiClient = new ApiClient();
        apiClient.addDefaultHeader(HttpHeaders.AUTHORIZATION, StringUtils.capitalize(userAccessTokenWithData.accessToken().tokenType()) + " " + userAccessTokenWithData.accessToken().accessToken());
        return new UserControllerApi(apiClient);
    }
}
