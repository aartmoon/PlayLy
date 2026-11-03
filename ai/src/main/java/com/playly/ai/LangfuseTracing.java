package com.playly.ai;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

final class LangfuseTracing {
    private LangfuseTracing() {
    }

    static OpenTelemetrySdk configure() {
        var publicKey = System.getenv().getOrDefault("LANGFUSE_PUBLIC_KEY", "");
        var secretKey = System.getenv().getOrDefault("LANGFUSE_SECRET_KEY", "");
        if (publicKey.isBlank() && secretKey.isBlank()) {
            return OpenTelemetrySdk.builder().build();
        }
        if (publicKey.isBlank() || secretKey.isBlank()) {
            throw new IllegalArgumentException("Configure both Langfuse API keys.");
        }

        var baseUrl = System.getenv().getOrDefault("LANGFUSE_BASE_URL", "http://localhost:3000");
        var credentials = Base64.getEncoder().encodeToString(
                (publicKey + ":" + secretKey).getBytes(StandardCharsets.UTF_8));
        var exporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(baseUrl.replaceAll("/+$", "") + "/api/public/otel/v1/traces")
                .addHeader("Authorization", "Basic " + credentials)
                .addHeader("x-langfuse-ingestion-version", "4")
                .build();
        var tracerProvider = SdkTracerProvider.builder()
                .setResource(Resource.getDefault().merge(Resource.create(Attributes.builder()
                        .put("service.name", "playly-ai")
                        .build())))
                .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
                .build();
        return OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();
    }
}
