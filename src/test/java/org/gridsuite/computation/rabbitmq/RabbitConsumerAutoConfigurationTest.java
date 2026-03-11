package org.gridsuite.computation.rabbitmq;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.cloud.stream.config.BindingProperties;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.cloud.stream.config.ListenerContainerCustomizer;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitConsumerAutoConfigurationTest {

    @Test
    void configureXPriorityForConsumerName() {
        RabbitConsumerAutoConfiguration config = new RabbitConsumerAutoConfiguration();
        BindingServiceProperties props = new BindingServiceProperties();

        BindingProperties binding = new BindingProperties();
        binding.setGroup("testGroup");
        Map<String, BindingProperties> bindings = new HashMap<>();
        bindings.put("consumeRun1-in-0", binding);
        props.setBindings(bindings);

        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
        ListenerContainerCustomizer<MessageListenerContainer> customizer = config.customizer(props);
        customizer.configure(container, "destination", "testGroup");

        assertThat(container.getConsumerArguments())
            .containsEntry("x-priority", 0);
    }

    @Test
    void configureXPriorityForMultipleConsumers() {
        RabbitConsumerAutoConfiguration config = new RabbitConsumerAutoConfiguration();
        BindingServiceProperties props = new BindingServiceProperties();

        BindingProperties binding = new BindingProperties();
        binding.setGroup("testGroup");
        Map<String, BindingProperties> bindings = new HashMap<>();
        bindings.put("consumeRun1-in-0", binding);

        props.setBindings(bindings);

        ListenerContainerCustomizer<MessageListenerContainer> customizer =
            config.customizer(props);

        SimpleMessageListenerContainer c1 = new SimpleMessageListenerContainer();
        SimpleMessageListenerContainer c2 = new SimpleMessageListenerContainer();

        customizer.configure(c1, "destination", "testGroup");
        customizer.configure(c2, "destination", "testGroup");

        assertThat(c1.getConsumerArguments())
            .containsEntry("x-priority", 0);
        assertThat(c2.getConsumerArguments())
            .containsEntry("x-priority", 1);
    }

    @Test
    void shouldNotSetPriorityForDifferentGroup() {
        RabbitConsumerAutoConfiguration config = new RabbitConsumerAutoConfiguration();
        BindingServiceProperties props = new BindingServiceProperties();

        BindingProperties binding = new BindingProperties();
        binding.setGroup("expectedGroup");
        Map<String, BindingProperties> bindings = new HashMap<>();
        bindings.put("consumeRun1-in-0", binding);

        props.setBindings(bindings);

        ListenerContainerCustomizer<MessageListenerContainer> customizer =
            config.customizer(props);
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();

        customizer.configure(container, "destination", "otherGroup");

        assertThat(container.getConsumerArguments()).isNullOrEmpty();
    }

    @Test
    void shouldHandleMissingBindingWithoutNpe() {
        RabbitConsumerAutoConfiguration config = new RabbitConsumerAutoConfiguration();
        BindingServiceProperties props = new BindingServiceProperties();
        props.setBindings(new HashMap<>());

        ListenerContainerCustomizer<MessageListenerContainer> customizer =
            config.customizer(props);
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();

        customizer.configure(container, "destination", "anyGroup");

        assertThat(container.getConsumerArguments()).isNullOrEmpty();
    }
}