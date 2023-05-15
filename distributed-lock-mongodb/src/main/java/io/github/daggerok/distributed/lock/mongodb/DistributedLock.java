package io.github.daggerok.distributed.lock.mongodb;

import io.vavr.CheckedFunction0;
import io.vavr.CheckedRunnable;
import io.vavr.control.Try;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ExecutableFindOperation;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Log4j2
@RequiredArgsConstructor
public class DistributedLock {

    private static final Function<Lock, Criteria> lockedBy = lock -> Criteria.where("lockedBy").is(lock.lockedBy);

    private final Duration defaultLockPeriod;
    private final MongoTemplate mongoTemplate;

    /**
     * Try to acquire a lock according to given config.
     * <p>
     * Usage:
     * <pre>
     *     var maybeLock = distributedLock.acquire(Lock.of(identifier));
     *
     *     return maybeLock.isPresent() ? "Lock was acquired." : "Lock can't be acquired.";
     * </pre>
     *
     * @param lockConfig - {@link Lock} configuration to be acquired
     * @return {@link Optional} of type {@link Lock} which is going to be containing instance of persisted {@link Lock}
     * in case if lock was acquired or empty otherwise
     * @see DistributedLock#acquire(Serializable[])
     * @see DistributedLock#acquire(Duration, Serializable[])
     */
    public Optional<Lock> acquire(Lock lockConfig) {
        Lock lock = Optional.ofNullable(lockConfig).orElseThrow(LockException::lockIsRequired);
        Optional<Lock> maybePrevious = tryLock(lock);
        return queryCurrent(maybePrevious);
    }

    /**
     * Try to acquire a lock according to given config.
     * <p>
     * Usage:
     * <pre>
     *     var maybeLock = distributedLock.acquire(Lock.of(identifier));
     *
     *     return maybeLock.isPresent() ? "Lock was acquired." : "Lock can't be acquired.";
     * </pre>
     *
     * @param identifiers - {@link Lock#lockedBy} identifiers to be acquired
     * @return {@link Optional} of type {@link Lock} which is going to be containing instance of persisted {@link Lock}
     * in case if lock was acquired or empty otherwise
     * @see DistributedLock#acquire(Lock)
     * @see DistributedLock#acquire(Duration, Serializable[])
     */
    @SafeVarargs
    public final <T extends Serializable> Optional<Lock> acquire(T... identifiers) {
        return acquire(Lock.of(identifiers));
    }

    /**
     * Try to acquire a lock according to given config.
     * <p>
     * Usage:
     * <pre>
     *     var maybeLock = distributedLock.acquire(Lock.of(identifier));
     *
     *     return maybeLock.isPresent() ? "Lock was acquired." : "Lock can't be acquired.";
     * </pre>
     *
     * @param lockPeriod  - how long lock is going to be acquired
     * @param identifiers - {@link Lock#lockedBy} identifiers to be acquired
     * @return {@link Optional} of type {@link Lock} which is going to be containing instance of persisted {@link Lock}
     * in case if lock was acquired or empty otherwise
     * @see DistributedLock#tryLock(Lock)
     * @see DistributedLock#acquire(Serializable[])
     */
    @SafeVarargs
    public final <T extends Serializable> Optional<Lock> acquire(Duration lockPeriod, T... identifiers) {
        return acquire(Lock.of(lockPeriod, identifiers));
    }

    /**
     * Try to acquire a lock according to given config.
     * <p>
     * Usage:
     * <pre>
     *     var maybeLock = distributedLock.acquire(Lock.of(identifier));
     *
     *     return maybeLock.isPresent() ? "Lock was acquired." : "Lock can't be acquired.";
     * </pre>
     *
     * @param description - lock description, comment or note, can be used as information to additionally identify who
     *                    was acquired certain lock
     * @param lockPeriod  - how long lock is going to be acquired
     * @param identifiers - {@link Lock#lockedBy} identifiers to be acquired
     * @return {@link Optional} of type {@link Lock} which is going to be containing instance of persisted {@link Lock}
     * in case if lock was acquired or empty otherwise
     * @see DistributedLock#tryLock(Lock)
     * @see DistributedLock#acquire(Serializable[])
     */
    @SafeVarargs
    public final <T extends Serializable> Optional<Lock> acquire(String description, Duration lockPeriod, T... identifiers) {
        return acquire(Lock.of(description, lockPeriod, identifiers));
    }

