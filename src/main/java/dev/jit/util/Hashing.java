package dev.jit.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class Hashing {
    private static final HexFormat HEX = HexFormat.of();   // lowercase hex, same as git

    private Hashing() {}                                   // utility class: only static methods, no instances

    /** SHA-1 of the bytes: always 20 raw bytes. */
    public static byte[] sha1(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(data);
        } catch (NoSuchAlgorithmException e) {             // every JDK has SHA-1, so this never happens in practice
            throw new IllegalStateException(e);
        }
    }

    /** 20 raw bytes -> 40 hex characters, e.g. 3b18e5... */
    public static String toHex(byte[] raw) { return HEX.formatHex(raw); }

    /** 40 hex characters -> 20 raw bytes (needed for trees in the next milestone). */
    public static byte[] fromHex(String hex) { return HEX.parseHex(hex); }

    public static boolean isFullHex(String s) {
        return s != null && s.matches("[0-9a-f]{40}");
    }
}