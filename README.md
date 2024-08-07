# MongoDB distributed lock [![tests](https://github.com/daggerok/distributed-lock-mongodb-spring-boot-starter/actions/workflows/tests.yml/badge.svg)](https://github.com/daggerok/distributed-lock-mongodb-spring-boot-starter/actions/workflows/tests.yml) [![integration tests](https://github.com/daggerok/distributed-lock-mongodb-spring-boot-starter/actions/workflows/integration-tests.yml/badge.svg)](https://github.com/daggerok/distributed-lock-mongodb-spring-boot-starter/actions/workflows/integration-tests.yml)
A `distributed-lock-mongodb-spring-boot-starter` repository project contains custom written `Distributed Lock` starter
based on `Spring Boot` and `MongoTemplate` with `Testcontainers` integration testing and fabric8 `docker-maven-plugin`
maven module to help run example showcase application uses mongo docker container

[![Maven Central](https://img.shields.io/maven-central/v/io.github.daggerok/distributed-lock-mongodb-spring-boot-starter.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github.daggerok%22%20AND%20a:%22distributed-lock-mongodb-spring-boot-starter%22)

## Versions

| Distributed Lock | Spring Boot    | GitHub branch     |
|------------------|----------------|-------------------|
| 3.3.2            | 3.3.2          | master            |
| 3.2.8            | 3.2.8          | spring-boot-3.2.x |
| 3.1.12           | 3.1.12         | spring-boot-3.1.x |
| 3.0.13           | 3.0.13         | spring-boot-3.0.x |
| 2.7.18           | 2.7.18         | spring-boot-2.7.x |
| 2.6.14-4         | 2.6.14         | spring-boot-2.6.x |
| 2.5.14-4         | 2.5.14         | spring-boot-2.5.x |
| 2.4.13-4         | 2.4.13         | spring-boot-2.4.x |
| 2.3.12-4         | 2.3.12.RELEASE | spring-boot-2.3.x |
| 2.2.13-4         | 2.2.13.RELEASE | spring-boot-2.2.x |
| 2.1.18-4         | 2.1.18.RELEASE | spring-boot-2.1.x |

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
    private DistributedLock distributedLock;

    // skipped...
}
```

You can inject it using `@Auqtowired annotation` by field injection (not recommended):

```java
@Service
public class MyService {

    private final DistributedLock distributedLock;

    public MyService(DistributedLock distributedLock) {
        this.distributedLock = distributedLock;
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

There are Lock object configurations available, but the easier way to acquire lock would be simply using identifier:

```java
distributedLock
        .acquireAndGet("user-123", () -> userService.update(user))
        .ifPresent(unused -> log.debug("User(id=123) has been updated"));
```
