package com.rdm.android;

import java.net.IDN;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

final class ConnectionValidator {
    private ConnectionValidator() { }

    static String normalizeHost(String input) {
        if (input == null) throw new IllegalArgumentException("Enter a PC name or IP address.");
        String host = input.trim();
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        if (host.isEmpty()) throw new IllegalArgumentException("Enter a PC name or IP address.");
        if (host.contains("/") || host.contains("\\") || host.contains("@")
                || host.contains("?") || host.contains("#") || containsWhitespace(host)) {
            throw new IllegalArgumentException("Enter only the PC name or IP address.");
        }

        if (host.indexOf(':') >= 0) {
            try {
                InetAddress address = InetAddress.getByName(host);
                if (!(address instanceof Inet6Address)) {
                    throw new IllegalArgumentException("Check the IPv6 address.");
                }
                return host.toLowerCase(Locale.ROOT);
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Check the IPv6 address.");
            }
        }

        try {
            String ascii = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES);
            if (ascii.length() > 253 || !isValidDnsName(ascii)) {
                throw new IllegalArgumentException("Check the PC name or IP address.");
            }
            return ascii.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Check the PC name or IP address.");
        }
    }

    static int parsePort(String input) {
        if (input == null || input.trim().isEmpty()) return 3389;
        String value = input.trim();
        if (!value.matches("[0-9]{1,5}")) {
            throw new IllegalArgumentException("Port must be a number from 1 to 65535.");
        }
        int port;
        try {
            port = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Port must be a number from 1 to 65535.");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be a number from 1 to 65535.");
        }
        return port;
    }

    static HostAndPort parseQuickConnect(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter a PC name or IP address.");
        }
        String value = input.trim();
        String host = value;
        int port = 3389;

        if (value.startsWith("[")) {
            int closing = value.indexOf(']');
            if (closing < 0) throw new IllegalArgumentException("Close the IPv6 address with `]`.");
            host = value.substring(1, closing);
            String suffix = value.substring(closing + 1);
            if (!suffix.isEmpty()) {
                if (!suffix.startsWith(":")) throw new IllegalArgumentException("Use [IPv6]:port.");
                port = parsePort(suffix.substring(1));
            }
        } else {
            int colonCount = 0;
            for (int i = 0; i < value.length(); i++) if (value.charAt(i) == ':') colonCount++;
            if (colonCount == 1) {
                int split = value.lastIndexOf(':');
                String possiblePort = value.substring(split + 1);
                if (possiblePort.matches("[0-9]+")) {
                    host = value.substring(0, split);
                    port = parsePort(possiblePort);
                }
            }
        }
        return new HostAndPort(normalizeHost(host), port);
    }

    private static boolean isValidDnsName(String host) {
        String candidate = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
        if (candidate.isEmpty()) return false;
        String[] labels = candidate.split("\\.", -1);
        for (String label : labels) {
            if (label.isEmpty() || label.length() > 63
                    || !label.matches("[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?")) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) return true;
        }
        return false;
    }

    static final class HostAndPort {
        final String host;
        final int port;

        HostAndPort(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }
}
