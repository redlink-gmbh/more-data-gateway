package io.redlink.more.data.model.garmin.transformation;

import io.redlink.more.data.util.MapperUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public record GarminTimeData<T>(Instant timestamp, Optional<Instant> endTimestamp, T data) {
    private static final String END_TIMESTAMP_KEY = "endTimestamp";

    public GarminTimeData(Instant timestamp, T data) {
        this(timestamp, Optional.empty(), data);
    }

    public Map<String, Object> dataToMap(String dataKey) {
        if (data == null) {
            return Collections.emptyMap();
        }
        HashMap<String, Object> map = new HashMap<>();
        endTimestamp.ifPresent(instant -> map.put(END_TIMESTAMP_KEY, instant));
        if (MapperUtils.isPrimitiveLike(data)) {
            map.put(dataKey, data);
        } else {
            Map<String, Object> converted =
                    MapperUtils.convertValue(data, Map.class);
            map.put(dataKey, converted);
        }
        return map;
    }
}