package io.github.daggerok.distributed.lock.mongodb;

import lombok.experimental.StandardException;

@StandardException
public class LockException extends RuntimeException {

    public static LockException lockIsRequired() {
        return new LockException("lock is required");
    }

    public static LockException executionIsRequired() {
        return new LockException("execution is required");
    }

    public static LockException runnableIsRequired() {
        return new LockException("runnable is required");
    }

    public static LockException lockIdentifierIsRequired() {
        return new LockException("lock by identifier is required");
    }

    public static LockException lockIdIsRequired() {
        return new LockException("lock ID is required");
    }
}
