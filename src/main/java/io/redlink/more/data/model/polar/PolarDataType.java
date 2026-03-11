package io.redlink.more.data.model.polar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum PolarDataType {
    HR,
    PPI,
    TEMPERATURE,
    ACC;

    public final String label;

    private static final Logger LOG = LoggerFactory.getLogger(PolarDataType.class);

    PolarDataType() {
        label = name().toLowerCase();
    }

    public static PolarDataType fromLabel(String label) {
        try {
            return PolarDataType.valueOf(label.toUpperCase());
        } catch (IllegalArgumentException e) {
            LOG.warn("Invalid PolarDataType: {}", label);
            return null;
        }
    }
}
