/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.powsybl.ws.commons.error.PowsyblWsProblemDetail;
import org.gridsuite.computation.error.ComputationException;
import org.gridsuite.computation.error.RestResponseEntityExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.HttpClientErrorException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.computation.error.ComputationBusinessErrorCode.INVALID_SORT_FORMAT;
import static org.gridsuite.computation.error.ComputationBusinessErrorCode.RESULT_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RestResponseEntityExceptionHandlerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private TestRestResponseEntityExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TestRestResponseEntityExceptionHandler();
    }

    @Test
    void mapsNotFoundBusinessErrorToStatus() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/results-endpoint/uuid");
        ComputationException exception = new ComputationException(RESULT_NOT_FOUND, "Result not found");
        ResponseEntity<PowsyblWsProblemDetail> response = handler.invokeHandleDomainException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertEquals("computation.resultNotFound", response.getBody().getBusinessErrorCode());
    }

    @Test
    void mapsBadRequestBusinessErrorToStatus() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/results-endpoint/uuid");
        ComputationException exception = new ComputationException(INVALID_SORT_FORMAT, "Invalid sort format");
        ResponseEntity<PowsyblWsProblemDetail> response = handler.invokeHandleDomainException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertEquals("computation.invalidSortFormat", response.getBody().getBusinessErrorCode());
    }

    @Test
    void propagatesRemoteErrorDetails() throws JsonProcessingException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/computations/remote");
        PowsyblWsProblemDetail remote = PowsyblWsProblemDetail.builder(HttpStatus.INTERNAL_SERVER_ERROR)
                .server("computation")
                .businessErrorCode("computation.remoteError")
                .detail("Computation failure")
                .path("/computation")
                .build();

        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "error",
                HttpHeaders.EMPTY,
                OBJECT_MAPPER.writeValueAsBytes(remote),
                null
        );

        ResponseEntity<PowsyblWsProblemDetail> response = handler.invokeHandleRemoteException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertEquals("computation.remoteError", response.getBody().getBusinessErrorCode());
        assertThat(response.getBody().getChain()).hasSize(1);
    }

    private static final class TestRestResponseEntityExceptionHandler extends RestResponseEntityExceptionHandler {
        private TestRestResponseEntityExceptionHandler() {
            super(() -> "computation-server");
        }

        ResponseEntity<PowsyblWsProblemDetail> invokeHandleDomainException(ComputationException exception, MockHttpServletRequest request) {
            return super.handleDomainException(exception, request);
        }

        ResponseEntity<PowsyblWsProblemDetail> invokeHandleRemoteException(HttpClientErrorException exception, MockHttpServletRequest request) {
            return super.handleRemoteException(exception, request);
        }
    }
}