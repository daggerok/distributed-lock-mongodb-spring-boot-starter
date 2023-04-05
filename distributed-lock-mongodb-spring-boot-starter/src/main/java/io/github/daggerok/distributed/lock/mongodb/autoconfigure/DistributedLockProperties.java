package io.github.daggerok.distributed.lock.mongodb.autoconfigure;

import java.time.Duration;
import lombok.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@Value
@ConfigurationProperties("io.github.daggerok.distributed.lock.mongodb")
public class DistributedLockProperties {

    Boolean enabled;
    Duration lockPeriod;

    public DistributedLockProperties(@DefaultValue("true") Boolean enabled,
                                     @DefaultValue("15000ms") Duration lockPeriod) {
        this.enabled = enabled;
        this.lockPeriod = lockPeriod;
    }
}
