package io.redlink.more.data.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.redlink.more.data.exception.BadRequestException;

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

    public static <T> T convertValue(Object o, Class<T> c) {
        return MAPPER.convertValue(o, c);
    }

    public static boolean isPrimitiveLike(Object value) {
        return value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum<?>;
    }
}
