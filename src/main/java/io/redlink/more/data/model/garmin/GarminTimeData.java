package io.redlink.more.data.model.garmin;

import java.time.Instant;
import java.util.Map;

public record GarminTimeData<T>(Instant timestamp, T data) {
    public Map<String, Object> dataToMap(String dataKey) {
        return Map.ofEntries(Map.entry(dataKey, data));
    }
}