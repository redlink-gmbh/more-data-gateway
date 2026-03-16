package io.redlink.more.data.model.polar;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PolarDataTypeTest {

    @ParameterizedTest(name = "fromLabel(\"{0}\") == {1}")
    @CsvSource({
            "hr,          HR",
            "HR,          HR",
            "Hr,          HR",
            "ppi,         PPI",
            "PPI,         PPI",
            "temperature, TEMPERATURE",
            "TEMPERATURE, TEMPERATURE",
            "acc,         ACC",
            "ACC,         ACC",
    })
    //Should be all redundant tests data should arrive properly
    @DisplayName("fromLabel: resolves known labels case-insensitively")
    void fromLabel_resolvesKnownLabels(String input, PolarDataType expected) {
        assertThat(PolarDataType.fromLabel(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("fromLabel: returns null for unknown label")
    void fromLabel_returnsNullForUnknown() {
        assertThat(PolarDataType.fromLabel("unknown")).isNull();
        assertThat(PolarDataType.fromLabel("")).isNull();
        assertThat(PolarDataType.fromLabel("heartrate")).isNull();
    }

    @Test
    @DisplayName("label field matches lowercase name for each constant")
    void labelField_matchesLowercaseName() {
        for (PolarDataType type : PolarDataType.values()) {
            assertThat(type.label).isEqualTo(type.name().toLowerCase());
        }
    }
}
