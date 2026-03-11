package io.redlink.more.data.util;

import org.apache.commons.lang3.Range;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DateTimeUtilsTest {

    @Test
    void testToInstantOrNull() {
        assertNull(DateTimeUtils.toInstantOrNull(null));

        Instant now = Instant.now();
        assertEquals(now, DateTimeUtils.toInstantOrNull(now));

        assertEquals(Instant.parse("2023-01-01T10:00:00Z"), DateTimeUtils.toInstantOrNull("2023-01-01T10:00:00Z"));
        assertEquals(Instant.parse("2023-01-01T10:00:00Z"), DateTimeUtils.toInstantOrNull("  2023-01-01T10:00:00Z  "));
        assertNull(DateTimeUtils.toInstantOrNull(""));
        assertNull(DateTimeUtils.toInstantOrNull("   "));

        // Epoch millis
        long millis = 1672567200000L; // 2023-01-01T10:00:00Z
        assertEquals(Instant.ofEpochMilli(millis), DateTimeUtils.toInstantOrNull(millis));

        // Epoch seconds
        long seconds = 1672567200L;
        assertEquals(Instant.ofEpochSecond(seconds), DateTimeUtils.toInstantOrNull(seconds));

        assertThrows(IllegalArgumentException.class, () -> DateTimeUtils.toInstantOrNull(new Object()));
    }

    @Test
    void testMergeRanges() {
        assertNull(DateTimeUtils.mergeRanges(null));
        assertTrue(DateTimeUtils.mergeRanges(Set.of()).isEmpty());

        Instant i1 = Instant.parse("2023-01-01T10:00:00Z");
        Instant i2 = Instant.parse("2023-01-01T11:00:00Z");
        Instant i3 = Instant.parse("2023-01-01T12:00:00Z");
        Instant i4 = Instant.parse("2023-01-01T13:00:00Z");

        Range<Instant> r1 = Range.of(i1, i2);
        Range<Instant> r2 = Range.of(i2, i3); // Overlaps/Touches at i2
        Range<Instant> r3 = Range.of(i4, i4.plusSeconds(3600)); // Gap between r2 and r3

        Set<Range<Instant>> merged = DateTimeUtils.mergeRanges(Set.of(r1, r2, r3));
        assertEquals(2, merged.size());
        assertTrue(merged.contains(Range.of(i1, i3)));
        assertTrue(merged.contains(Range.of(i4, i4.plusSeconds(3600))));

        // Fully contained
        Range<Instant> r4 = Range.of(i1, i4);
        Range<Instant> r5 = Range.of(i2, i3);
        Set<Range<Instant>> merged2 = DateTimeUtils.mergeRanges(Set.of(r4, r5));
        assertEquals(1, merged2.size());
        assertTrue(merged2.contains(r4));
    }
}
