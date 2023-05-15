package io.github.daggerok.distributed.lock.mongodb;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.With;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@With
@Data
@Document
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PACKAGE)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class Lock {

    @Id
    String id;

    @Version
    Long version;

    @Indexed(unique = true)
    String lockedBy;

    String description;

    Instant lockedAt;

    Instant lastModifiedAt;

    String lockPeriodDuration;

    State state;

    /**
     * Creates a lock configuration by its identifier and optionals description and lock period.
     * <p>
     * Use description as a comment for information to additionally identify who was acquired a specific lock.
     * <p>
     * Use lock period to override defaults. Lock period is going to be use in addition to {@link State}
     * to detect if it was expired in case if server was shutdown before acquired lock release.
     * <p>
     * All non-nullable identifiers will be represented with {@link Object#toString()} and joined with dash separator.
     *
     * @param description - optional description, for example lockedBy IP address
     * @param lockPeriod  - optional lock period, in null default lock period is going to be used instead
     * @param identifiers - identifier, at lease one non nullable is required
     * @return {@link Lock} configuration
     */
    @SafeVarargs
    public static <T extends Serializable> Lock of(String description, Duration lockPeriod, T... identifiers) {
        T[] items = Optional.ofNullable(identifiers).orElseThrow(LockException::lockIdentifierIsRequired);

        String lockedBy = Arrays.stream(items).filter(Objects::nonNull).map(Object::toString).collect(Collectors.joining("-"));
        if (lockedBy.isEmpty()) throw LockException.lockIdentifierIsRequired();

        String lockPeriodDuration = Optional.ofNullable(lockPeriod).map(Duration::toString).orElse(null);
        return new Lock(null, null, lockedBy, description, null, null, lockPeriodDuration, State.NONE);
    }

    /**
     * Creates a lock configuration by its identifier and optional lock period.
     * <p>
     * Use lock period to override defaults. Lock period is going to be use in addition to {@link State}
     * to detect if it was expired in case if server was shutdown before acquired lock release.
     * <p>
     * All non-nullable identifiers will be represented with {@link Object#toString()} and joined with dash separator.
     *
     * @param lockPeriod  - optional lock period, in null default lock period is going to be used instead
     * @param identifiers - identifier, at lease one non nullable is required
     * @return {@link Lock} configuration
     */
    @SafeVarargs
    public static <T extends Serializable> Lock of(Duration lockPeriod, T... identifiers) {
        return Lock.of(null, lockPeriod, identifiers);
    }

    /**
     * Creates a lock configuration by its identifier.
     * <p>
     * All non-nullable identifiers will be represented with {@link Object#toString()} and joined with dash separator.
     *
     * @param identifiers - at lease one non-nullable identifier is required
     * @return {@link Lock} configuration
     */
    @SafeVarargs
    public static <T extends Serializable> Lock of(T... identifiers) {
        return Lock.of(null, identifiers);
    }

    public Duration getLockPeriod() {
        return Optional.ofNullable(lockPeriodDuration).map(Duration::parse).orElse(null);
    }

    public enum State {
        NONE,
        LOCKED,
    }
}
