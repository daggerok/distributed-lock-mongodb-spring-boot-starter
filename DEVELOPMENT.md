# Development

## CVS

To support different Spring Boot versions it's important to keep track next summary:
* in `io.github.daggerok.distributed.lock.mongodb.example.ExampleApplicationTests` class
  * use `var` for java 17 branch
    ```java
    var someVariable = // ...
    ```
  * use lombok `val` for java 8 branch:
    ```java
    import lombok.val;
    // skipped...
    val someVariable = // ...
    ```
* in `.github/workflows/*.yaml` files:
  * use `strategy.matrix.java: [17, 20]` for java 17 branch
  * use `strategy.matrix.java: [8, 11, 17, 20]` for java 8 branch
* in `io.github.daggerok.distributed.lock.mongodb.autoconfigure.DistributedLockProperties` class
  don't use `@ConstructorBinding` annotation starting from Spring Boot version 2.7.10
* starting from Spring Boot version 2.7.10 instead of using `src/main/resources/META-INF/spring.factories` file
  use `src/main/resource/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` file

## Build and run tests

```bash
./mvnw clean ; ./mvnw -U
```

## Run and test example application

```bash
#brew reinstall httpie jq

killall -9 java
./mvnw -f docker docker:stop
rm -rfv ~/.m2/repository/io/github/daggerok/distributed-lock-mongodb-spring-boot-starter

./mvnw clean ; ./mvnw -DskipTests install
./mvnw -f docker                                                    docker:start
./mvnw -f distributed-lock-mongodb-spring-boot-starter-example spring-boot:start

http -I get :8080/get-state/daggerok
http -I post :8080/post-state/daggerok/initialize-state

id=`http -I post :8080/post-lock/daggerok | jq -r '.id'`
http -I post :8080/post-state/daggerok/this-should-not-work
http -I post :8080/post-unlock-by-id/${id}
http -I post :8080/post-state/daggerok/but-now-this-should-work

./mvnw -f distributed-lock-mongodb-spring-boot-starter-example spring-boot:stop
./mvnw -f docker                                                    docker:stop
```
