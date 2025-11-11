package io.redlink.more.data.util;

public class StringUtils {
    public static String anonymize(String s) {
        return anonymize(s, 4);
    }

    public static String anonymize(String s, int maxVisible) {
        int visibleLength = Math.min(maxVisible, s.length() - s.length() / 2);
        String anonym;
        if (s.isEmpty()) {
            anonym = s;
        } else if (visibleLength <= 0) {
            anonym = new String(new char[s.length()]).replace('\0', '*');
        } else {
            anonym = new String(new char[s.length() - visibleLength]).replace('\0', '*') +
                    s.substring(s.length() - visibleLength);
        }
        return anonym;
    }
}
