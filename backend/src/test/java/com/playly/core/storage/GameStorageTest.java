package com.playly.core.storage;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GameStorageTest {
    private HttpServer server;
    private GameStorage storage;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        storage = new GameStorage("http://127.0.0.1:" + server.getAddress().getPort(),
                "https://storage.example.com", "playly", "playly-local", "playly-games",
                Duration.ofMinutes(5), Duration.ofHours(24), Duration.ofMinutes(10));
    }

    @AfterEach
    void stop() {
        if (storage != null) storage.close();
        if (server != null) server.stop(0);
    }

    @Test
    void signsForPublicHostWithPathStyleAndNoAdditionalHeaders() {
        var file = new GameStorage.StoredFile("game.json", "playly-games", "generations/a/game +1.json",
                "application/json", 2L, "hash");
        var manifest = new GameStorage.Manifest("1.0", "1.0.0", file, List.of());

        var result = storage.sign(UUID.randomUUID(), UUID.randomUUID(), manifest);
        var url = URI.create(result.gameSpec().url());

        assertEquals("storage.example.com", url.getHost());
        assertEquals("/playly-games/generations/a/game +1.json", url.getPath());
        assertTrue(url.getRawQuery().contains("X-Amz-SignedHeaders=host"));
        assertTrue(url.getRawQuery().contains("X-Amz-Expires=300"));
        assertTrue(url.getRawQuery().contains("X-Amz-Signature="));
    }

    @Test
    void readsObjectsAndDistinguishesMissingKeysFromStorageFailures() throws Exception {
        server.createContext("/playly-games/", exchange -> {
            String key = exchange.getRequestURI().getPath();
            int status = key.endsWith("missing") ? 404 : key.endsWith("denied") ? 403 : 200;
            String response = status == 200 ? "{}" : "<Error><Code>"
                    + (status == 404 ? "NoSuchKey" : "AccessDenied") + "</Code><Message>test</Message></Error>";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", status == 200 ? "application/json" : "application/xml");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });

        try (var object = storage.open("game.json")) {
            assertEquals("{}", new String(object.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertThrows(GameStorage.InvalidPackage.class, () -> storage.open("missing"));
        assertThrows(GameStorage.StorageUnavailable.class, () -> storage.open("denied"));
    }

    @Test
    void listsAllPagesAndDeletesUsingBucketPath() {
        var pages = new AtomicInteger();
        var deletes = new AtomicInteger();
        server.createContext("/playly-games", exchange -> {
            if (exchange.getRequestMethod().equals("DELETE")) {
                if (exchange.getRequestURI().getPath().equals("/playly-games/generations/first.json")) {
                    deletes.incrementAndGet();
                }
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            boolean second = exchange.getRequestURI().getRawQuery().contains("continuation-token=next");
            pages.incrementAndGet();
            String response = "<ListBucketResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">"
                    + "<Name>playly-games</Name><Prefix>generations/</Prefix><KeyCount>1</KeyCount>"
                    + "<IsTruncated>" + !second + "</IsTruncated>"
                    + (second ? "" : "<NextContinuationToken>next</NextContinuationToken>")
                    + "<Contents><Key>generations/" + (second ? "second" : "first") + ".json</Key>"
                    + "<LastModified>2026-01-01T00:00:00Z</LastModified><Size>2</Size></Contents>"
                    + "</ListBucketResult>";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/xml");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });

        var keys = new ArrayList<String>();
        for (var object : storage.generationObjects()) keys.add(object.key());
        assertEquals(List.of("generations/first.json", "generations/second.json"), keys);
        assertEquals(2, pages.get());
        storage.remove(keys.getFirst());
        assertEquals(1, deletes.get());
    }
}
