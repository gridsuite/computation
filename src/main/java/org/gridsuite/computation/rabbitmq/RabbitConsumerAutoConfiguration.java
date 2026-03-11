package org.gridsuite.computation.rabbitmq;

import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.config.BindingProperties;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.cloud.stream.config.ListenerContainerCustomizer;
import org.springframework.context.annotation.Bean;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@AutoConfiguration
@ConditionalOnProperty(
    name = "computation.rabbit.consume-run-load-balanced.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class RabbitConsumerAutoConfiguration {
    private static final String RABBITMQ_CONSUMER_NAME_TO_LOAD_BALANCE = "consumeRun1-in-0";

    /*
     * RabbitMQ consumer priority:
     * https://www.rabbitmq.com/docs/consumer-priority
     *
     * Each container creates exactly one AMQP consumer with prefetch=1 and its own priority.
     * When dispatching messages, RabbitMQ always selects the highest-priority consumer
     * that is available.
     */
    @Bean
    public ListenerContainerCustomizer<MessageListenerContainer> customizer(BindingServiceProperties bindingServiceProperties) {
        String computationRunGroup = Optional.ofNullable(bindingServiceProperties.getBindings())
            .map(bindings -> bindings.get(RABBITMQ_CONSUMER_NAME_TO_LOAD_BALANCE))
            .map(BindingProperties::getGroup)
            .orElse(null);

        /*
         * Using AtomicInteger as in org/springframework/cloud/stream/binder/rabbit/RabbitMessageChannelBinder.java
         * We expect cloud stream to call our customizer exactly once in order for each container so it will produce a sequence of increasing priorities
         */
        AtomicInteger index = new AtomicInteger();
        return (container, destination, group) -> {
            if (container instanceof SimpleMessageListenerContainer smlc
                && computationRunGroup != null
                && Objects.equals(group, computationRunGroup)) {
                smlc.setConsumerArguments(Map.of("x-priority", index.getAndIncrement()));
            }
        };
    }
}
