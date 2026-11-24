package com.playly.ai;

import com.playly.ai.plan.GenerationPipeline;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.playly.ai.GenerationStorage.JSON;
import static com.playly.ai.GenerationStorage.env;

public final class GenerationWorker {
    private static final String REQUEST = "game.generation.request";
    private static final String RESULT = "game.generation.result";
    private record Request(UUID eventId, Integer schemaVersion, UUID generationId, UUID gameId, String prompt,
                           Instant deadlineAt, String gameSpecVersion, String runtimeVersion, UUID baseVersionId,
                           GenerationStorage.Reference baseManifest) {}
    private record Error(String code, String message) {}
    private record Result(UUID eventId, int schemaVersion, UUID generationId, UUID gameId, String occurredAt,
                          String type, GenerationStorage.Reference manifest, Error error) {}

    private GenerationWorker() {}

    public static void main(String[] args) throws IOException, InterruptedException {
        try (var telemetry = LangfuseTracing.configure()) {
            var tracer = telemetry.getTracer("com.playly.ai");
            tracer.spanBuilder("playly.ai.worker.start").startSpan().end();
            if (!Boolean.parseBoolean(env("PLAYLY_GENERATION_ENABLED", "false"))) {
                System.out.println("PlayLy generation is disabled. Set PLAYLY_GENERATION_ENABLED to enable it.");
                new CountDownLatch(1).await();
                return;
            }
            try (var http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                         .connectTimeout(Duration.ofSeconds(8)).build();
                 var storage = new GenerationStorage()) {
                var langfuse = new LangfuseClient(http);
                var generation = new GenerationPipeline(http, tracer,
                        langfuse.pinnedPrompt("playly-" + GenerationPipeline.POLICY_VERSION, GenerationPipeline.policy()),
                        langfuse.pinnedPrompt("playly-" + GenerationPipeline.RUBRIC_VERSION, GenerationPipeline.rubric()));
                run(tracer, storage, generation, langfuse);
            }
        }
    }

    private static void run(Tracer tracer, GenerationStorage storage, GenerationPipeline pipeline, LangfuseClient scores) {
        String broker = env("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
        var consumerConfig = Map.<String, Object>of(
                "bootstrap.servers", broker, "group.id", "playly-ai", "group.protocol", "classic",
                "enable.auto.commit", false, "auto.offset.reset", "earliest", "max.poll.records", 1,
                "max.poll.interval.ms", 300_000);
        var producerConfig = Map.<String, Object>of("bootstrap.servers", broker, "acks", "all",
                "enable.idempotence", true, "delivery.timeout.ms", 10000, "request.timeout.ms", 5000,
                "max.block.ms", 5000);

        var stopped = new CountDownLatch(1);
        try (var consumer = new KafkaConsumer<>(consumerConfig, new StringDeserializer(), new StringDeserializer());
             var producer = new KafkaProducer<>(producerConfig, new StringSerializer(), new StringSerializer());
             var work = Executors.newVirtualThreadPerTaskExecutor()) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                consumer.wakeup();
                try {
                    stopped.await(8, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }, "generation-shutdown"));
            var inFlight = new java.util.concurrent.atomic.AtomicBoolean();
            consumer.subscribe(List.of(REQUEST), new ConsumerRebalanceListener() {
                @Override
                public void onPartitionsRevoked(Collection<TopicPartition> partitions) {}

                @Override
                public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                    if (inFlight.get()) consumer.pause(partitions);
                }
            });
            while (!Thread.currentThread().isInterrupted()) {
                for (var record : consumer.poll(Duration.ofSeconds(1))) {
                    var partition = new TopicPartition(record.topic(), record.partition());
                    try {
                        var request = parse(record);
                        if (request != null) {
                            String result = storage.outcome(request.generationId());
                            if (result == null) {
                                if (request.deadlineAt().isAfter(Instant.now())) {
                                    send(producer, record.key(), JSON.toJson(result(request, "STARTED", null, null)));
                                }
                                int generationId = consumer.groupMetadata().generationId();
                                inFlight.set(true);
                                consumer.pause(consumer.assignment());
                                try {
                                    var completed = finish(request, tracer, storage, pipeline, scores, work, () -> {
                                        consumer.poll(Duration.ZERO);
                                        if (consumer.groupMetadata().generationId() != generationId
                                                || !consumer.assignment().contains(partition)) {
                                            throw new IllegalStateException("Consumer group changed during generation");
                                        }
                                    });
                                    result = storage.remember(request.generationId(), JSON.toJson(completed));
                                } finally {
                                    inFlight.set(false);
                                    consumer.resume(consumer.assignment());
                                }
                            }
                            send(producer, record.key(), result);
                        }
                        consumer.commitSync(Map.of(partition, new OffsetAndMetadata(record.offset() + 1)));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    } catch (WakeupException shutdown) {
                        throw shutdown;
                    } catch (Exception retryable) {
                        System.err.println("Generation delivery retry: " + retryable.getClass().getSimpleName());
                        if (consumer.assignment().contains(partition)) consumer.seek(partition, record.offset());
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
            }
        } catch (WakeupException shutdown) {
            // Consumer shutdown.
        } finally {
            stopped.countDown();
        }
    }

