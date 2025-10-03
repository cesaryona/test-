package com.observability.kafka;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca um método @Bean que retorna Consumer para adicionar
 * observabilidade automática (traceId + logs mascarados)
 *
 * Uso:
 * <pre>
 * {@code
 * @Bean
 * @KafkaObservable("meu-consumer")
 * public Consumer<MeuDto> consumer() {
 *     return service::processar;
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface KafkaObservable {
    /**
     * Nome customizado do consumer (para logs).
     * Se não informado, usa o nome do método.
     */
    String value() default "";
}