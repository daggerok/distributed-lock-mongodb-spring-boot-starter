package io.github.daggerok.distributed.lock.mongodb.autoconfigure;

import io.github.daggerok.distributed.lock.mongodb.DistributedLock;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

@ConditionalOnProperty(
        prefix = "io.github.daggerok.distributed.lock.mongodb",
        name = "enabled", havingValue = "true", matchIfMissing = true
)
@Log4j2
@Configuration
@ConditionalOnClass(DistributedLock.class)
@ComponentScan(basePackageClasses = DistributedLock.class)
@AutoConfiguration(after = MongoDataAutoConfiguration.class)
@EnableConfigurationProperties(DistributedLockProperties.class)
public class DistributedLockAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DistributedLock distributedLock(MongoTemplate mongoTemplate, DistributedLockProperties props) {
        log.info("Initializing DistributedLock(mongoTemplate={}, props={})", mongoTemplate, props);
        return new DistributedLock(props.getLockCollectionName(), props.getLockPeriod(), mongoTemplate);
    }
}
