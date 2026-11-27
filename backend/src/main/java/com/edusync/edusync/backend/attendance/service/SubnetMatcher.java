package com.edusync.edusync.backend.attendance.service;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * LOCAL_WIFI_QR "in the room" check (decided 27 Nov 2026): the student's IP must be
 * inside the room's subnet, written as CIDR, e.g. "10.20.5.0/24" (IPv4 or IPv6).
 * Several subnets may be separated by commas: "10.20.5.0/24, 10.20.6.0/24".
 * A plain IP without "/n" must match exactly.
 *
 * Only numeric addresses are accepted, so InetAddress never does a DNS lookup here.
 */
public final class SubnetMatcher {

    private SubnetMatcher() {
    }

    public static boolean contains(String subnets, String ip) {
        if (subnets == null || subnets.isBlank() || ip == null || ip.isBlank()) {
            return false;
        }
        byte[] address = parse(ip.trim());
        if (address == null) {
            return false;
        }
        for (String subnet : subnets.split(",")) {
            if (matches(subnet.trim(), address)) {
                return true;
            }
        }
        return false;
    }

    /** True if the text is a valid subnet list (used when checking a room before a LOCAL_WIFI_QR session). */
    public static boolean isValid(String subnets) {
        if (subnets == null || subnets.isBlank()) {
            return false;
        }
        for (String subnet : subnets.split(",")) {
            String[] parts = subnet.trim().split("/", 2);
            byte[] network = parse(parts[0]);
            if (network == null) {
                return false;
            }
            if (parts.length == 2) {
                try {
                    int bits = Integer.parseInt(parts[1].trim());
                    if (bits < 0 || bits > network.length * 8) {
                        return false;
                    }
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean matches(String subnet, byte[] address) {
        String[] parts = subnet.split("/", 2);
        byte[] network = parse(parts[0]);
        if (network == null || network.length != address.length) {
            return false;   // IPv4 vs IPv6 never match
        }
        int bits;
        try {
            bits = parts.length == 2 ? Integer.parseInt(parts[1].trim()) : network.length * 8;
        } catch (NumberFormatException e) {
            return false;
        }
        if (bits < 0 || bits > network.length * 8) {
            return false;
        }
        for (int i = 0; i < bits; i++) {
            int mask = 0x80 >> (i % 8);
            if ((network[i / 8] & mask) != (address[i / 8] & mask)) {
                return false;
            }
        }
        return true;
    }

    /** Numeric IPv4 / IPv6 only (no host names). IPv4-mapped IPv6 (::ffff:10.0.0.1) becomes IPv4. */
    private static byte[] parse(String text) {
        String t = text.trim();
        boolean numeric = t.matches("[0-9.]+") || (t.contains(":") && t.matches("[0-9a-fA-F:.%]+"));
        if (!numeric) {
            return null;
        }
        try {
            return InetAddress.getByName(t).getAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
