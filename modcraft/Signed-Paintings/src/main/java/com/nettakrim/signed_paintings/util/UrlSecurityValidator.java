package com.nettakrim.signed_paintings.util;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Validates media URLs to enforce HTTPS-only access and prevent SSRF against local/private networks.
 */
public final class UrlSecurityValidator {
    private UrlSecurityValidator() {}

    /**
     * Fast syntactic check for an HTTPS URL (safe to call on the client/render thread).
     */
    public static boolean isValidHttpsUrl(String rawUrl) {
        return parseHttpsUri(rawUrl) != null;
    }

    /**
     * Parses and validates that the URL uses HTTPS, has a valid non-local host, and has no userinfo.
     */
    public static URI parseHttpsUri(String rawUrl) {
        if (rawUrl == null) {
            return null;
        }
        String trimmed = rawUrl.trim();
        if (trimmed.isEmpty() || trimmed.length() > 2048) {
            return null;
        }

        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException e) {
            return null;
        }

        String scheme = uri.getScheme();
        if (scheme == null || !scheme.equalsIgnoreCase("https")) {
            return null;
        }

        if (uri.getRawUserInfo() != null) {
            return null;
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return null;
        }

        int port = uri.getPort();
        if (port != -1 && (port < 1 || port > 65535)) {
            return null;
        }

        if (isBlockedHostname(host)) {
            return null;
        }

        return uri;
    }

    /**
     * Performs full DNS resolution and ensures none of the resolved addresses are private, loopback,
     * link-local, multicast, or reserved. Must be called off the main thread.
     */
    public static URI validateAndResolveHttpsUri(String rawUrl) throws SecurityException {
        URI uri = parseHttpsUri(rawUrl);
        if (uri == null) {
            throw new SecurityException("Rejected non-HTTPS or invalid URL: " + rawUrl);
        }

        String host = uri.getHost();
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses == null || addresses.length == 0) {
                throw new SecurityException("Could not resolve host: " + host);
            }
            for (InetAddress address : addresses) {
                if (isDisallowedAddress(address)) {
                    throw new SecurityException("Blocked SSRF target address for host " + host + ": " + address.getHostAddress());
                }
            }
        } catch (UnknownHostException e) {
            throw new SecurityException("Unknown host: " + host, e);
        }

        return uri;
    }

    private static boolean isBlockedHostname(String host) {
        String lower = host.toLowerCase(Locale.ROOT);
        if (lower.equals("localhost")
                || lower.endsWith(".localhost")
                || lower.endsWith(".local")
                || lower.endsWith(".internal")
                || lower.equals("ip6-localhost")
                || lower.equals("ip6-loopback")) {
            return true;
        }
        return false;
    }

    private static boolean isDisallowedAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;
            // 0.0.0.0/8 ("this network")
            if (b0 == 0) return true;
            // 100.64.0.0/10 (Carrier-grade NAT / shared address space)
            if (b0 == 100 && (b1 & 0xC0) == 64) return true;
            // 169.254.0.0/16 (Link-local / cloud metadata)
            if (b0 == 169 && b1 == 254) return true;
            // 192.0.0.0/24 (IETF protocol assignments)
            if (b0 == 192 && b1 == 0 && (bytes[2] & 0xFF) == 0) return true;
            // 198.18.0.0/15 (Benchmarking)
            if (b0 == 198 && (b1 == 18 || b1 == 19)) return true;
            // 240.0.0.0/4 (Reserved / broadcast)
            if (b0 >= 240) return true;
        } else if (bytes.length == 16) {
            // fc00::/7 (Unique local IPv6)
            if ((bytes[0] & 0xFE) == 0xFC) return true;
        }

        return false;
    }
}
