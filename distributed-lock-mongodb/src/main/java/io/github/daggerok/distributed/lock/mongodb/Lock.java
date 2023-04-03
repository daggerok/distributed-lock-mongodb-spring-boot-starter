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

    Instant lockedAt;

    Instant lastModifiedAt;

    Duration lockPeriod;

    State state;

    @SafeVarargs
    public static <T extends Serializable> Lock of(Duration lockPeriod, T... identifiers) {
        T[] items = Optional.ofNullable(identifiers).orElseThrow(LockException::lockIdentifierIsRequired);

        String lockedBy = Arrays.stream(items).filter(Objects::nonNull).map(Object::toString).collect(Collectors.joining("-"));
        if (lockedBy.isEmpty()) throw LockException.lockIdentifierIsRequired();

        return new Lock(null, null, lockedBy, null, null, lockPeriod, State.NONE);
    }

    @SafeVarargs
    public static <T extends Serializable> Lock of(T... identifiers) {
        return Lock.of(null, identifiers);
    }

    public enum State {
        NONE,
        LOCKED,
    }
}