    /**
     * Acquire lock according to given config and return supplied value from given execution if lock can be acquired.
     * <p>
     * Usage:
     *
     * <pre>
     *     Optional<SyncResult> maybeResult = distributedLock.acquireAndGet(Lock.of("masterLeaderSync"), () ->
     *         businessService.masterLeaderSync()
     *     );
     *
     *     maybeResult.ifPresent(result -> log.info("Synchronization complete: {}", result));
     * </pre>
     *
     * @param lockConfig - {@link Lock} configuration to be acquired
     * @param execution  - {@link CheckedFunction0} vavr.io checked function supplier for execution if lock will be acquired
     * @return {@link Optional}, which can either containing execution result or will be empty if execution result is null or lock wasn't acquired
     */
    public <T> Optional<T> acquireAndGet(Lock lockConfig, CheckedFunction0<T> execution) {
        CheckedFunction0<T> anExecution = Optional.ofNullable(execution).orElseThrow(LockException::executionIsRequired);
        return acquire(lockConfig).flatMap(acquired -> executeAndRelease(acquired, anExecution));
    }

    /**
     * Acquire lock according to given config and consume given execution if lock can be acquired.
     * <p>
     * Usage:
     *
     * <pre>
     *     Optional<Boolean> maybeSync = distributedLock.acquireAndGet(Lock.of("masterLeaderSync"), () ->
     *         businessService.masterLeaderSync()
     *     );
     * </pre>
     *
     * @param lockConfig - {@link Lock} configuration to be acquired
     * @param runnable   - {@link CheckedRunnable} vavr.io checked runnable consumer function for execution if lock will be acquired
     * @return {@link Optional} of tpye {@link Boolean}
     */
    public <T> Optional<Boolean> acquireAndRun(Lock lockConfig, CheckedRunnable runnable) {
        CheckedRunnable aRunnable = Optional.ofNullable(runnable).orElseThrow(LockException::runnableIsRequired);
        return acquire(lockConfig).flatMap(acquired -> runAndRelease(acquired, aRunnable));
    }

    /**
     * Release lock by ID.
     * <p>
     * Usage:
     * <pre>
     *     Optional<Lock> maybeLock = distributedLock.release("642b52e873d0ec7cd4463f05");
     *
     *     return (maybeLock.isPresent()) ? "Lock was released." : "Lock not found.";
     * </pre>
     *
     * @param lockId - {@link Lock} entity ID to be released
     * @return {@link Optional} of type {@link Lock} if it was successfully released otherwise {@link Optional#empty}
     */
    public Optional<Lock> release(String lockId) {
        String id = Optional.ofNullable(lockId).orElseThrow(LockException::lockIdIsRequired);
        Optional<Lock> maybePrevious = mongoTemplate.update(Lock.class)
                .matching(Query.query(Criteria.where("id").is(id)))
                .apply(Update.update("state", Lock.State.NONE).set("lastModifiedAt", Instant.now()))
                .findAndModify();
        Optional<Lock> maybeReleased = queryCurrent(maybePrevious);
        maybeReleased.ifPresent(it -> log.debug("Lock released: {}", it));
        return maybeReleased;
    }

    /**
     * DRY-code method to query lock state by its ID.
     *
     * @param previous - previous {@link Optional} instance of type {@link Lock} to be used for getting ID
     * @return {@link Optional} of current {@link Lock} state or {@link Optional#empty()} otherwise
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    Optional<Lock> queryCurrent(Optional<Lock> previous) {
        Objects.requireNonNull(previous, "Optional may not be null");
        return previous.map(Lock::getId)
                .map(Criteria.where("id")::is)
                .map(Query::query)
                .map(mongoTemplate.query(Lock.class)::matching)
                .flatMap(ExecutableFindOperation.TerminatingFind::one);
    }

    // package-private APIs and helper DRY-code reusable methods

    /**
     * Helper method to acquire a lock according to given config.
     *
     * @param lock - {@link Lock} configuration to be acquired
     * @return {@link Optional} of type {@link Lock} which is going to be containing instance of persisted {@link Lock}
     * in case if lock was acquired or empty otherwise
     * @see DistributedLock#acquire(Lock)
     * @see DistributedLock#acquire(Serializable[])
     * @see DistributedLock#acquire(Duration, Serializable[])
     */
    Optional<Lock> tryLock(Lock lock) {
        Optional<Lock> maybeExistingLock = findExistingLock(lock);
        return maybeExistingLock.isPresent()
                // if lock is available try to acquire it
                ? maybeExistingLock.flatMap(this::acquireExistingLock)
                // otherwise try to create new lock at first time
                : createNewLock(lock);
    }

    /**
     * Helper method to find first existing lock (released or expired) for given config.
     *
     * @param config - {@link Lock} config
     * @return {@link Optional} of type {@link Lock} for given {@link Lock} config
     */
    Optional<Lock> findExistingLock(Lock config) {
        Optional<Lock> maybeLock = mongoTemplate.query(Lock.class).matching(Query.query(lockedBy.apply(config))).one();

        maybeLock.ifPresent(lock -> {
            boolean isReleased = lock.state == Lock.State.NONE;
            boolean isExpired = lock.state == Lock.State.LOCKED
                    && Objects.nonNull(lock.lockedAt) && Objects.nonNull(lock.getLockPeriod())
                    && Instant.now().isAfter(lock.lastModifiedAt.plusNanos(lock.getLockPeriod().toNanos()));

            if (isReleased) log.debug("Found released lock: {}", lock);
            if (isExpired) log.debug("Found expired lock: {}", lock);
            if (!isReleased && !isExpired) log.debug("Found non expired lock: {}", lock);
        });
        if (!maybeLock.isPresent()) log.debug("Lock not found by: {}", config.lockedBy);

        return maybeLock;
    }

