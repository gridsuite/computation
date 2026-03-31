/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation.rabbitmq;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

/**
 * @author Kevin Le Saulnier <kevin.le-saulnier at rte-france.com>
 */
@ConfigurationProperties(prefix = "computation.rabbit")
public record ComputationRabbitProperties(List<String> loadbalancedGroup) { }
