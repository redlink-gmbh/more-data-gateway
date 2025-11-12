package io.redlink.more.data.model.garmin;

import io.redlink.more.data.api.app.v1.model.GarminDataPointDTO;
import io.redlink.more.data.model.ParticipantKeyValue;

import java.util.List;

public record ParticipantGarminDataPoint(
        ParticipantKeyValue participantKeyValue,
        List<GarminDataPointDTO> garminDataPoints
) {
}
