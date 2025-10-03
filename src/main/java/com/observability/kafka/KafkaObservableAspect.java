package com.observability.kafka;

import com.observability.config.ObservabilityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Consumer;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.springframework.kafka.core.KafkaTemplate")
public class KafkaObservableAspect {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String MDC_KEY = "traceId";

    private final ObservabilityProperties properties;

    @Around("@annotation(kafkaObservable)")
    public Object aroundKafkaConsumer(ProceedingJoinPoint joinPoint, KafkaObservable kafkaObservable)
            throws Throwable {

        String consumerName = kafkaObservable.value();
        if (consumerName.isEmpty()) {
            consumerName = joinPoint.getSignature().getName();
        }

        final String finalConsumerName = consumerName;

        // Pega o Consumer original (sem cast)
        Object result = joinPoint.proceed();

        // Verifica se é Consumer
        if (!(result instanceof Consumer)) {
            return result;
        }

        @SuppressWarnings("unchecked")
        Consumer<Object> originalConsumer = (Consumer<Object>) result;

        // Retorna Consumer encapsulado
        return new Consumer<Object>() {
            @Override
            public void accept(Object message) {
                String traceId = null;
                Object payload = null;

                try {
                    if (message instanceof Message) {
                        Message<?> kafkaMessage = (Message<?>) message;
                        Object traceIdHeader = kafkaMessage.getHeaders().get(TRACE_ID_HEADER);

                        if (traceIdHeader != null) {
                            traceId = traceIdHeader.toString();
                        }

                        payload = kafkaMessage.getPayload();
                    } else {
                        payload = message;
                    }

                    if (traceId == null || traceId.isBlank()) {
                        traceId = UUID.randomUUID().toString();
                    }

                    MDC.put(MDC_KEY, traceId);

                    log.info("kafka-consumer",
                            kv("type", "receive"),
                            kv("consumer", finalConsumerName),
                            kv("payload", maskPayload(payload))
                    );

                    originalConsumer.accept(message);

                    log.info("kafka-consumer",
                            kv("type", "success"),
                            kv("consumer", finalConsumerName)
                    );

                } catch (Exception e) {
                    log.error("kafka-consumer",
                            kv("type", "error"),
                            kv("consumer", finalConsumerName),
                            kv("error", e.getMessage()),
                            kv("payload", maskPayload(payload)),
                            e
                    );
                    throw e;
                } finally {
                    MDC.remove(MDC_KEY);
                }
            }
        };
    }

    @SuppressWarnings("unchecked")
    private Object maskPayload(Object data) {
        if (!properties.getMasking().isEnabled() || data == null) {
            return data;
        }

        if (data instanceof Map) {
            Map<String, Object> map = new LinkedHashMap<>((Map<String, Object>) data);

            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = entry.getKey().toLowerCase();

                if (properties.getMasking().getFullMaskFields().contains(key)) {
                    map.put(entry.getKey(), properties.getMasking().getMaskValue());
                } else if (properties.getMasking().getPartialMaskFields().contains(key)) {
                    Object value = entry.getValue();
                    if (value instanceof String) {
                        map.put(entry.getKey(), maskCpfCnpj((String) value));
                    }
                } else if (entry.getValue() instanceof Map || entry.getValue() instanceof List) {
                    map.put(entry.getKey(), maskPayload(entry.getValue()));
                }
            }
            return map;
        }

        if (data instanceof List) {
            List<Object> list = (List<Object>) data;
            return list.stream().map(this::maskPayload).toList();
        }

        return data;
    }

    private String maskCpfCnpj(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        String digitsOnly = value.replaceAll("\\D", "");
        if (digitsOnly.length() <= 4) {
            return value.replaceAll("\\d", "*");
        }

        StringBuilder result = new StringBuilder();
        int digitIndex = 0;
        int totalDigits = digitsOnly.length();

        for (char c : value.toCharArray()) {
            if (Character.isDigit(c)) {
                digitIndex++;
                if (digitIndex <= 2 || digitIndex > totalDigits - 2) {
                    result.append(c);
                } else {
                    result.append('*');
                }
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }
}