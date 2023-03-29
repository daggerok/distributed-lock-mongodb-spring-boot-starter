package io.github.daggerok.springbootmongolock;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import io.github.daggerok.springbootmongolock.SpringBootDistributedMongoLockTests.Contact.Person;
import java.util.Optional;
import java.util.stream.StreamSupport;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mongo.MongoProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Log4j2
@Testcontainers
@AllArgsConstructor(onConstructor_ = @Autowired)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SpringBootDistributedMongoLockTests {

    MongoClient mongoClient;
    MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        if (mongoTemplate.collectionExists(Person.class)) mongoTemplate.remove(Person.class);
    }

    @Test // https://docs.spring.io/spring-data/mongodb/docs/current/reference/html/#mongo-template.find-and-upsert
    void should_find_and_modify_using_update_matching_apply_and_findAndModifyValue_methods() {
        // given
        mongoTemplate.insert(Person.of("Max", 12));
        mongoTemplate.insert(Person.of("Max", 23));

        // when use atomic operation: find and modify
        Optional<Person> maybeOldPerson = mongoTemplate.update(Person.class)
                .matching(new Query(Criteria.where("name").is("Max").and("age").is(12)))
                .apply(new Update().inc("age", 1))
                .findAndModify();
        log.info("maybeOldPerson: {}", maybeOldPerson);
        assertThat(maybeOldPerson).isPresent();
        maybeOldPerson.ifPresent(oldPerson -> assertThat(oldPerson.age).isEqualTo(12));

        // then
        Optional<Person> maybeUpdatedPerson = mongoTemplate.query(Person.class)
                .matching(new Query(Criteria.where("name").is("Max").and("age").is(13)))
                .first();
        log.info("maybeUpdatedPerson: {}", maybeUpdatedPerson);
        assertThat(maybeUpdatedPerson).isPresent();
        maybeUpdatedPerson.ifPresent(updatedPerson -> assertThat(updatedPerson.age).isEqualTo(13));
    }

    @Test // https://docs.spring.io/spring-data/mongodb/docs/current/reference/html/#mongo-template.find-and-upsert
    void should_find_and_modify_using_findAndModify_method() {
        // given
        mongoTemplate.insert(Person.of("Max", 12));
        mongoTemplate.insert(Person.of("Max", 23));

        // when use atomic operation: find and modify
        Person oldPerson = mongoTemplate.findAndModify(
                new Query(Criteria.where("name").is("Max").and("age").is(12)),
                new Update().inc("age", 1),
                Person.class
        );
        log.info("oldPerson: {}", oldPerson);
        assertThat(oldPerson.age).isEqualTo(12);

        // then
        Person updatedPerson = mongoTemplate.findOne(
                new Query(Criteria.where("name").is("Max").and("age").is(13)),
                Person.class
        );
        log.info("updatedPerson: {}", updatedPerson);
        assertThat(updatedPerson.age).isEqualTo(13);
    }

    @Test
    void should_test_context() {
        log.info("mongo databases: {}", String.join(", ", mongoClient.listDatabaseNames()));

        log.info("mongo collections:");
        StreamSupport
                .stream(
                        mongoClient.listDatabases()
                                .map(it -> it.getString("name"))
                                .map(mongoClient::getDatabase)
                                .map(MongoDatabase::listCollectionNames)
                                .spliterator(), false
                )
                .flatMap(it -> StreamSupport.stream(it.spliterator(), false))
                .forEach(log::info);
    }

    // Spring Boot test application

    @SpringBootApplication
    static class SpringBootTestApplication {

        @TestConfiguration
        static class SpringBootTestApplicationConfig {

        }
    }

    interface Contact {

        @Value(staticConstructor = "of")
        class Person implements Contact {

            String id;
            String name;
            int age;

            @Version Long version;

            static Person of(String name, int age) {
                return Person.of(null, name, age, null);
            }
        }
    }

    // Testcontainers testing infrastructure:

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:4.4.2")
            .withExposedPorts(MongoProperties.DEFAULT_PORT)
            .waitingFor(new HostPortWaitStrategy())
            .withAccessToHost(true);

    @DynamicPropertySource
    static void setupSpringBootProperties(DynamicPropertyRegistry dynamicPropertyRegistry) {
        log.info("Setting up spring.data.mongodb={}", mongoDBContainer::getReplicaSetUrl);
        dynamicPropertyRegistry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }
}
