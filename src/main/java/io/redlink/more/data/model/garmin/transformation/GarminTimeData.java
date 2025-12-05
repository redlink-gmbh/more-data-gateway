package io.redlink.more.data.model.garmin.transformation;

import io.redlink.more.data.util.MapperUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static io.redlink.more.data.util.ElasticUtils.Constants.GARMIN_SUMMARY_ID_KEY;

public record GarminTimeData<T>(Instant timestamp, T data, Map<String, Object> additionalData) {

    public GarminTimeData(Instant timestamp, T data) {
        this(timestamp, data, Collections.emptyMap());
    }

    public Map<String, Object> dataToMap(String dataKey, String summaryId) {
        HashMap<String, Object> map = new HashMap<>(additionalData);
        if (summaryId != null && !summaryId.isEmpty()) {
            map.put(GARMIN_SUMMARY_ID_KEY, summaryId);
        }
        if (data == null) {
            return map;
        }
        if (MapperUtils.isPrimitiveLike(data)) {
            map.put(dataKey, data);
        } else {
            map.putAll(MapperUtils.convertValue(data, Map.class));
        }
        return map.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}