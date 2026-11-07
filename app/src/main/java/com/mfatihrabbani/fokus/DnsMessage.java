package com.mfatihrabbani.fokus;

import java.util.Arrays;

/** Minimal DNS parsing: read the asked name and build a "not found" answer. */
final class DnsMessage {
    private DnsMessage() {}

    /** Returns the first question's name, or null when the message is malformed. */
    static String questionName(byte[] dns) {
        int end = questionEnd(dns);
        if (end < 0) return null;
        StringBuilder name = new StringBuilder();
        int i = 12;
        while (dns[i] != 0) {
            int len = dns[i] & 0xff;
            if (name.length() > 0) name.append('.');
            name.append(new String(dns, i + 1, len, java.nio.charset.StandardCharsets.US_ASCII));
            i += len + 1;
        }
        return name.toString();
    }

    /** Builds an NXDOMAIN response containing only the original question. */
    static byte[] notFound(byte[] query) {
        int end = questionEnd(query);
        if (end < 0) end = 12;
        byte[] r = Arrays.copyOf(query, end);
        r[2] = (byte) (0x80 | (query[2] & 0x79)); // QR=1, keep opcode and RD
        r[3] = (byte) 0x83;                       // RA=1, RCODE=3 (NXDOMAIN)
        r[4] = 0;
        r[5] = (byte) (end > 12 ? 1 : 0);         // one question
        for (int i = 6; i < 12; i++) r[i] = 0;    // no answers, authority or extras
        return r;
    }

    /** Offset just past the first question (name + type + class), or -1. */
    private static int questionEnd(byte[] dns) {
        if (dns.length < 12 || dns[4] != 0 || dns[5] == 0) return -1;
        int i = 12;
        while (i < dns.length) {
            int len = dns[i] & 0xff;
            if (len == 0) {
                return i + 5 <= dns.length ? i + 5 : -1;
            }
            if ((len & 0xc0) != 0) return -1; // compression is never used in questions
            i += len + 1;
        }
        return -1;
    }
}
