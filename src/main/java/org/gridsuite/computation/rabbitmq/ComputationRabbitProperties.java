package org.gridsuite.computation.rabbitmq;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@ConfigurationProperties(prefix = "computation.rabbit")
public record ComputationRabbitProperties(List<String> loadbalancedGroup) { }
