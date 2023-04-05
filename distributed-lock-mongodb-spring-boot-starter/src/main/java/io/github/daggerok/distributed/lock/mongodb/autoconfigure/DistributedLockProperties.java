package io.github.daggerok.distributed.lock.mongodb.autoconfigure;

import java.time.Duration;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties("io.github.daggerok.distributed.lock.mongodb")
public class DistributedLockProperties {
    Boolean enabled = true;
    Duration lockPeriod = Duration.ofSeconds(15);
}
