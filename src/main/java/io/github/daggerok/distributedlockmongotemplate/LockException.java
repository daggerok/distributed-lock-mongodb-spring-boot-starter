package io.github.daggerok.distributedlockmongotemplate;

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

    public static LockException lockIdentityIsRequired() {
        return new LockException("lock identity is required");
    }
}
