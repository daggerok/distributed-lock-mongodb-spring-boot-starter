# MongoDB distributed lock [![tests](https://github.com/daggerok/distributed-lock-mongodb-spring-boot-starter/actions/workflows/tests.yml/badge.svg)](https://github.com/daggerok/distributed-lock-mongodb-spring-boot-starter/actions/workflows/tests.yml) [![integration tests](https://github.com/daggerok/distributed-lock-mongodb-spring-boot-starter/actions/workflows/integration-tests.yml/badge.svg)](https://github.com/daggerok/distributed-lock-mongodb-spring-boot-starter/actions/workflows/integration-tests.yml)
A `distributed-lock-mongodb-spring-boot-starter` repository project contains custom written `Distributed Lock` starter
based on `Spring Boot` and `MongoTemplate` with `Testcontainers` integration testing and fabric8 `docker-maven-plugin`
maven module to help run example showcase application uses mongo docker container

[![Maven Central](https://img.shields.io/maven-central/v/io.github.daggerok/distributed-lock-mongodb-spring-boot-starter.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github.daggerok%22%20AND%20a:%22distributed-lock-mongodb-spring-boot-starter%22)

## Installation

```xml
<dependency>
    <groupId>io.github.daggerok</groupId>
    <artifactId>distributed-lock-mongodb-spring-boot-starter</artifactId>
    <version>RELEASE</version>
</dependency>
```

Or:

```xml

<dependency>
    <groupId>io.github.daggerok</groupId>
    <artifactId>distributed-lock-mongodb-spring-boot-starter</artifactId>
    <version>LATEST</version>
</dependency>
```

...if you need SNAPSHOT

## Supported operations

* Acquire lock (try lock)
* Release lock (try to unlock)
* Acquire lock and consume (only if lock was acquired)
* Acquire lock and supply value (only if lock was acquired)

## Usage

### Inject distributed lock

A `DistributedLockAutoConfiguration` will provide `DistributedLock` you can use as locking implementation.

Feel free to simply inject it by defined single constructor in your spring Bean:

```java
@Service
public class MyService {

    @Autowired
    private MyService myService;

    // skipped...
}
```

Or you can inject it using `@Auqtowired annotation` by field injection (not recommended):

```java
@Service
public class MyService {

    private final MyService myService;

    public MyService(MyService myService) {
        this.myService = myService;
    }

    // skipped...
}
```

Or query it from `ApplicationContext` directly:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        var context = SpringApplication.run(ExampleApplication.class, args);
        var distributedLock = context.getBean(DistributedLock.class);
        // skipped...
    }
}
```

### acquire(Lock config)

Acquire lock (try lock)

```java
Optional<Lock> maybeLock = distributedLock.acquire(Lock.of(identifier));
        if (maybeLock.isPresent()) log.debug("Lock was acquired.");
        else log.warn("Lock can't be acquired...");
```

### release(String lockId)

Release lock (try to unlock)

```java
Optional<Lock> maybeUnlock = distributedLock.release("642b52e873d0ec7cd4463f05")
if (maybeUnlock.isPresent()) log.debug("Lock was released.");
else log.warn("Can't release lock...");
```

### acquireAndRun

Acquire lock and consume (only if lock was acquired)

```java
Optional<Boolean> maybeSync = distributedLock.acquireAndGet(Lock.of("sync"), () -> leaderElection.sync());
if (maybeSync.isPresent()) log.debug("Data synchronization was completed")
else log.warn("Data wasn't synced");
```

### acquireAndGet

Acquire lock and supply value (only if lock was acquired)

```java
Optional<SyncResult> syncResult = distributedLock.acquireAndGet(Lock.of("ETL"), () -> syncService.etl());
maybeResult.ifPresent(result -> log.debug("Synchronization complete with: {}", result));
```
