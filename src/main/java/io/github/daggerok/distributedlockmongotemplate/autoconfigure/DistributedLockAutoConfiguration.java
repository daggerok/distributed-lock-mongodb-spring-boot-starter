package io.github.daggerok.distributedlockmongotemplate.autoconfigure;

import io.github.daggerok.distributedlockmongotemplate.DistributedLock;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

@Log4j2
@Configuration
@ConditionalOnClass(DistributedLock.class)
@AutoConfiguration(after = MongoDataAutoConfiguration.class)
@EnableConfigurationProperties(DistributedLockProperties.class)
@ComponentScan(basePackageClasses = DistributedLock.class)
public class DistributedLockAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DistributedLock distributedLock(MongoTemplate mongoTemplate, DistributedLockProperties props) {
        log.info("Initializing DistributedLock(mongoTemplate={}, props={})", mongoTemplate, props);
        return new DistributedLock(mongoTemplate, props);
    }
}
