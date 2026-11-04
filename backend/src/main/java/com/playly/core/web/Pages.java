package com.playly.core.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public final class Pages {
    public record Page<T>(List<T> items, String nextCursor) {}
    public record Position(OffsetDateTime time, UUID id) {}

    public static Position position(String cursor, String scope) {
        if (cursor == null) return null;
        try {
            String[] parts = decode(cursor, scope).split("/", -1);
            if (parts.length != 2) throw ApiProblem.badRequest();
            return new Position(OffsetDateTime.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (IllegalArgumentException | java.time.DateTimeException error) {
            throw ApiProblem.badRequest();
        }
    }

    public static int number(String cursor, String scope) {
        if (cursor == null) return Integer.MAX_VALUE;
        try {
            int number = Integer.parseInt(decode(cursor, scope));
            if (number <= 0) throw ApiProblem.badRequest();
            return number;
        } catch (IllegalArgumentException error) {
            throw ApiProblem.badRequest();
        }
    }

    public static <T> Page<T> page(List<T> rows, int limit, String scope, Function<T, String> position) {
        if (rows.size() <= limit) return new Page<>(rows, null);
        return new Page<>(rows.subList(0, limit), encode(scope, position.apply(rows.get(limit - 1))));
    }

    private static String encode(String scope, String position) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((scopeHash(scope) + ":" + position).getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String cursor, String scope) {
        String value = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        String prefix = scopeHash(scope) + ":";
        if (!value.startsWith(prefix)) throw ApiProblem.badRequest();
        return value.substring(prefix.length());
    }

    private static String scopeHash(String scope) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(scope.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private Pages() {}
}
