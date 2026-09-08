package com.exskylab.koala.core.helpers;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record IpNetwork(byte[] address, int prefixLength) {

    public static IpNetwork parse(String value) {
        String[] parts = value.trim().split("/", 2);
        InetAddress address = literalAddress(parts[0]);
        if (address == null) {
            throw new IllegalArgumentException("Guvenilir proxy IP veya CIDR olmali.");
        }
        int bits = address.getAddress().length * 8;
        int prefix;
        try {
            prefix = parts.length == 1 ? bits : Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Guvenilir proxy CIDR on eki gecersiz.", exception);
        }
        if (prefix < 0 || prefix > bits) {
            throw new IllegalArgumentException("Guvenilir proxy CIDR on eki gecersiz.");
        }
        return new IpNetwork(address.getAddress(), prefix);
    }

    public static InetAddress literalAddress(String value) {
        if (value == null || !value.matches("[0-9a-fA-F:.]+")) {
            return null;
        }
        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    public static boolean coverTheWholeAddressSpace(Collection<IpNetwork> networks) {
        Map<Integer, List<IpNetwork>> byFamily = networks.stream()
                .collect(Collectors.groupingBy(network -> network.address.length));
        return byFamily.values().stream().anyMatch(IpNetwork::coverTheirFamily);
    }

    public boolean contains(InetAddress candidate) {
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

    private static boolean coverTheirFamily(List<IpNetwork> family) {
        BigInteger addressCount = BigInteger.TWO.pow(family.getFirst().address.length * 8);
        List<IpNetwork> ordered = new ArrayList<>(family);
        ordered.sort(Comparator.comparing(IpNetwork::firstAddress));

        BigInteger covered = BigInteger.ZERO;
        for (IpNetwork network : ordered) {
            if (network.firstAddress().compareTo(covered) > 0) {
                return false;
            }
            BigInteger next = network.firstAddress().add(network.addressCount());
            covered = covered.max(next);
        }
        return covered.compareTo(addressCount) >= 0;
    }

    private BigInteger firstAddress() {
        byte[] base = address.clone();
        for (int index = 0; index < base.length; index++) {
            int keptBits = Math.clamp(prefixLength - index * 8, 0, 8);
            base[index] = (byte) (base[index] & (0xFF << (8 - keptBits)));
        }
        return new BigInteger(1, base);
    }

    private BigInteger addressCount() {
        return BigInteger.TWO.pow(address.length * 8 - prefixLength);
    }
}
