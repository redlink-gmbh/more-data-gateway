package io.redlink.more.data.model.garmin;

public enum GarminSummaryType {
    DAILIES,
    EPOCHS,
    STRESS,
    PULSEOX,
    SLEEP,
    HRV;

    public final String label;

    GarminSummaryType() {
        label = name().toLowerCase();
    }

    public static GarminSummaryType fromLabel(String label) {
        return GarminSummaryType.valueOf(label.toUpperCase());
    }
}
