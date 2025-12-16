package io.redlink.more.data.model.garmin.transformation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GarminStepData(
        Integer steps,
        Integer stepsGoal,
        Double distanceInMeters
) {
}
