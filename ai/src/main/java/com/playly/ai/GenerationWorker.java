package com.playly.ai;

import java.util.concurrent.CountDownLatch;

public class GenerationWorker {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("PlayLy AI worker scaffold is idle.");
        new CountDownLatch(1).await();
    }
}
