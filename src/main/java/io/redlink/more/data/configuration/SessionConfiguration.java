/*
 * Copyright (c) 2022 Redlink GmbH.
 */
package io.redlink.more.data.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.util.StringUtils;

@Configuration
public class SessionConfiguration {

    @Value("${server.servlet.session.cookie.domain:}")
    private String cookieDomain;

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setSameSite("None");
        serializer.setUseSecureCookie(true);
        serializer.setUseHttpOnlyCookie(true);
        if (StringUtils.hasText(cookieDomain)) {
            serializer.setDomainName(cookieDomain);
        }
        return serializer;
    }

    @Bean
    public RedisSerializer<Object> springSessionRedisSerializer() {
        return new GenericJackson2JsonRedisSerializer(sessionObjectMapper());
    }

    private ObjectMapper sessionObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModules(SecurityJackson2Modules.getModules(getClass().getClassLoader()));
        mapper.registerModule(new Jdk8Module());
        return mapper;
    }

}
