package io.github.daggerok.distributed.lock.mongodb;

import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.springframework.boot.autoconfigure.mongo.MongoProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Log4j2
@Testcontainers
@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public abstract class AbstractTestcontainersTests {

    @Container
    protected static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:4.4.2")
            .withExposedPorts(MongoProperties.DEFAULT_PORT)
            .waitingFor(new HostPortWaitStrategy())
            .withAccessToHost(true);

    @DynamicPropertySource
    protected static void setupSpringBootProperties(DynamicPropertyRegistry dynamicPropertyRegistry) {
        log.info("Setting up spring.data.mongodb={}", mongoDBContainer::getReplicaSetUrl);
        dynamicPropertyRegistry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }
}