    /**
     * Helper method to acquire existing lock.
     *
     * @param lock - {@link Lock} config
     * @return {@link Optional} with newly created and acquired {@link Lock}
     */
    Optional<Lock> createNewLock(Lock lock) {
        Index indexToEnsure = new Index("lockedBy", Sort.Direction.ASC).named("Lock_lockedBy").unique();
        String index = mongoTemplate.indexOps(Lock.class).ensureIndex(indexToEnsure);
        log.debug("Ensured index {} exists", index);

        Duration lockPeriod = Optional.ofNullable(lock.getLockPeriod()).orElse(defaultLockPeriod);
        log.debug("Trying to create new lock for {} period and {} config", lockPeriod, lock);
        Instant now = Instant.now();
        Lock toAcquire = lock.withState(Lock.State.LOCKED)
                .withLockedAt(now)
                .withLastModifiedAt(now)
                .withLockPeriodDuration(lockPeriod.toString());
        return Try.of(() -> mongoTemplate.insert(toAcquire))
                .onSuccess(acquired -> log.debug("New lock created and acquired: {}", acquired))
                .onFailure(throwable -> log.error("New lock creation error: {}", throwable::getMessage))
                .toJavaOptional();
    }

    /**
     * Helper method to acquire existing lock.
     *
     * @param lock - {@link Lock} config
     * @return {@link Optional} of type {@link Lock} if that can be acquired
     */
    Optional<Lock> acquireExistingLock(Lock lock) {
        Duration lockPeriod = Optional.ofNullable(lock.getLockPeriod()).orElse(defaultLockPeriod);
        log.debug("Trying to acquiring existing lock for {} period and {} config", lockPeriod, lock);
        Criteria id = Criteria.where("id").is(lock.id);
        Criteria version = Criteria.where("version").is(lock.version);
        Criteria lockedBy = DistributedLock.lockedBy.apply(lock);
        Criteria stateNone = Criteria.where("state").is(Lock.State.NONE);
        Criteria released = new Criteria().andOperator(id, lockedBy, version, stateNone);
        Criteria stateLocked = Criteria.where("state").is(Lock.State.LOCKED);
        Instant expiredFrom = Instant.now().minusNanos(lockPeriod.toNanos());
        Criteria lastModifiedAt = Criteria.where("lastModifiedAt").lt(expiredFrom);
        Criteria expired = new Criteria().andOperator(id, lockedBy, version, stateLocked, lastModifiedAt);
        Criteria releasedOrExpired = new Criteria().orOperator(released, expired);
        return Try
                .of(() ->
                        mongoTemplate.update(Lock.class)
                                .matching(Query.query(releasedOrExpired))
                                .apply(Update.update("state", Lock.State.LOCKED).set("lastModifiedAt", Instant.now()))
                                .findAndModify()
                )
                .onSuccess(o -> log.debug(o.map(unused -> "Existing lock acquired").orElse("Wasn't able to acquire existing lock")))
                .onFailure(throwable -> log.error("Error occurred on acquiring of existing lock: {}", throwable::getMessage))
                .getOrElseThrow(throwable -> new LockException(throwable));
    }

    /**
     * A vavr.io {@link Try} to supply execution and release a lock after all.
     *
     * @param lock      - {@link Lock} to be released
     * @param execution - {@link CheckedRunnable} for execution
     * @return {@link Optional} of type {@link Void}
     */
    <T> Optional<T> executeAndRelease(Lock lock, CheckedFunction0<T> execution) {
        return Try.of(execution)
                .andFinallyTry(() -> release(lock.id))
                .onFailure(throwable -> log.error("Execution error: {}", throwable::getMessage))
                .onSuccess(result -> log.debug("Execution result: {}", result))
                .toJavaOptional();
    }

    /**
     * A vavr.io {@link Try} to execute runnable and release a lock after all.
     *
     * @param lock     - {@link Lock} to be released
     * @param runnable - {@link CheckedRunnable} for execution
     * @return {@link Optional} of type {@link Boolean} with true if run was successful and false otherwise
     */
    Optional<Boolean> runAndRelease(Lock lock, CheckedRunnable runnable) {
        return Try.run(runnable)
                .andFinallyTry(() -> release(lock.id))
                .onFailure(throwable -> log.error("Run error: {}", throwable::getMessage))
                .map(unused -> true)
                .recover(throwable -> false)
                .toJavaOptional();
    }
}
