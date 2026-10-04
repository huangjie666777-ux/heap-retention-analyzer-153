package dev.heap;

/** Lossless hex rendering/parsing of 64-bit object IDs. */
public final class HexIds {
    private HexIds() {
    }

    public static String toHex(long id) {
        return "0x" + Long.toHexString(id);
    }

    /** Accepts "0x..." or plain hex; rejects anything else. */
    public static Long parse(String text) {
        if (text == null) {
            return null;
        }
        String s = text.trim();
        if (s.startsWith("0x") || s.startsWith("0X")) {
            s = s.substring(2);
        }
        if (s.isEmpty() || s.length() > 16) {
            return null;
        }
        try {
            return Long.parseUnsignedLong(s, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
