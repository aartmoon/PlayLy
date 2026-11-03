package com.playly.ai;

import java.util.concurrent.CountDownLatch;

public class GenerationWorker {
    public static void main(String[] args) throws InterruptedException {
        var telemetry = LangfuseTracing.configure();
        Runtime.getRuntime().addShutdownHook(new Thread(telemetry::close, "langfuse-shutdown"));
        telemetry.getTracer("com.playly.ai").spanBuilder("playly.ai.worker.start")
                .setAttribute("langfuse.observation.type", "span")
                .startSpan()
                .end();

        System.out.println("PlayLy AI worker scaffold is idle.");
        new CountDownLatch(1).await();
    }
}
