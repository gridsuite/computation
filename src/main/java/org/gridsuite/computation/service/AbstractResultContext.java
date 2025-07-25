/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.computation.service.NotificationService.*;

/**
 * @author Mathieu Deharbe <mathieu.deharbe at rte-france.com>
 * @param <C> run context specific to a computation, including parameters
 */
@Data
public abstract class AbstractResultContext<C extends AbstractComputationRunContext<?>> {

    protected static final String RESULT_UUID_HEADER = "resultUuid";

    protected static final String NETWORK_UUID_HEADER = "networkUuid";

    protected static final String REPORT_UUID_HEADER = "reportUuid";

    public static final String VARIANT_ID_HEADER = "variantId";

    public static final String REPORTER_ID_HEADER = "reporterId";

    public static final String REPORT_TYPE_HEADER = "reportType";

    protected static final String MESSAGE_ROOT_NAME = "parameters";

    private final UUID resultUuid;
    private final C runContext;

    protected AbstractResultContext(UUID resultUuid, C runContext) {
        this.resultUuid = Objects.requireNonNull(resultUuid);
        this.runContext = Objects.requireNonNull(runContext);
    }

    public Message<String> toMessage(ObjectMapper objectMapper) {
        String parametersJson = "";
        if (objectMapper != null) {
            try {
                parametersJson = objectMapper.writeValueAsString(runContext.getParameters());
            } catch (JsonProcessingException e) {
                throw new UncheckedIOException(e);
            }
        }
        return MessageBuilder.withPayload(parametersJson)
                .setHeader(RESULT_UUID_HEADER, resultUuid.toString())
                .setHeader(NETWORK_UUID_HEADER, runContext.getNetworkUuid().toString())
                .setHeader(VARIANT_ID_HEADER, runContext.getVariantId())
                .setHeader(HEADER_RECEIVER, runContext.getReceiver())
                .setHeader(HEADER_PROVIDER, runContext.getProvider())
                .setHeader(HEADER_USER_ID, runContext.getUserId())
                .setHeader(REPORT_UUID_HEADER, runContext.getReportInfos().reportUuid() != null ? runContext.getReportInfos().reportUuid().toString() : null)
                .setHeader(REPORTER_ID_HEADER, runContext.getReportInfos().reporterId())
                .setHeader(REPORT_TYPE_HEADER, runContext.getReportInfos().computationType())
                .copyHeaders(getSpecificMsgHeaders(objectMapper))
                .build();
    }

    @SuppressWarnings("unused")
    protected Map<String, String> getSpecificMsgHeaders(ObjectMapper ignoredObjectMapper) {
        return Map.of();
    }
}
