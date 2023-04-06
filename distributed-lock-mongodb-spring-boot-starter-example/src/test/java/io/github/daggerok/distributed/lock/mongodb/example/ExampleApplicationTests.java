package io.github.daggerok.distributed.lock.mongodb.example;

import io.github.daggerok.distributed.lock.mongodb.AbstractTestcontainersTests;
import io.github.daggerok.distributed.lock.mongodb.Lock;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@Log4j2
@DisplayName("ExampleApplication tests")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExampleApplicationTests extends AbstractTestcontainersTests {

    @LocalServerPort
    Integer port;

    @Autowired
    TestRestTemplate testRestTemplate;

    @Autowired
    MongoTemplate mongoTemplate;

    Function<String, String> url = path -> {
        Objects.requireNonNull(path, "path may not be null");
        var aPath = path.startsWith("/") ? path : String.format("/%s", path);
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
        var username = UUID.randomUUID().toString();
        var getStateUrl = url.apply(String.format("/get-state/%s", username));
        // when get current state
        var getStateResponseType = new ParameterizedTypeReference<Optional<LastMessage>>() {};
        var getStateResponse = testRestTemplate.exchange(URI.create(getStateUrl), HttpMethod.GET, null, getStateResponseType);
        log.info("getStateResponse: {}", getStateResponse);
        // then there is no such state for given user
        assertThat(getStateResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // and given
        var postInitialStateUrl = url.apply(String.format("/post-state/%s/initialize-state", username));
        // when initialize state
        var postStateResponseType = new ParameterizedTypeReference<Optional<List<LastMessage>>>() {};
        var postInitialStateResponse = testRestTemplate.exchange(postInitialStateUrl, HttpMethod.POST, null, postStateResponseType);
        log.info("postInitialStateResponse: {}", postInitialStateResponse);
        // then user initial state was posted successfully
        assertThat(postInitialStateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // and given
        var postLockUrl = url.apply(String.format("/post-lock/%s", username));
        // when posting lock
        var postLockResponseType = new ParameterizedTypeReference<Optional<Lock>>() {};
        var postLockResponse = testRestTemplate.exchange(postLockUrl, HttpMethod.POST, null, postLockResponseType);
        log.info("postLockResponse: {}", postLockResponse);
        // then lock was successful
        assertThat(postLockResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Optional<Lock> maybeLock = postLockResponse.getBody();
        assertThat(maybeLock).isPresent();
        // and lock ID was kept
        var lockId = maybeLock.get().getId();
        assertThat(lockId).isNotNull().isNotBlank();

        // and given
        var postShouldNotWorkUrl = url.apply(String.format("/post-state/%s/this-should-not-work", username));
        // when try already locked
        var postShouldNotWorkResponse = testRestTemplate.exchange(postShouldNotWorkUrl, HttpMethod.POST, null, postStateResponseType);
        log.info("postShouldNotWorkResponse: {}", postShouldNotWorkResponse);
        // then update was not successful because of lock
        assertThat(postShouldNotWorkResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // and given
        var postUnlockUrl = url.apply(String.format("/post-unlock-by-id/%s", lockId));
        // when releasing a lock
        var postUnlockResponse = testRestTemplate.exchange(postUnlockUrl, HttpMethod.POST, null, postLockResponseType);
        log.info("postUnlockResponse: {}", postUnlockResponse);
        // then lock was released
        assertThat(postUnlockResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // and finally given
        var postShouldWorkUrl = url.apply(String.format("/post-state/%s/this-now-should-work", username));
        // when updating non locked state
        var postShouldWorkResponse = testRestTemplate.exchange(postShouldWorkUrl, HttpMethod.POST, null, postStateResponseType);
        log.info("postShouldWorkResponse: {}", postShouldWorkResponse);
        // then state was successfully updated
        assertThat(postShouldWorkResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        // and body no empty
        var maybeBody = postShouldWorkResponse.getBody();
        assertThat(maybeBody).isPresent();
        // and test: PASSED
        var testPassed = maybeBody.get().stream().map(LastMessage::getContent).anyMatch("this-now-should-work"::equals);
        assertThat(testPassed).isTrue();
    }
}
