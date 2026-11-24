package com.playly.ai.plan;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

final class DockerGameRunner {
    private static final String IMAGE = "playly-game-runner";
    private static final int MAX_PACKAGE_BYTES = 16 * 1024 * 1024;
    private static final Pattern OUTPUT_PATH =
            Pattern.compile("(?:[a-zA-Z0-9_-]+/)*[a-zA-Z0-9_-][a-zA-Z0-9_.-]*");
    private static final Gson JSON = new Gson();
    private final Path output;
    private final String runtime;

    DockerGameRunner(Path output) throws IOException, InterruptedException {
        this(output, null);
    }

    DockerGameRunner(Path output, String runtime) throws IOException, InterruptedException {
        this.runtime = runtime;
        this.output = output.toAbsolutePath().normalize();
        if (Files.exists(this.output)) {
            throw new IOException("Output directory already exists: " + this.output);
        }
        var inspect = new ProcessBuilder("docker", "image", "inspect", IMAGE)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        try {
            if (!inspect.waitFor(10, TimeUnit.SECONDS) || inspect.exitValue() != 0) {
                throw new IOException("Docker and the playly-game-runner image are required.");
            }
        } finally {
            inspect.destroyForcibly();
        }
    }

    Path build(List<GeneratedSources.SourceFile> sources) throws IOException, InterruptedException {
        var temporary = Files.createTempDirectory("playly-runner-");
        try {
            var input = temporary.resolve("input.json");
            var result = temporary.resolve("result.json");
            var log = temporary.resolve("runner.log");
            Files.writeString(input, JSON.toJson(sources));
            runContainer(input, result, log);
            return writePackage(result);
        } finally {
            deleteTree(temporary);
        }
    }

    private void runContainer(Path input, Path result, Path log) throws IOException, InterruptedException {
        String name = "playly-runner-" + UUID.randomUUID();
        var args = new java.util.ArrayList<>(List.of("docker", "run", "--rm", "--pull=never", "--name", name, "-i",
                "--network=none", "--read-only", "--cap-drop=ALL", "--security-opt=no-new-privileges:true",
                "--cpus=1", "--memory=1g", "--memory-swap=1g", "--pids-limit=128", "--shm-size=128m",
                "--tmpfs", "/work:rw,nosuid,nodev,size=256m,mode=1777",
                "--tmpfs", "/tmp:rw,nosuid,nodev,size=128m,mode=1777"));
        if (runtime != null) args.add("--runtime=" + runtime);
        args.add(IMAGE);
        var process = new ProcessBuilder(args).redirectInput(input.toFile()).redirectOutput(result.toFile())
                .redirectError(log.toFile()).start();
        try {
            if (!process.waitFor(150, TimeUnit.SECONDS)) {
                throw new IOException("Game sandbox exceeded its time limit.");
            }
            var status = process.exitValue();
            if (status != 0) {
                String diagnostics;
                try (var errors = Files.newInputStream(log)) {
                    diagnostics = new String(errors.readNBytes(8192), StandardCharsets.UTF_8)
                            .replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "").strip();
                }
                if (status == 20 || status == 21) {
                    throw new GameSourceGenerator.RejectedBuild(diagnostics);
                }
                throw new IOException("Game sandbox exited with code " + status + ". " + diagnostics);
            }
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                stopContainer(name);
            }
        }
    }

    private static void stopContainer(String name) throws IOException, InterruptedException {
        var removal = new ProcessBuilder("docker", "rm", "-f", name)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        try {
            removal.waitFor(10, TimeUnit.SECONDS);
        } finally {
            removal.destroyForcibly();
        }
    }

    private Path writePackage(Path result) throws IOException {
        if (Files.size(result) > 24 * 1024 * 1024) {
            throw new IOException("Game sandbox response exceeds the size limit.");
        }
        ArtifactFile[] files;
        try (var reader = Files.newBufferedReader(result)) {
            files = JSON.fromJson(reader, ArtifactFile[].class);
        } catch (JsonParseException exception) {
            throw new IOException("Game sandbox returned invalid output.", exception);
        }
        if (files == null || files.length == 0 || files.length > 512) {
            throw new IOException("Game sandbox returned an invalid file list.");
        }
        Files.createDirectories(output.getParent());
        var staging = Files.createTempDirectory(output.getParent(), ".playly-dist-");
        try {
            var paths = new HashSet<String>();
            var size = 0L;
            for (var file : files) {
                if (file == null || file.path() == null || file.data() == null
                        || !OUTPUT_PATH.matcher(file.path()).matches() || !paths.add(file.path())) {
                    throw new IOException("Game sandbox returned an invalid output file.");
                }
                byte[] data;
                try {
                    data = Base64.getDecoder().decode(file.data());
                } catch (IllegalArgumentException exception) {
                    throw new IOException("Game sandbox returned invalid file contents.", exception);
                }
                size += data.length;
                if (size > MAX_PACKAGE_BYTES) {
                    throw new IOException("Game package exceeds the size limit.");
                }
                var target = staging.resolve(file.path());
                Files.createDirectories(target.getParent());
                Files.write(target, data);
            }
            if (!paths.contains("index.html")) {
                throw new IOException("Game sandbox output is missing index.html.");
            }
            Files.move(staging, output);
            return output;
        } finally {
            deleteTree(staging);
        }
    }

    static void deleteTree(Path root) throws IOException {
        if (Files.exists(root)) {
            try (var paths = Files.walk(root)) {
                for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }

    private record ArtifactFile(String path, String data) {}
}
