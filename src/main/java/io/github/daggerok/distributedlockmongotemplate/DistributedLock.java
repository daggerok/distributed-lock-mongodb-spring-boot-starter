package io.github.daggerok.distributedlockmongotemplate;

import io.github.daggerok.distributedlockmongotemplate.autoconfigure.DistributedLockProperties;
import io.vavr.CheckedFunction0;
import io.vavr.CheckedRunnable;
import io.vavr.control.Try;
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
    private final DistributedLockProperties props;

    /**
     * Acquire lock according to given config and return supplied value from given execution if lock can be acquired.
     * <p>
     * Usage:
     *
     * <pre>
     *     Optional<SyncResult> = distributedLock.acquireAndGet(Lock.of("masterLeaderSync"), () ->
     *         businessService.masterLeaderSync()
     *     );
     * </pre>
     *
     * @param lockConfig - {@link Lock} configuration to be acquired
     * @param execution  - {@link CheckedFunction0} vavr.io checked function supplier for execution if lock will be acquired
     * @return {@link Optional}, which can either containing execution result or will be empty if execution result is null or lock wasn't acquired
     */
    public <T> Optional<T> acquireAndGet(Lock lockConfig, CheckedFunction0<T> execution) {
        Lock lock = Optional.ofNullable(lockConfig).orElseThrow(LockException::lockIsRequired);
        CheckedFunction0<T> anExecution = Optional.ofNullable(execution).orElseThrow(LockException::executionIsRequired);

        long count = countLocks(lock);
        Duration lockPeriod = Optional.ofNullable(lock.lockPeriod).orElseGet(props::getLockPeriod);
        // if no locks available: create it at first time, otherwise try to acquire existing lock
        Optional<Lock> maybeLock = count < 1 ? createNewLock(lock, lockPeriod) : acquireExistingLock(lock, lockPeriod);
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
        Lock lock = Optional.ofNullable(lockConfig).orElseThrow(LockException::lockIsRequired);
        CheckedRunnable aRunnable = Optional.ofNullable(runnable).orElseThrow(LockException::runnableIsRequired);

        long count = countLocks(lock);
        Duration lockPeriod = Optional.ofNullable(lock.lockPeriod).orElseGet(props::getLockPeriod);
        // if no locks available: create it at first time, otherwise try to acquire existing lock
        Optional<Lock> maybeLock = count < 1 ? createNewLock(lock, lockPeriod) : acquireExistingLock(lock, lockPeriod);
        return maybeLock.flatMap(it -> runAndRelease(it.id, aRunnable));
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

    // package-private APIs and helper DRY-code reusable methods

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
                .andFinallyTry(releaseLock(lockId))
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
    <T> Optional<Boolean> runAndRelease(String lockId, CheckedRunnable runnable) {
        return Try.run(runnable)
                .andFinallyTry(releaseLock(lockId))
                .onFailure(throwable -> log.error("Run error: {}", throwable::getMessage))
                .map(unused -> true)
                .recover(throwable -> false)
                .toJavaOptional();
    }

    /**
     * Callback defined lock releasing. Must be used in final block after execution.
     *
     * @param lockId - {@link Lock} entity ID for release
     * @return {@link CheckedRunnable} callback
     */
    private CheckedRunnable releaseLock(String lockId) {
        return () -> mongoTemplate.update(Lock.class)
                .matching(Criteria.where("id").is(lockId))
                .apply(Update.update("state", Lock.State.NONE).set("lastModifiedAt", Instant.now()))
                .findAndModify()
                .ifPresent(it -> {
                    Lock released = mongoTemplate.query(Lock.class).matching(Criteria.where("id").is(it.id)).oneValue();
                    log.debug("Lock finally released: {}", released);
                });
    }
}
