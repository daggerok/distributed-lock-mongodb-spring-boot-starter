package io.github.daggerok.distributed.lock.mongodb.example;

import io.github.daggerok.distributed.lock.mongodb.DistributedLock;
import io.github.daggerok.distributed.lock.mongodb.Lock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class ExampleApplication {
    public static void main(String[] args) {
        SpringApplication.run(ExampleApplication.class, args);
    }
}

@Value
@Document
class LastMessage {

    @Id
    String id;

    Instant lastModifiedAt;

    @Version
    Long version;

    @Indexed(unique = true)
    String fromUser;

    String content;

    public static LastMessage of(String user) {
        return new LastMessage(null, null, null, null, user);
    }

    public static LastMessage of(String fromUser, String content) {
        return new LastMessage(null, null, null, fromUser, content);
    }
}

@Log4j2
@RestController
@RequiredArgsConstructor
class ExampleResource {

    private final MongoTemplate mongoTemplate;
    private final DistributedLock distributedLock;

    @PostMapping("/post-lock/{username}")
    ResponseEntity<Optional<Lock>> tryLock(@PathVariable String username) {
        Optional<Lock> maybeLock = distributedLock.acquire(username);
        return toResponseEntity(maybeLock);
    }

    @PostMapping("/post-unlock-by-id/{lockId}")
    ResponseEntity<Optional<Lock>> tryUnlock(@PathVariable String lockId) {
        Optional<Lock> maybeReleasedLock = distributedLock.release(lockId);
        return toResponseEntity(maybeReleasedLock);
    }

    @GetMapping("/get-state/{username}")
    ResponseEntity<Optional<LastMessage>> getLastMessage(@PathVariable String username) {
        Optional<LastMessage> maybeLastMessage = mongoTemplate.query(LastMessage.class)
                .matching(Criteria.where("fromUser").is(username))
                .one();
        return toResponseEntity(maybeLastMessage);
    }

    @PostMapping("/post-state/{username}/{content}")
    ResponseEntity<Optional<List<LastMessage>>> tryLockAndUpdate(@PathVariable String username, @PathVariable String content) {
        Optional<List<LastMessage>> maybeLastMessages = distributedLock.acquireAndGet(Lock.of(username), () -> {
            mongoTemplate.update(LastMessage.class)
                    .matching(Criteria.where("fromUser").is(username))
                    .apply(Update.update("content", content).set("lastModifiedAt", Instant.now()))
                    .withOptions(FindAndModifyOptions.options().upsert(true))
                    .findAndModify()
                    .ifPresent(prev -> log.debug("Updating {}", prev));
            return mongoTemplate.query(LastMessage.class)
                    .matching(Criteria.where("fromUser").is(username))
                    .all();
        });
        return toResponseEntity(maybeLastMessages);
    }

    private <T> ResponseEntity<Optional<T>> toResponseEntity(Optional<T> optional) {
        return optional.isPresent() ? ResponseEntity.ok(optional) : ResponseEntity.noContent().build();
    }
}
