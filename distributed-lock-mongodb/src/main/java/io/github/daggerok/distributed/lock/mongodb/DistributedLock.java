package io.github.daggerok.distributed.lock.mongodb;

import io.vavr.CheckedFunction0;
import io.vavr.CheckedRunnable;
import io.vavr.control.Try;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@Log4j2
@RequiredArgsConstructor
public class DistributedLock {

    private static final Function<Lock, Criteria> lockedBy = lock -> Criteria.where("lockedBy").is(lock.lockedBy);

    private final MongoTemplate mongoTemplate;
    private final Duration lockPeriod;

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
        return tryLock(lock);
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
     * @param identifiers - {@link Lock#lockedBy} identifiers to be acquired
     * @param lockPeriod  - how long lock is going to be acquired
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
        Optional<Lock> maybeLock = acquire(lockConfig);
        return maybeLock.flatMap(it -> executeAndRelease(it.id, anExecution));
    }

    /**
     * Acquire lock according to given config and consume given execution if lock can be acquired.
     * <p>
     * Usage:
     *
     * <pre>
     *     Optional<Boolean> = distributedLock.acquireAndGet(Lock.of("masterLeaderSync"), () ->
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
        Optional<Lock> maybeLock = acquire(lockConfig);
        return maybeLock.flatMap(it -> runAndRelease(it.id, aRunnable));
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
        Optional<Lock> lock = mongoTemplate.update(Lock.class)
                .matching(Criteria.where("id").is(id))
                .apply(Update.update("state", Lock.State.NONE).set("lastModifiedAt", Instant.now()))
                .findAndModify()
                .flatMap(it -> mongoTemplate.query(Lock.class).matching(Criteria.where("id").is(it.id)).one());
        lock.ifPresent(it -> log.debug("Lock {} released", it));
        return lock;
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
        Duration lockPeriod = Optional.ofNullable(lock.lockPeriod).orElse(this.lockPeriod);
        // if no locks available: create it at first time, otherwise try to acquire existing lock
        return countLocks(lock) < 1 ? createNewLock(lock, lockPeriod) : acquireExistingLock(lock, lockPeriod);
    }

    /**
     * Helper method to count existing locks for given config.
     *
     * @param lock - {@link Lock} config
     * @return number of locks create for given {@link Lock} config
     */
    long countLocks(Lock lock) {
        long count = mongoTemplate.query(Lock.class).matching(lockedBy.apply(lock)).count();
        log.debug("Found: {} for {} criteria", count, lock.lockedBy);
        return count;
    }

    /**
     * Helper method to acquire existing lock.
     *
     * @param lock       - {@link Lock} config
     * @param lockPeriod - {@link Duration} lock period
     * @return {@link Optional} with newly created and acquired {@link Lock}
     */
    Optional<Lock> createNewLock(Lock lock, Duration lockPeriod) {
        Index indexToEnsure = new Index("lockedBy", Sort.Direction.ASC).named("Lock_lockedBy").unique();
        String index = mongoTemplate.indexOps(Lock.class).ensureIndex(indexToEnsure);
        log.debug("Ensured index {} exists", index);

        Instant now = Instant.now();
        Lock toAcquire = lock.withLockPeriod(lockPeriod).withState(Lock.State.LOCKED).withLockedAt(now).withLastModifiedAt(now);
        Lock acquired = mongoTemplate.insert(toAcquire);
        log.debug("Acquired: {}", acquired);

        return Optional.of(acquired);
    }

    /**
     * Helper method to acquire existing lock.
     *
     * @param lock       - {@link Lock} config
     * @param lockPeriod - {@link Duration} lock period
     * @return {@link Optional} of type {@link Lock} if that can be acquired
     */
    Optional<Lock> acquireExistingLock(Lock lock, Duration lockPeriod) {
        Criteria stateNone = Criteria.where("state").is(Lock.State.NONE);
        Criteria lockedAt = Criteria.where("lockedAt").lt(Instant.now().minusNanos(lockPeriod.toNanos()));
        Criteria noneFindCriteria = new Criteria().andOperator(lockedBy.apply(lock), stateNone);
        Criteria lockedFindCriteria = new Criteria().andOperator(lockedBy.apply(lock), lockedAt);
        Criteria findCriteria = new Criteria().orOperator(noneFindCriteria, lockedFindCriteria);
        Update modifyUpdate = Update.update("state", Lock.State.LOCKED).set("lastModifiedAt", Instant.now());
        return mongoTemplate.update(Lock.class).matching(findCriteria).apply(modifyUpdate).findAndModify();
    }

    /**
     * A vavr.io {@link Try} to supply execution and release a lock after all.
     *
     * @param lockId    - {@link Lock} entity ID for release
     * @param execution - {@link CheckedRunnable} for execution
     * @return {@link Optional} of type {@link Void}
     */
    <T> Optional<T> executeAndRelease(String lockId, CheckedFunction0<T> execution) {
        return Try.of(execution)
                .andFinallyTry(() -> release(lockId))
                .onFailure(throwable -> log.error("Execution error: {}", throwable::getMessage))
                .onSuccess(result -> log.debug("Execution result: {}", result))
                .toJavaOptional();
    }

    /**
     * A vavr.io {@link Try} to execute runnable and release a lock after all.
     *
     * @param lockId   - {@link Lock} entity ID for release
     * @param runnable - {@link CheckedRunnable} for execution
     * @return {@link Optional} of type {@link Boolean} with true if run was successful and false otherwise
     */
    Optional<Boolean> runAndRelease(String lockId, CheckedRunnable runnable) {
        return Try.run(runnable)
                .andFinallyTry(() -> release(lockId))
                .onFailure(throwable -> log.error("Run error: {}", throwable::getMessage))
                .map(unused -> true)
                .recover(throwable -> false)
                .toJavaOptional();
    }
}
