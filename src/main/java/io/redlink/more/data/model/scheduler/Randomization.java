package io.redlink.more.data.model.scheduler;

public record Randomization(boolean state, int duration) {
    public static Randomization none = new Randomization(false, 0);
}
