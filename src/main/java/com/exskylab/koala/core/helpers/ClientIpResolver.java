package com.exskylab.koala.core.helpers;

import com.exskylab.koala.core.properties.TrustedProxyProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

@Component
public final class ClientIpResolver {

    private final List<Network> trustedNetworks;

    public ClientIpResolver(TrustedProxyProperties properties) {
        this.trustedNetworks = properties.getTrustedProxies().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(Network::parse)
                .toList();
    }

    public String resolve(HttpServletRequest request) {
        String remote = normalizeAddress(request.getRemoteAddr());
        if (remote == null) {
            return "unknown";
        }
        if (!isTrusted(remote)) {
            return remote;
        }

        List<String> chain = forwardedChain(request);
        for (int index = chain.size() - 1; index >= 0; index--) {
            String candidate = chain.get(index);
            if (!isTrusted(candidate)) {
                return candidate;
            }
        }
        return chain.isEmpty() ? remote : chain.getFirst();
    }

    private boolean isTrusted(String address) {
        InetAddress parsed = parseAddress(address);
        return parsed != null && trustedNetworks.stream().anyMatch(network -> network.contains(parsed));
    }

    private static List<String> forwardedChain(HttpServletRequest request) {
        String forwarded = request.getHeader("Forwarded");
        if (forwarded != null && !forwarded.isBlank()) {
            List<String> parsed = new ArrayList<>();
            for (String element : forwarded.split(",")) {
                for (String parameter : element.split(";")) {
                    String[] pair = parameter.trim().split("=", 2);
                    if (pair.length == 2 && pair[0].equalsIgnoreCase("for")) {
                        String address = normalizeAddress(pair[1]);
                        if (address != null) {
                            parsed.add(address);
                        }
                    }
                }
            }
            if (!parsed.isEmpty()) {
                return parsed;
            }
        }

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor == null || xForwardedFor.isBlank()) {
            return List.of();
        }
        List<String> parsed = new ArrayList<>();
        for (String value : xForwardedFor.split(",")) {
            String address = normalizeAddress(value);
            if (address != null) {
                parsed.add(address);
            }
        }
        return parsed;
    }

    private static String normalizeAddress(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String candidate = value.trim();
        if (candidate.startsWith("\"") && candidate.endsWith("\"") && candidate.length() > 1) {
            candidate = candidate.substring(1, candidate.length() - 1);
        }
        if (candidate.startsWith("[") && candidate.contains("]")) {
            candidate = candidate.substring(1, candidate.indexOf(']'));
        } else if (candidate.chars().filter(character -> character == ':').count() == 1
                && candidate.contains(".")) {
            candidate = candidate.substring(0, candidate.indexOf(':'));
        }
        return parseAddress(candidate) == null ? null : candidate;
    }

    private static InetAddress parseAddress(String value) {
        if (value == null || !value.matches("[0-9a-fA-F:.]+")) {
            return null;
        }
        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    private record Network(byte[] address, int prefixLength) {

        private static Network parse(String value) {
            String[] parts = value.trim().split("/", 2);
            InetAddress address = parseAddress(parts[0]);
            if (address == null) {
                throw new IllegalArgumentException("Guvenilir proxy IP veya CIDR olmali.");
            }
            int bits = address.getAddress().length * 8;
            int prefix = parts.length == 1 ? bits : Integer.parseInt(parts[1]);
            if (prefix < 0 || prefix > bits) {
                throw new IllegalArgumentException("Guvenilir proxy CIDR on eki gecersiz.");
            }
            return new Network(address.getAddress(), prefix);
        }

        private boolean contains(InetAddress candidate) {
            byte[] other = candidate.getAddress();
            if (other.length != address.length) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;
            for (int index = 0; index < fullBytes; index++) {
                if (address[index] != other[index]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainingBits);
            return (address[fullBytes] & mask) == (other[fullBytes] & mask);
        }
    }
}
