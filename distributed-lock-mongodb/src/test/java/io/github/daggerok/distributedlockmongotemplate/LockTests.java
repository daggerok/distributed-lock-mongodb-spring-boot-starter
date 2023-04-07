package io.github.daggerok.distributedlockmongotemplate;

import io.github.daggerok.distributed.lock.mongodb.Lock;
import io.github.daggerok.distributed.lock.mongodb.LockException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Log4j2
@DisplayName("Lock tests")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LockTests {

    @Test
    void should_not_create_lock_config_because_lock_identity_is_required() {
        // when description, lock period and serializable array has nulls
        assertThatThrownBy(() -> Lock.of((String) /* description: */ null, /* lockPeriod: */ null, /* identifiers: */ null, null))
                // then
                .isInstanceOf(LockException.class)
                .hasMessage("lock by identifier is required");

        // and when all arguments are nulls
        assertThatThrownBy(() -> Lock.of(/* description: */ null, /* lockPeriod: */ null, /* identifiers: */ (Serializable[]) null))
                // then
                .isInstanceOf(LockException.class)
                .hasMessage("lock by identifier is required");

        // and when lockPeriod with identifiers serializable array are nulls
        assertThatThrownBy(() -> Lock.of(/* lockPeriod: */ null, /* identifiers: */ (Serializable[]) null))
                // then
                .isInstanceOf(LockException.class)
                .hasMessage("lock by identifier is required");

        // and when identifiers serializable array is null
        assertThatThrownBy(() -> Lock.of(/* identifiers: */ (Serializable[]) null))
                // then
                .isInstanceOf(LockException.class)
                .hasMessage("lock by identifier is required");

        // and when description with lock period was provided
        assertThatThrownBy(() -> Lock.of("description", Duration.ofSeconds(1)))
                // then
                .isInstanceOf(LockException.class)
                .hasMessage("lock by identifier is required");

        // and when only lock period was provided
        assertThatThrownBy(() -> Lock.of(Duration.ofSeconds(1)))
                // then
                .isInstanceOf(LockException.class)
                .hasMessage("lock by identifier is required");

        // and when no argument was provided
        assertThatThrownBy(Lock::of)
                // then
                .isInstanceOf(LockException.class)
                .hasMessage("lock by identifier is required");
    }

    @Test
    void should_create_lock_config_for_different_types_by_its_toString_representation() {
        // given
        Instant instant = Instant.parse("2023-03-30T18:12:34.567Z");

        // when
        Lock lockConfig = Lock.of(instant, Duration.ofSeconds(1));
        log.info("lockConfig: {}", lockConfig);

        // then
        assertThat(lockConfig.getLockedBy()).containsSequence("2023-03-30T18:12:34.567Z", "-", "PT1S");
    }

    @Test
    void should_create_lock_config_by_lock_period_and_identifiers() {
        // when
        Lock lockConfig = Lock.of(Duration.ofMinutes(15), "value1", 2345L);
        log.info("lockConfig: {}", lockConfig);

        // then
        assertThat(lockConfig.getLockedAt()).isNull();
        assertThat(lockConfig.getLockPeriod()).isEqualTo(Duration.ofMinutes(15));
        assertThat(lockConfig.getLockedBy()).startsWith("value1-2345");
    }

    @Test
    void should_create_lock_config_by_identifiers() {
        // when
        Lock lockConfig = Lock.of("value", 123, BigInteger.valueOf(456789L));
        log.info("lockConfig: {}", lockConfig);

        // then
        assertThat(lockConfig.getLockPeriod()).isNull();
        assertThat(lockConfig.getLockedBy()).containsSequence("value", "-", "123", "-", "456789");
    }

    @Test
    void should_create_lock_config_with_description() {
        // when
        Lock lockConfig = Lock.of("a description", /* lockPeriod: */ null, "1", 23L, new BigDecimal("4.56"), new BigInteger("7890"));
        log.info("lockConfig: {}", lockConfig);

        // then
        assertThat(lockConfig.getDescription()).isEqualTo("a description");
        assertThat(lockConfig.getLockedBy()).isEqualTo("1-23-4.56-7890");
    }
}
