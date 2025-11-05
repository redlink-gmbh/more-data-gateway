package io.redlink.more.data.model;

import java.util.Map;

public record ParticipantKeyValue(
        Long studyId,
        Integer participantId,
        String key,
        Map<String, Object> value
) {
}
