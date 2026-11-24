package com.playly.ai.plan;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public final class RunnerServer {
    static final int MAX_INPUT = 2 * 1024 * 1024;
    static final int MAX_OUTPUT = 24 * 1024 * 1024;
    private static final Gson JSON = new Gson();
    private static final Semaphore BUILD_SLOT = new Semaphore(1);

    private RunnerServer() {}

    public static void main(String[] args) throws IOException, InterruptedException {
        String runtime = System.getenv("PLAYLY_SANDBOX_RUNTIME");
        String token = System.getenv().getOrDefault("PLAYLY_RUNNER_TOKEN", "");
        if (runtime == null || !Set.of("runsc", "kata", "kata-runtime").contains(runtime) || token.length() < 32) {
            throw new IllegalArgumentException("Configure gVisor or Kata and a runner token of at least 32 characters.");
        }
        requireRuntime(runtime);
        var address = new InetSocketAddress(System.getenv().getOrDefault("PLAYLY_RUNNER_BIND", "127.0.0.1"),
                Integer.parseInt(System.getenv().getOrDefault("PLAYLY_RUNNER_PORT", "8091")));
        var server = HttpServer.create(address, 8);
        server.createContext("/build", exchange -> serve(exchange, runtime, token));
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
    }

    private static void requireRuntime(String runtime) throws IOException, InterruptedException {
        var format = "{{range $name, $_ := .Runtimes}}{{if eq $name \"" + runtime + "\"}}ready{{end}}{{end}}";
        var docker = new ProcessBuilder("docker", "info", "--format", format)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            if (!docker.waitFor(10, TimeUnit.SECONDS) || docker.exitValue() != 0
                    || !"ready".equals(new String(docker.getInputStream().readNBytes(32), StandardCharsets.UTF_8).strip())) {
                throw new IOException("The configured isolation runtime is not registered in Docker.");
            }
        } finally {
            if (docker.isAlive()) docker.destroyForcibly();
        }
    }

    private static void serve(HttpExchange exchange, String runtime, String token) throws IOException {
        try (exchange) {
            String header = exchange.getRequestHeaders().getFirst("Authorization");
            if (!exchange.getRequestURI().getPath().equals("/build") || !exchange.getRequestMethod().equals("POST")
                    || header == null || !MessageDigest.isEqual(header.getBytes(StandardCharsets.UTF_8),
                            ("Bearer " + token).getBytes(StandardCharsets.UTF_8))) {
                respond(exchange, 404, new byte[0]);
                return;
            }
            if (!BUILD_SLOT.tryAcquire()) {
                respond(exchange, 503, new byte[0]);
                return;
            }
            try {
                byte[] body = exchange.getRequestBody().readNBytes(MAX_INPUT + 1);
                if (body.length > MAX_INPUT) throw new IllegalArgumentException();
                var source = GeneratedSources.parseArchive(new String(body, StandardCharsets.UTF_8));
                var temporary = Files.createTempDirectory("playly-build-");
                try {
                    var output = new DockerGameRunner(temporary.resolve("dist"), runtime).build(source);
                    try (var paths = Files.walk(output)) {
                        var files = new java.util.ArrayList<Map<String, String>>();
                        for (var path : paths.filter(Files::isRegularFile).sorted().toList()) {
                            files.add(Map.of("path", output.relativize(path).toString().replace('\\', '/'),
                                    "data", Base64.getEncoder().encodeToString(Files.readAllBytes(path))));
                        }
                        byte[] result = JSON.toJson(files).getBytes(StandardCharsets.UTF_8);
                        if (result.length > MAX_OUTPUT) throw new IOException("Runner output is too large");
                        respond(exchange, 200, result);
                    }
                } finally {
                    DockerGameRunner.deleteTree(temporary);
                }
            } catch (IllegalArgumentException invalid) {
                respond(exchange, 400, new byte[0]);
            } catch (GameSourceGenerator.RejectedBuild rejected) {
                respond(exchange, 422, rejected.getMessage().getBytes(StandardCharsets.UTF_8));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (IOException unavailable) {
                if (exchange.getResponseCode() == -1) respond(exchange, 503, new byte[0]);
                System.err.println("Sandbox did not complete a build.");
            } finally {
                BUILD_SLOT.release();
            }
        }
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", status == 200 ? "application/json" : "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        exchange.getResponseBody().write(body);
    }
}
