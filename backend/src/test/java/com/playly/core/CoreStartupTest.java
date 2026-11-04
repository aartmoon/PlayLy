package com.playly.core;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "playly.auth.jwt-secret=playly-test-secret-only-for-startup-test",
        "spring.flyway.enabled=false",
        "spring.kafka.admin.auto-create=false",
        "spring.kafka.listener.auto-startup=false"
})
class CoreStartupTest {
    @MockitoBean(name = "taskScheduler")
    private ThreadPoolTaskScheduler taskScheduler;

    @MockitoBean(name = "outboxScheduler")
    private ThreadPoolTaskScheduler outboxScheduler;

    @Autowired
    private ApplicationContext context;

    @Test
    void startsWithGenerationServicesAndKafkaTopics() {
        assertEquals("game.generation.request", context.getBean("generationRequestsTopic", NewTopic.class).name());
        assertEquals("game.generation.result", context.getBean("generationResultsTopic", NewTopic.class).name());
    }
}
