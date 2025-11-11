package io.redlink.more.data.util;

public class StringUtils {
    public static String shorten(String s) {
        return shorten(s, 4);
    }

    public static String shorten(String s, int maxLength) {
        if (s.length() > maxLength) {
            return "..." + org.apache.commons.lang3.StringUtils.right(s, maxLength);
        } else {
            return s;
        }
    }
}
