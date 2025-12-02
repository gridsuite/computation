/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation.error;

import com.powsybl.ws.commons.error.PowsyblWsProblemDetail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.computation.error.ComputationBusinessErrorCode.INVALID_SORT_FORMAT;
import static org.gridsuite.computation.error.ComputationBusinessErrorCode.RESULT_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ComputationExceptionHandlerTest {

    private ComputationExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ComputationExceptionHandler(() -> "computation");
    }

    @Test
    void mapsNotFoundBusinessErrorToStatus() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/results-endpoint/uuid");
        ComputationException exception = new ComputationException(RESULT_NOT_FOUND, "Result not found");
        ResponseEntity<PowsyblWsProblemDetail> response = handler.handleComputationException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertEquals("computation.resultNotFound", response.getBody().getBusinessErrorCode());
    }

    @Test
    void mapsBadRequestBusinessErrorToStatus() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/results-endpoint/uuid");
        ComputationException exception = new ComputationException(INVALID_SORT_FORMAT, "Invalid sort format");
        ResponseEntity<PowsyblWsProblemDetail> response = handler.handleComputationException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertEquals("computation.invalidSortFormat", response.getBody().getBusinessErrorCode());
    }
}
