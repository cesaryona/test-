package com.observability.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.Set;

@Data
@ConfigurationProperties(prefix = "observability")
public class ObservabilityProperties {

    private Http http = new Http();
    private Kafka kafka = new Kafka();
    private Masking masking = new Masking();

    @Data
    public static class Http {
        private boolean enabled = true;
        private Set<String> excludedPaths = new HashSet<>(Set.of(
                "/actuator/health",
                "/actuator/metrics",
                "/favicon.ico"
        ));
    }

    @Data
    public static class Kafka {
        private boolean enabled = true;
    }

    @Data
    public static class Masking {
        private boolean enabled = true;
        private String maskValue = "***MASKED***";

        // Campos que serão mascarados completamente
        private Set<String> fullMaskFields = new HashSet<>(Set.of(
                "password", "senha", "token", "authorization",
                "secret", "apiKey", "api_key", "creditCard",
                "credit_card", "cvv", "pin"
        ));

        // Campos que terão mascaramento parcial (CPF/CNPJ)
        private Set<String> partialMaskFields = new HashSet<>(Set.of(
                "cpf", "cnpj", "documento"
        ));
    }
}