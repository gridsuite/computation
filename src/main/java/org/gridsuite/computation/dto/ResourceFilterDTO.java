/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/**
 * An object that can be used to filter data with the JPA Criteria API (via Spring Specification)
 * @param dataType the type of data we want to filter (text, number)
 * @param type the type of filter (contains, startsWith...)
 * @param value the value of the filter
 * @param column the column / field on which the filter will be applied
 * @param tolerance precision/tolerance used for the comparisons (simulates the rounding of the database values) Only useful for numbers.
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */
public record ResourceFilterDTO(@NotNull DataType dataType, @NotNull Type type, Object value, @NotNull String column, Double tolerance) {

    public ResourceFilterDTO(@NotNull DataType dataType, @NotNull Type type, Object value, @NotNull String column) {
        this(dataType, type, value, column, null);
    }

    public enum DataType {
        @JsonProperty("text")
        TEXT,
        @JsonProperty("number")
        NUMBER,
    }

    public enum Type {
        @JsonProperty("contains")
        CONTAINS,
        @JsonProperty("startsWith")
        STARTS_WITH,
        @JsonProperty("equals")
        EQUALS,
        @JsonProperty("notEqual")
        NOT_EQUAL,
        @JsonProperty("lessThanOrEqual")
        LESS_THAN_OR_EQUAL,
        @JsonProperty("greaterThanOrEqual")
        GREATER_THAN_OR_EQUAL,
        @JsonProperty("in")
        IN
    }
}

