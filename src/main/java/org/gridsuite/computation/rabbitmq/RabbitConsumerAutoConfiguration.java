package org.gridsuite.computation.rabbitmq;

import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.stream.config.ListenerContainerCustomizer;
import org.springframework.context.annotation.Bean;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@AutoConfiguration
@EnableConfigurationProperties(ComputationRabbitProperties.class)
public class RabbitConsumerAutoConfiguration {
    /*
     * RabbitMQ consumer priority:
     * https://www.rabbitmq.com/docs/consumer-priority
     *
     * Each container creates exactly one AMQP consumer with prefetch=1 and its own priority.
     * When dispatching messages, RabbitMQ always selects the highest-priority consumer
     * that is available.
     */
    @Bean
    public ListenerContainerCustomizer<MessageListenerContainer> customizer(ComputationRabbitProperties computationRabbitProperties) {
        List<String> loadBalancedGroups = computationRabbitProperties.loadbalancedGroup();
        Map<String, AtomicInteger> groupIndexes = new ConcurrentHashMap<>();
        /*
         * Using AtomicInteger as in org/springframework/cloud/stream/binder/rabbit/RabbitMessageChannelBinder.java
         * We expect cloud stream to call our customizer exactly once in order for each container so it will produce a sequence of increasing priorities
         */
        return (container, destination, group) -> {
            if (!(container instanceof SimpleMessageListenerContainer smlc) || loadBalancedGroups == null) {
                return;
            }

            if (!loadBalancedGroups.contains(group)) {
                return;
            }

            AtomicInteger index = groupIndexes.computeIfAbsent(group, g -> new AtomicInteger());

            smlc.setConsumerArguments(Map.of(
                "x-priority", index.getAndIncrement()
            ));
        };
    }
}
