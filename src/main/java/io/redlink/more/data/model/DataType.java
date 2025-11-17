package io.redlink.more.data.model;

public enum DataType {
    HEARTRATE("hr"),
    ACTIVITY("activity");

    public final String dataType;

    DataType(String dataType) {
        this.dataType = dataType;
    }
}
