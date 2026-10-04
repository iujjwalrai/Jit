package dev.jit.objects;

import java.time.ZoneOffset;

/**
 * Who did something and when, as git writes it in a commit:
 *   "Ujjwal Rai <ujjwal@example.com> 1700000000 +0530"
 * The time is seconds since 1970 (UTC) plus the author's own timezone offset, kept so
 * log can show the time as it was on their clock.
 */
public record Signature(String name, String email, long epochSeconds, ZoneOffset offset) {

    public Signature {
        if (name.matches(".*[<>\n].*") || email.matches(".*[<>\n].*"))     // these would break the line format
            throw new IllegalArgumentException("name/email can't contain '<', '>' or newlines");
    }

    public String format() {
        return name + " <" + email + "> " + epochSeconds + " " + formatOffset(offset);
    }

    public static Signature parse(String s) {
        int lt = s.lastIndexOf('<'), gt = s.lastIndexOf('>');               // "Name |<|email|>| 1700000000 +0530"
        if (lt < 0 || gt < lt) throw new IllegalStateException("bad signature: " + s);
        String[] when = s.substring(gt + 1).trim().split(" ");
        if (when.length != 2) throw new IllegalStateException("bad signature: " + s);
        return new Signature(s.substring(0, lt).trim(), s.substring(lt + 1, gt),
                Long.parseLong(when[0]), parseOffset(when[1]));
    }

    /** +05:30 -> "+0530". Git's format has no colon. */
    public static String formatOffset(ZoneOffset offset) {
        int secs = offset.getTotalSeconds();
        int abs = Math.abs(secs);
        return String.format("%s%02d%02d", secs < 0 ? "-" : "+", abs / 3600, abs % 3600 / 60);
    }

    /** "+0530" -> +05:30 */
    public static ZoneOffset parseOffset(String s) {
        if (!s.matches("[+-]\\d{4}")) throw new IllegalStateException("bad timezone: " + s);
        int sign = s.charAt(0) == '-' ? -1 : 1;
        return ZoneOffset.ofHoursMinutes(sign * Integer.parseInt(s.substring(1, 3)),
                                         sign * Integer.parseInt(s.substring(3, 5)));
    }
}
