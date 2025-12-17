package io.redlink.more.data.util;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class DateTimeUtils {
    public static OffsetDateTime offsetDateTimeFromEpochSeconds(long epochSecond, int offset) {
        Instant unixTimestamp = Instant.ofEpochSecond(epochSecond);
        ZoneOffset zoneOffset = ZoneOffset.ofTotalSeconds(offset);
        return unixTimestamp.atOffset(zoneOffset);
    }
}
