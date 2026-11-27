package com.edusync.edusync.backend.attendance;

import com.edusync.edusync.backend.attendance.service.SubnetMatcher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubnetMatcherTest {

    @Test
    void ipv4InsideAndOutside() {
        assertTrue(SubnetMatcher.contains("10.20.5.0/24", "10.20.5.7"));
        assertTrue(SubnetMatcher.contains("10.20.5.0/24", "10.20.5.255"));
        assertFalse(SubnetMatcher.contains("10.20.5.0/24", "10.20.6.1"));
        assertFalse(SubnetMatcher.contains("10.20.5.0/24", "192.168.1.5"));
        assertTrue(SubnetMatcher.contains("10.20.0.0/16", "10.20.99.1"));
    }

    @Test
    void severalSubnets_plainIp_andIpv6() {
        assertTrue(SubnetMatcher.contains("10.20.5.0/24, 10.20.6.0/24", "10.20.6.9"));
        assertTrue(SubnetMatcher.contains("10.20.5.10", "10.20.5.10"));
        assertFalse(SubnetMatcher.contains("10.20.5.10", "10.20.5.11"));
        assertTrue(SubnetMatcher.contains("fd00:1::/64", "fd00:1::abcd"));
        assertFalse(SubnetMatcher.contains("fd00:1::/64", "10.20.5.7"));        // IPv6 room, IPv4 student
        assertTrue(SubnetMatcher.contains("10.20.5.0/24", "::ffff:10.20.5.7"));  // IPv4 written as IPv6
    }

    @Test
    void badInput_neverMatches_andIsNotValid() {
        assertFalse(SubnetMatcher.contains(null, "10.20.5.7"));
        assertFalse(SubnetMatcher.contains("", "10.20.5.7"));
        assertFalse(SubnetMatcher.contains("10.20.5.0/24", "not-an-ip"));
        assertFalse(SubnetMatcher.contains("campus.local/24", "10.20.5.7"));     // host names are refused (no DNS)

        assertTrue(SubnetMatcher.isValid("10.20.5.0/24"));
        assertTrue(SubnetMatcher.isValid("10.20.5.0/24,10.20.6.0/24"));
        assertFalse(SubnetMatcher.isValid("10.20.5.0/33"));
        assertFalse(SubnetMatcher.isValid("abc"));
        assertFalse(SubnetMatcher.isValid(""));
    }
}
