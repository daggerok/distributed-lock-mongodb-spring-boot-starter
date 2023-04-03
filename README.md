# spring-boot-mongo-lock [![tests](https://github.com/daggerok/spring-boot-mongo-lock/actions/workflows/tests.yml/badge.svg)](https://github.com/daggerok/spring-boot-mongo-lock/actions/workflows/tests.yml)

## Example app

```bash
mvn -f distributed-lock-mongodb-docker                      docker:start
mvn -f distributed-lock-mongodb-spring-boot-starter-example spring-boot:start

mvn -f distributed-lock-mongodb-spring-boot-starter-example spring-boot:stop
mvn -f distributed-lock-mongodb-docker                      docker:stop
```

This repository demonstrates how to implement distributed lock with mongo db and mongoTemplate from spring

<!--

# Read Me First
The following was discovered as part of building this project:

* The JVM level was changed from '1.8' to '17', review the [JDK Version Range](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-Versions#jdk-version-range) on the wiki for more details.

# Getting Started

### Reference Documentation
For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/docs/3.0.5/maven-plugin/reference/html/)
* [Create an OCI image](https://docs.spring.io/spring-boot/docs/3.0.5/maven-plugin/reference/html/#build-image)
* [Testcontainers MongoDB Module Reference Guide](https://www.testcontainers.org/modules/databases/mongodb/)
* [Testcontainers](https://www.testcontainers.org/)
* [Thymeleaf](https://docs.spring.io/spring-boot/docs/3.0.5/reference/htmlsingle/#web.servlet.spring-mvc.template-engines)
* [Spring Data MongoDB](https://docs.spring.io/spring-boot/docs/3.0.5/reference/htmlsingle/#data.nosql.mongodb)
* [Handling Form Submission](https://spring.io/guides/gs/handling-form-submission/)
* [Accessing Data with MongoDB](https://spring.io/guides/gs/accessing-data-mongodb/)

-->
