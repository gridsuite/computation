/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.gridsuite.computation.utils.annotations.PostCompletion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com
 */
@AllArgsConstructor
@Service
public class NotificationService {
    private static final String CANCEL_CATEGORY_BROKER_OUTPUT = NotificationService.class.getName() + ".output-broker-messages.cancel";
    private static final String RUN_CATEGORY_BROKER_OUTPUT = NotificationService.class.getName() + ".output-broker-messages.run";
    private static final String STOP_CATEGORY_BROKER_OUTPUT = NotificationService.class.getName() + ".output-broker-messages.stop";
    private static final String RESULT_CATEGORY_BROKER_OUTPUT = NotificationService.class.getName() + ".output-broker-messages.result";
    private static final String FAILED_CATEGORY_BROKER_OUTPUT = NotificationService.class.getName() + ".output-broker-messages.failed";
    private static final String CANCEL_FAILED_CATEGORY_BROKER_OUTPUT = NotificationService.class.getName() + ".output-broker-messages.cancel-failed";

    private static final Logger RUN_MESSAGE_LOGGER = LoggerFactory.getLogger(RUN_CATEGORY_BROKER_OUTPUT);
    private static final Logger CANCEL_MESSAGE_LOGGER = LoggerFactory.getLogger(CANCEL_CATEGORY_BROKER_OUTPUT);
    private static final Logger STOP_MESSAGE_LOGGER = LoggerFactory.getLogger(STOP_CATEGORY_BROKER_OUTPUT);
    private static final Logger RESULT_MESSAGE_LOGGER = LoggerFactory.getLogger(RESULT_CATEGORY_BROKER_OUTPUT);
    private static final Logger FAILED_MESSAGE_LOGGER = LoggerFactory.getLogger(FAILED_CATEGORY_BROKER_OUTPUT);
    private static final Logger CANCEL_FAILED_MESSAGE_LOGGER = LoggerFactory.getLogger(CANCEL_FAILED_CATEGORY_BROKER_OUTPUT);

    public static final String HEADER_RESULT_UUID = "resultUuid";
    public static final String HEADER_RECEIVER = "receiver";
    public static final String HEADER_PROVIDER = "provider";
    public static final String HEADER_MESSAGE = "message";
    public static final String HEADER_USER_ID = "userId";
    public static final String HEADER_DEBUG = "debug";
    public static final String HEADER_ERROR_MESSAGE = "errorMessage";

    public static final String SENDING_MESSAGE = "Sending message : {}";

    @Getter private final StreamBridge publisher;
    @Getter private final String publishPrefix;

    @Autowired
    public NotificationService(StreamBridge publisher) {
        this(publisher, "publish");
    }

    public void sendRunMessage(Message<String> message) {
        RUN_MESSAGE_LOGGER.debug(SENDING_MESSAGE, message);
        publisher.send(publishPrefix + "Run-out-0", message);
    }

    public void sendCancelMessage(Message<String> message) {
        CANCEL_MESSAGE_LOGGER.debug(SENDING_MESSAGE, message);
        publisher.send(publishPrefix + "Cancel-out-0", message);
    }

    @PostCompletion
    public void sendDebugMessage(UUID resultUuid, String receiver, String userId, @Nullable Map<String, Object> additionalHeaders) {
        MessageBuilder<String> builder = MessageBuilder
                .withPayload("")
                .setHeader(HEADER_RESULT_UUID, resultUuid.toString())
                .setHeader(HEADER_RECEIVER, receiver)
                .setHeader(HEADER_USER_ID, userId)
                .copyHeaders(additionalHeaders);
        Message<String> message = builder.build();
        RESULT_MESSAGE_LOGGER.debug(SENDING_MESSAGE, message);
        publisher.send(publishPrefix + "Debug-out-0", message);
    }

    @PostCompletion
    public void sendResultMessage(UUID resultUuid, String receiver, String userId, @Nullable Map<String, Object> additionalHeaders) {
        MessageBuilder<String> builder = MessageBuilder
                .withPayload("")
                .setHeader(HEADER_RESULT_UUID, resultUuid.toString())
                .setHeader(HEADER_RECEIVER, receiver)
                .setHeader(HEADER_USER_ID, userId)
                .copyHeaders(additionalHeaders);
        Message<String> message = builder.build();
        RESULT_MESSAGE_LOGGER.debug(SENDING_MESSAGE, message);
        publisher.send(publishPrefix + "Result-out-0", message);
    }

    @PostCompletion
    public void publishStop(UUID resultUuid, String receiver, String computationLabel) {
        Message<String> message = MessageBuilder
                .withPayload("")
                .setHeader(HEADER_RESULT_UUID, resultUuid.toString())
                .setHeader(HEADER_RECEIVER, receiver)
                .setHeader(HEADER_MESSAGE, getCancelMessage(computationLabel))
                .build();
        STOP_MESSAGE_LOGGER.debug(SENDING_MESSAGE, message);
        publisher.send(publishPrefix + "Stopped-out-0", message);
    }

    @PostCompletion
    public void publishCancelFailed(UUID resultUuid, String receiver, String computationLabel, String userId) {
        Message<String> message = MessageBuilder
                .withPayload("")
                .setHeader(HEADER_RESULT_UUID, resultUuid.toString())
                .setHeader(HEADER_RECEIVER, receiver)
                .setHeader(HEADER_USER_ID, userId)
                .setHeader(HEADER_MESSAGE, getCancelFailedMessage(computationLabel))
                .build();
        CANCEL_FAILED_MESSAGE_LOGGER.info(SENDING_MESSAGE, message);
        publisher.send(publishPrefix + "CancelFailed-out-0", message);
    }

    public static String getCancelMessage(String computationLabel) {
        return computationLabel + " was canceled";
    }

    public static String getFailedMessage(String computationLabel) {
        return computationLabel + " has failed";
    }

    public static String getCancelFailedMessage(String computationLabel) {
        return computationLabel + " could not be cancelled";
    }
}
