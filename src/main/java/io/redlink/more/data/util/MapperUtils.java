package io.redlink.more.data.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.redlink.more.data.exception.BadRequestException;

import java.util.Map;

public class MapperUtils {
    public static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    public static String writeValueAsString(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    public static <T> T readValue(Object o, Class<T> c) {
        if (o == null) return null;
        try {
            return MAPPER.readValue(o.toString(), c);
        } catch (JsonProcessingException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    public static Map<String, Object> toMap(Object o) {
        return (Map<String, Object>) MAPPER.convertValue(o, Map.class);
    }
}
