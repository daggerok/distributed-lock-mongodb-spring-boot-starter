package io.github.daggerok.distributed.lock.mongodb.example;

import io.github.daggerok.distributed.lock.mongodb.Lock;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.mongo.MongoProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Log4j2
@Testcontainers
@DisplayName("ExampleApplication tests")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExampleApplicationTests {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:4.4.2")
            .withExposedPorts(MongoProperties.DEFAULT_PORT)
            .waitingFor(new HostPortWaitStrategy())
            .withAccessToHost(true);

    @DynamicPropertySource
    static void setupSpringBootProperties(DynamicPropertyRegistry dynamicPropertyRegistry) {
        log.info("Setting up spring.data.mongodb={}", mongoDBContainer::getReplicaSetUrl);
        dynamicPropertyRegistry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @LocalServerPort
    Integer port;

    @Autowired
    TestRestTemplate testRestTemplate;

    @Autowired
    MongoTemplate mongoTemplate;

    Function<String, String> url = path -> {
        Objects.requireNonNull(path, "path may not be null");
        val aPath = path.startsWith("/") ? path : String.format("/%s", path);
        return String.format("http://127.0.0.1:%d%s", port, aPath);
    };

    @Test
    void should_do_integration_test() {
        // given no state nor lock
        Stream.of(Lock.class, LastMessage.class).forEach(type -> {
            if (mongoTemplate.collectionExists(Lock.class)) {
                mongoTemplate.remove(Lock.class);
            }
        });

        // and given
        val username = UUID.randomUUID().toString();
        val getStateUrl = url.apply(String.format("/get-state/%s", username));
        // when get current state
        val getStateResponseType = new ParameterizedTypeReference<Optional<LastMessage>>() {};
        val getStateResponse = testRestTemplate.exchange(URI.create(getStateUrl), HttpMethod.GET, null, getStateResponseType);
        log.info("getStateResponse: {}", getStateResponse);
        // then there is no such state for given user
        assertThat(getStateResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // and given
        String postInitialStateUrl = url.apply(String.format("/post-state/%s/initialize-state", username));
        // when initialize state
        val postStateResponseType = new ParameterizedTypeReference<Optional<List<LastMessage>>>() {};
        val postInitialStateResponse = testRestTemplate.exchange(postInitialStateUrl, HttpMethod.POST, null, postStateResponseType);
        log.info("postInitialStateResponse: {}", postInitialStateResponse);
        // then user initial state was posted successfully
        assertThat(postInitialStateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // and given
        val postLockUrl = url.apply(String.format("/post-lock/%s", username));
        // when posting lock
        val postLockResponseType = new ParameterizedTypeReference<Optional<Lock>>() {};
        val postLockResponse = testRestTemplate.exchange(postLockUrl, HttpMethod.POST, null, postLockResponseType);
        log.info("postLockResponse: {}", postLockResponse);
        // then lock was successful
        assertThat(postLockResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Optional<Lock> maybeLock = postLockResponse.getBody();
        assertThat(maybeLock).isPresent();
        // and lock ID was kept
        val lockId = maybeLock.get().getId();
        assertThat(lockId).isNotNull().isNotBlank();

        // and given
        val postShouldNotWorkUrl = url.apply(String.format("/post-state/%s/this-should-not-work", username));
        // when try already locked
        val postShouldNotWorkResponse = testRestTemplate.exchange(postShouldNotWorkUrl, HttpMethod.POST, null, postStateResponseType);
        log.info("postShouldNotWorkResponse: {}", postShouldNotWorkResponse);
        // then update was not successful because of lock
        assertThat(postShouldNotWorkResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // and given
        val postUnlockUrl = url.apply(String.format("/post-unlock-by-id/%s", lockId));
        // when releasing a lock
        val postUnlockResponse = testRestTemplate.exchange(postUnlockUrl, HttpMethod.POST, null, postLockResponseType);
        log.info("postUnlockResponse: {}", postUnlockResponse);
        // then lock was released
        assertThat(postUnlockResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // and finally given
        val postShouldWorkUrl = url.apply(String.format("/post-state/%s/this-now-should-work", username));
        // when updating non locked state
        val postShouldWorkResponse = testRestTemplate.exchange(postShouldWorkUrl, HttpMethod.POST, null, postStateResponseType);
        log.info("postShouldWorkResponse: {}", postShouldWorkResponse);
        // then state was successfully updated
        assertThat(postShouldWorkResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        // and body no empty
        val maybeBody = postShouldWorkResponse.getBody();
        assertThat(maybeBody).isPresent();
        // and test: PASSED
        val testPassed = maybeBody.get().stream().map(LastMessage::getContent).anyMatch("this-now-should-work"::equals);
        assertThat(testPassed).isTrue();
    }
}