    private static Request parse(ConsumerRecord<String, String> record) {
        try {
            var obj = com.google.gson.JsonParser.parseString(record.value()).getAsJsonObject();
            var deadline = Instant.parse(obj.remove("deadlineAt").getAsString());
            var dto = JSON.fromJson(obj, WireRequest.class);
            if (dto.eventId() == null || dto.gameId() == null || dto.generationId() == null
                    || !dto.generationId().toString().equals(record.key()) || dto.schemaVersion() == null
                    || (dto.schemaVersion() != 1 && dto.schemaVersion() != 2) || dto.prompt() == null
                    || (dto.baseVersionId() == null) != (dto.baseManifest() == null)) {
                throw new IllegalArgumentException("Invalid generation job");
            }
            return new Request(dto.eventId(), dto.schemaVersion(), dto.generationId(), dto.gameId(), dto.prompt(),
                    deadline, dto.gameSpecVersion(), dto.runtimeVersion(), dto.baseVersionId(), dto.baseManifest());
        } catch (RuntimeException invalid) {
            System.err.println("Discarding invalid generation request at offset " + record.offset());
            return null;
        }
    }

    private record WireRequest(UUID eventId, Integer schemaVersion, UUID generationId, UUID gameId, String prompt,
                               String gameSpecVersion, String runtimeVersion, UUID baseVersionId,
                               GenerationStorage.Reference baseManifest) {}

    private static Result finish(Request request, Tracer tracer, GenerationStorage storage, GenerationPipeline pipeline,
                                 LangfuseClient scores, java.util.concurrent.ExecutorService work, Runnable poll)
            throws InterruptedException {
        if (request.schemaVersion() != 2 || !"2.0".equals(request.gameSpecVersion()) || !"2.0.0".equals(request.runtimeVersion())) {
            return failure(request, "UNSUPPORTED_GAME_REQUEST");
        }
        long remaining = Duration.between(Instant.now(), request.deadlineAt()).toMillis();
        if (remaining <= 0) return failure(request, "GENERATION_TIMEOUT");
        Callable<Result> operation = () -> {
            var span = tracer.spanBuilder("playly.generation")
                    .setAttribute("generationId", request.generationId().toString()).startSpan();
            try (var scope = span.makeCurrent()) {
                var base = request.baseManifest() == null ? null : storage.readBase(request.baseManifest());
                var output = pipeline.generate(request.prompt(), base);
                GenerationStorage.checkDeadline(request.deadlineAt());
                scores.write(span.getSpanContext().getTraceId(), output.attemptId(), output.artifactSha256(), output.checks());
                var manifest = storage.store(request.generationId(), output, span.getSpanContext().getTraceId(), request.deadlineAt());
                GenerationStorage.checkDeadline(request.deadlineAt());
                return result(request, "SUCCEEDED", manifest, null);
            } catch (Exception error) {
                span.setStatus(StatusCode.ERROR, error.getClass().getSimpleName());
                throw error;
            } finally {
                span.end();
            }
        };
        var future = work.submit(operation);
        try {
            while (remaining > 0) {
                try {
                    return future.get(Math.min(remaining, 1000), TimeUnit.MILLISECONDS);
                } catch (TimeoutException waiting) {
                    poll.run();
                    remaining = Duration.between(Instant.now(), request.deadlineAt()).toMillis();
                }
            }
            return failure(request, "GENERATION_TIMEOUT");
        } catch (ExecutionException failed) {
            var cause = failed.getCause();
            String code = !Instant.now().isBefore(request.deadlineAt()) ? "GENERATION_TIMEOUT" : switch (cause) {
                case GenerationPipeline.Failure failure -> failure.code();
                case software.amazon.awssdk.core.exception.SdkException ignored -> "STORAGE_ERROR";
                case IllegalArgumentException ignored -> "INVALID_GAME_SPEC";
                default -> "INTERNAL_ERROR";
            };
            System.err.println("Generation " + request.generationId() + " failed: " + code + " (" +
                    cause.getClass().getSimpleName() + ")");
            return failure(request, code);
        } finally {
            future.cancel(true);
        }
    }

    private static Result failure(Request request, String code) {
        return result(request, "FAILED", null, new Error(code, "Game generation failed"));
    }

    private static Result result(Request request, String type, GenerationStorage.Reference manifest, Error error) {
        var event = UUID.nameUUIDFromBytes((request.eventId() + ":" + type).getBytes(StandardCharsets.UTF_8));
        return new Result(event, request.schemaVersion(), request.generationId(), request.gameId(), Instant.now().toString(),
                type, manifest, error);
    }

    private static void send(KafkaProducer<String, String> producer, String key, String value)
            throws ExecutionException, InterruptedException, TimeoutException {
        producer.send(new ProducerRecord<>(RESULT, key, value)).get(15, TimeUnit.SECONDS);
    }
}
