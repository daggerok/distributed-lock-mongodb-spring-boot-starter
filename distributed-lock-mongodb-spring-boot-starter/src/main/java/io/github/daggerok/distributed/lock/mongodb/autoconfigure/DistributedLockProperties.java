package io.github.daggerok.distributed.lock.mongodb.autoconfigure;

import java.time.Duration;
import lombok.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

@Value
@ConstructorBinding
@ConfigurationProperties("io.github.daggerok.distributed.lock.mongodb")
public class DistributedLockProperties {
    boolean enabled = true;
    Duration lockPeriod = Duration.ofSeconds(15);
}
