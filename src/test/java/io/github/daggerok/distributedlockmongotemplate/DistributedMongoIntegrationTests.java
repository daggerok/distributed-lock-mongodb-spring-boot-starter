package io.github.daggerok.distributedlockmongotemplate;

import com.mongodb.client.MongoClient;
import io.github.daggerok.distributedlockmongotemplate.autoconfigure.DistributedLockProperties;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mongo.MongoProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@Log4j2
@Testcontainers
@AllArgsConstructor(onConstructor_ = @Autowired)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DistributedMongoIntegrationTests {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:4.4.2")
            .withExposedPorts(MongoProperties.DEFAULT_PORT)
            .waitingFor(new HostPortWaitStrategy())
            .withAccessToHost(true);

    MongoClient mongoClient;
    MongoTemplate mongoTemplate;
    DistributedLock distributedLock;
    DistributedLockProperties props;

    @DynamicPropertySource
    static void setupSpringBootProperties(DynamicPropertyRegistry dynamicPropertyRegistry) {
        log.info("Setting up spring.data.mongodb={}", mongoDBContainer::getReplicaSetUrl);
        dynamicPropertyRegistry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @BeforeEach
    void before_each() {
        if (mongoTemplate.collectionExists(Lock.class)) {
            mongoTemplate.remove(Lock.class);
        }
    }

    @Test
    void should_not_acquire_and_get_on_missing_arguments() {
        // when lock is null
        assertThatThrownBy(() -> distributedLock.acquireAndGet(null, null))
                // then
                .isInstanceOf(LockException.class)
                .hasMessage("lock is required");

        // and when execution is null
        assertThatThrownBy(() -> distributedLock.acquireAndGet(Lock.of("should_not_acquire_and_get_on_missing_arguments"), null))
                // then
                .isInstanceOf(LockException.class)
                .hasMessage("execution is required");

        // when lock identity is null
        assertThatThrownBy(() -> distributedLock.acquireAndGet(Lock.of(), null))
                // then
                .isInstanceOf(LockException.class)
                .hasMessage("lock identity is required");

        // when lock identifiers vararg array is null
        assertThatThrownBy(() -> distributedLock.acquireAndGet(Lock.of(null), null))
                // then
                .isInstanceOf(LockException.class)
                .hasMessage("lock identity is required");

        // when lock period and identifiers vararg array are nulls
        assertThatThrownBy(() -> distributedLock.acquireAndGet(Lock.of(null, null), null))
                // then
                .isInstanceOf(LockException.class)
                .hasMessage("lock identity is required");
    }

    @Test
    void should_not_acquire_not_expired_locked_get() {
        // given
        Lock lockedConfig = Lock.of("should_not_acquire_not_expired_locked_get")
                .withLockedAt(Instant.now().minusSeconds(5))
                .withLastModifiedAt(Instant.now())
                .withState(Lock.State.LOCKED);
        Lock locked = mongoTemplate.insert(lockedConfig);
        log.info("now is: {} locked: {}", Instant.now(), locked);

        // and
        AtomicBoolean successfulLock = new AtomicBoolean(false);

        // when
        Optional<Boolean> result = distributedLock.acquireAndGet(
                Lock.of("should_not_acquire_not_expired_locked_get"), () -> successfulLock.getAndSet(true)
        );

        // then
        assertThat(successfulLock.get()).isFalse();
        assertThat(result).isNotPresent();
    }

    @Test
    void should_acquire_unlocked_get() {
        // given unlocked
        Lock unlockedConfig = Lock.of("should_acquire_unlocked_get")
                .withLockedAt(Instant.now().minusSeconds(5))
                .withLastModifiedAt(Instant.now())
                .withState(Lock.State.NONE);
        Lock unlocked = mongoTemplate.insert(unlockedConfig);
        log.info("now is: {} unlocked: {}", Instant.now(), unlocked);

        // and
        AtomicBoolean successfulLock = new AtomicBoolean(false);

        // when
        Optional<Boolean> result = distributedLock.acquireAndGet(
                Lock.of("should_acquire_unlocked_get"), () -> successfulLock.getAndSet(true)
        );

        // then
        assertThat(successfulLock.get()).isEqualTo(true);
        assertThat(result).isPresent();
    }

    @Test
    void should_acquire_expired_lock_and_get() {
        // given expired lock
        Instant now = Instant.now();
        Instant lockedAt = now.minusNanos(props.getLockPeriod().toNanos());
        Lock expired = Lock.of("should_acquire_expired_lock_and_get")
                .withState(Lock.State.LOCKED).withLockedAt(lockedAt).withLastModifiedAt(lockedAt);
        Lock existingExpiredLock = mongoTemplate.insert(expired);
        log.info("now is: {} existingExpiredLock: {}", Instant.now(), existingExpiredLock);

        // and
        AtomicBoolean successfulLock = new AtomicBoolean(false);

        // when
        val result = distributedLock.acquireAndGet(
                Lock.of("should_acquire_expired_lock_and_get"),
                () -> successfulLock.getAndSet(true)
        );

        // then
        assertThat(successfulLock.get()).isTrue();
        assertThat(result).isPresent();
    }

    @Test
    void should_create_first_time_lock_and_get() {
        // given
        AtomicBoolean successfulLock = new AtomicBoolean(false);

        // when
        Optional<Boolean> result = distributedLock.acquireAndGet(
                Lock.of("should_create_first_time_lock_and_get"), () -> successfulLock.getAndSet(true)
        );

        // then
        assertThat(successfulLock.get()).isTrue();
        assertThat(result).isPresent();
    }

    @Test
    void should_not_acquire_and_run_on_missing_arguments() {
        // when lock is null
        assertThatThrownBy(() -> distributedLock.acquireAndRun(null, null))
                // then
                .isInstanceOf(LockException.class)
                .hasMessage("lock is required");

        // and when execution is null
        assertThatThrownBy(() -> distributedLock.acquireAndRun(Lock.of("should_not_acquire_and_run_on_missing_arguments"), null))
                // then
                .isInstanceOf(LockException.class)
                .hasMessage("runnable is required");

        // when lock identity is null
        assertThatThrownBy(() -> distributedLock.acquireAndRun(Lock.of(), null))
                // then
                .isInstanceOf(LockException.class)
                .hasMessage("lock identity is required");

        // when lock identifiers vararg array is null
        assertThatThrownBy(() -> distributedLock.acquireAndRun(Lock.of(null), null))
                // then
                .isInstanceOf(LockException.class)
                .hasMessage("lock identity is required");

        // when lock period and identifiers vararg array are nulls
        assertThatThrownBy(() -> distributedLock.acquireAndRun(Lock.of(null, null), null))
                // then
                .isInstanceOf(LockException.class)
                .hasMessage("lock identity is required");
    }

    @Test
    void should_not_acquire_not_expired_locked_run() {
        // given
        Lock lockedConfig = Lock.of("should_not_acquire_not_expired_locked_run")
                .withLockedAt(Instant.now().minusSeconds(5))
                .withLastModifiedAt(Instant.now())
                .withState(Lock.State.LOCKED);
        Lock locked = mongoTemplate.insert(lockedConfig);
        log.info("now is: {} locked: {}", Instant.now(), locked);

        // and
        AtomicBoolean successfulLock = new AtomicBoolean(false);

        // when
        Optional<Boolean> result = distributedLock.acquireAndRun(
                Lock.of("should_not_acquire_not_expired_locked_run"), () -> successfulLock.set(true)
        );

        // then
        assertThat(successfulLock.get()).isFalse();
        assertThat(result).isNotPresent();
    }

    @Test
    void should_acquire_unlocked_run() {
        // given unlocked
        Lock unlockedConfig = Lock.of("should_acquire_unlocked_run")
                .withLockedAt(Instant.now().minusSeconds(5))
                .withLastModifiedAt(Instant.now())
                .withState(Lock.State.NONE);
        Lock unlocked = mongoTemplate.insert(unlockedConfig);
        log.info("now is: {} unlocked: {}", Instant.now(), unlocked);

        // and
        AtomicBoolean successfulLock = new AtomicBoolean(false);

        // when
        Optional<Boolean> result = distributedLock.acquireAndRun(
                Lock.of("should_acquire_unlocked_run"), () -> successfulLock.set(true)
        );

        // then
        assertThat(successfulLock.get()).isEqualTo(true);

        // and
        assertThat(result).isPresent();
        result.ifPresent(successfulAcquireAndRun -> assertThat(successfulAcquireAndRun).isTrue());
    }

    @Test
    void should_acquire_expired_lock_and_run() {
        // given expired lock
        Instant now = Instant.now();
        Instant lockedAt = now.minusNanos(props.getLockPeriod().toNanos());
        Lock expired = Lock.of("should_acquire_expired_lock_and_run").withState(Lock.State.LOCKED)
                .withLockedAt(lockedAt).withLastModifiedAt(lockedAt);
        Lock existingExpiredLock = mongoTemplate.insert(expired);
        log.info("now is: {} existingExpiredLock: {}", Instant.now(), existingExpiredLock);

        // and
        AtomicBoolean successfulLock = new AtomicBoolean(false);

        // when
        Optional<Boolean> result = distributedLock.acquireAndRun(
                Lock.of("should_acquire_expired_lock_and_run"), () -> successfulLock.set(true)
        );

        // then
        assertThat(successfulLock.get()).isEqualTo(true);

        // and
        assertThat(result).isPresent();
        result.ifPresent(successfulAcquireAndRun -> assertThat(successfulAcquireAndRun).isTrue());
    }

    @Test
    void should_create_first_time_lock_and_run() {
        // given
        AtomicBoolean successfulLock = new AtomicBoolean(false);

        // when
        Optional<Boolean> result = distributedLock.acquireAndRun(
                Lock.of("should_create_first_time_lock_and_run"), () -> successfulLock.set(true)
        );

        // then
        assertThat(successfulLock.get()).isTrue();

        // and
        assertThat(result).isPresent();
        result.ifPresent(successfulAcquireAndRun -> assertThat(successfulAcquireAndRun).isTrue());
    }

    @SpringBootApplication
    static class SpringBootTestApplication {
    }
}
