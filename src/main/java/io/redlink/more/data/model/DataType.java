package io.redlink.more.data.model;

public enum DataType {
    HEARTRATE("hr"),
    ACTIVITY_START("activity_start"),
    ACTIVITY_END("activity_end"),
    SLEEP_START("sleep_start"),
    SLEEP_END("sleep_end");

    public final String dataType;

    DataType(String dataType) {
        this.dataType = dataType;
    }
}
