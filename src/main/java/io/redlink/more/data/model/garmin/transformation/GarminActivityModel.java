package io.redlink.more.data.model.garmin.transformation;

import io.redlink.more.data.custom.model.GarminDataPoint;

public record GarminActivityModel(
        GarminDataPoint.ActivityTypeEnum activityType,
        Double met,
        GarminDataPoint.IntensityEnum intensity
) {
}
