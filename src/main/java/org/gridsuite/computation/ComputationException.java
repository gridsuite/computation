/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation;

import lombok.Getter;

import java.util.Objects;

/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */
@Getter
public class ComputationException extends RuntimeException {
    public enum Type {
        RESULT_NOT_FOUND("Result not found."),
        INVALID_FILTER_FORMAT("The filter format is invalid."),
        INVALID_SORT_FORMAT("The sort format is invalid"),
        INVALID_FILTER("Invalid filter"),
        NETWORK_NOT_FOUND("Network not found"),
        PARAMETERS_NOT_FOUND("Computation parameters not found"),
        FILE_EXPORT_ERROR("Error exporting the file"),
        EVALUATE_FILTER_FAILED("Error evaluating the file"),
        LIMIT_REDUCTION_CONFIG_ERROR("Error int the configuration of the limit reduction"),
        SPECIFIC("Unknown error during the computation");

        private final String defaultMessage;

        Type(String defaultMessage) {
            this.defaultMessage = defaultMessage;
        }
    }

    private final Type exceptionType;

    public ComputationException(Type exceptionType) {
        super(Objects.requireNonNull(exceptionType.defaultMessage));
        this.exceptionType = Objects.requireNonNull(exceptionType);
    }

    public ComputationException(String message) {
        super(message);
        this.exceptionType = Type.SPECIFIC;
    }

    public ComputationException(String message, Throwable cause) {
        super(message, cause);
        this.exceptionType = Type.SPECIFIC;
    }

    public ComputationException(Type exceptionType, String message) {
        super(message);
        this.exceptionType = Objects.requireNonNull(exceptionType);
    }

    public ComputationException(Type exceptionType, String message, Throwable cause) {
        super(message, cause);
        this.exceptionType = Objects.requireNonNull(exceptionType);
    }
}
