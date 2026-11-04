package com.playly.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@SpringBootApplication
@EnableScheduling
public class PlayLyApplication {
    @Bean("taskScheduler")
    ThreadPoolTaskScheduler taskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("core-maintenance-");
        return scheduler;
    }

    @Bean("outboxScheduler")
    ThreadPoolTaskScheduler outboxScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("core-outbox-");
        return scheduler;
    }

    public static void main(String[] args) {
        SpringApplication.run(PlayLyApplication.class, args);
    }
}
