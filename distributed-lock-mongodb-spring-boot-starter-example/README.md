# Example application

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
