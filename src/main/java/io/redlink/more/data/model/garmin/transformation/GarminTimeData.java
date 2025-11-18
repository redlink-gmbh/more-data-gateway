package io.redlink.more.data.model.garmin.transformation;

import io.redlink.more.data.util.MapperUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public record GarminTimeData<T>(Instant timestamp, T data, Map<String, Object> additionalData) {
    public GarminTimeData(Instant timestamp, T data) {
        this(timestamp, data, Collections.emptyMap());
    }

    public Map<String, Object> dataToMap(String dataKey) {
        if (data == null) {
            return additionalData;
        }
        HashMap<String, Object> map = new HashMap<>(additionalData);
        if (MapperUtils.isPrimitiveLike(data)) {
            map.put(dataKey, data);
        } else {
            map.putAll(MapperUtils.convertValue(data, Map.class));
        }
        return map;
    }
}