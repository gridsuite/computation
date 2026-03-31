/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation.rabbitmq;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.cloud.stream.config.ListenerContainerCustomizer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Kevin Le Saulnier <kevin.le-saulnier at rte-france.com>
 */
class RabbitConsumerAutoConfigurationTest {

    @Test
    void shouldSetPriorityForConfiguredGroup() {
        RabbitConsumerAutoConfiguration config = new RabbitConsumerAutoConfiguration();

        ListenerContainerCustomizer<MessageListenerContainer> customizer =
            config.customizer(new ComputationRabbitProperties(List.of("testGroup")));

        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();

        customizer.configure(container, "destination", "testGroup");

        assertThat(container.getConsumerArguments())
            .containsEntry("x-priority", 0);
    }

    @Test
    void shouldIncrementPriorityForSameGroup() {
        RabbitConsumerAutoConfiguration config = new RabbitConsumerAutoConfiguration();
        ListenerContainerCustomizer<MessageListenerContainer> customizer =
            config.customizer(new ComputationRabbitProperties(List.of("testGroup")));

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
    void shouldMaintainIndependentCountersPerGroup() {
        RabbitConsumerAutoConfiguration config = new RabbitConsumerAutoConfiguration();
        ListenerContainerCustomizer<MessageListenerContainer> customizer =
            config.customizer(new ComputationRabbitProperties(List.of("groupA", "groupB")));

        SimpleMessageListenerContainer a1 = new SimpleMessageListenerContainer();
        SimpleMessageListenerContainer a2 = new SimpleMessageListenerContainer();
        SimpleMessageListenerContainer b1 = new SimpleMessageListenerContainer();

        customizer.configure(a1, "destination", "groupA");
        customizer.configure(a2, "destination", "groupA");
        customizer.configure(b1, "destination", "groupB");

        assertThat(a1.getConsumerArguments())
            .containsEntry("x-priority", 0);
        assertThat(a2.getConsumerArguments())
            .containsEntry("x-priority", 1);

        assertThat(b1.getConsumerArguments())
            .containsEntry("x-priority", 0);
    }

    @Test
    void shouldNotSetPriorityForNonConfiguredGroup() {
        RabbitConsumerAutoConfiguration config = new RabbitConsumerAutoConfiguration();

        ListenerContainerCustomizer<MessageListenerContainer> customizer =
            config.customizer(new ComputationRabbitProperties(List.of("expectedGroup")));

        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();

        customizer.configure(container, "destination", "otherGroup");

        assertThat(container.getConsumerArguments()).isNullOrEmpty();
    }

    @Test
    void shouldHandleEmptyConfiguration() {
        RabbitConsumerAutoConfiguration config = new RabbitConsumerAutoConfiguration();

        ListenerContainerCustomizer<MessageListenerContainer> customizer =
            config.customizer(new ComputationRabbitProperties(null));

        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();

        customizer.configure(container, "destination", "anyGroup");

        assertThat(container.getConsumerArguments()).isNullOrEmpty();
    }
}
