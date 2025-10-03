package com.observability.autoconfigure;

import com.observability.config.ObservabilityProperties;
import com.observability.filter.LoggingFilter;
import com.observability.kafka.KafkaObservableAspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

@AutoConfiguration
@EnableConfigurationProperties(ObservabilityProperties.class)
public class ObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnProperty(prefix = "observability.http", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<LoggingFilter> loggingFilter(ObservabilityProperties properties) {
        FilterRegistrationBean<LoggingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new LoggingFilter(properties));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    @ConditionalOnClass(name = "org.springframework.kafka.core.KafkaTemplate")
    @ConditionalOnProperty(prefix = "observability.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
    public KafkaObservableAspect kafkaObservableAspect(ObservabilityProperties properties) {
        return new KafkaObservableAspect(properties);
    }
}