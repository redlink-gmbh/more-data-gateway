package io.redlink.more.data.model;

public record DataHealth(
        boolean valid,
        ObservationDataState state
) {
    public static DataHealth MISSING = new DataHealth(false, ObservationDataState.MISSING);

    @Override
    public String toString() {
        return "DataHealth{" +
                "valid=" + valid +
                ", state=" + state +
                '}';
    }
}
