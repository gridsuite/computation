/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation.error.utils;

import com.powsybl.ws.commons.error.PowsyblWsProblemDetail;
import org.gridsuite.computation.error.AbstractTypedComputationRestResponseEntityExceptionHandler;
import org.gridsuite.computation.error.ComputationBusinessErrorCode;
import org.gridsuite.computation.error.ComputationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

public class TestTypedRestResponseEntityException extends AbstractTypedComputationRestResponseEntityExceptionHandler<SpecificErrorCode> {
    public TestTypedRestResponseEntityException() {
        super(() -> "computation-server", SpecificErrorCode.class);
    }

    @Override
    protected HttpStatus mapSpecificStatus(SpecificErrorCode code) {
        return switch (code) {
            case MISC -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    public ResponseEntity<PowsyblWsProblemDetail> invokeHandle(SpecificErrorCode code) {
        SpecificException exception = new SpecificException(code, "msg");
        return handleDomainException(exception, new MockHttpServletRequest("GET", "/test"));
    }

    public ResponseEntity<PowsyblWsProblemDetail> invokeHandle(ComputationBusinessErrorCode code) {
        ComputationException exception = new ComputationException(code, "msg");
        return handleDomainException(exception, new MockHttpServletRequest("GET", "/test"));
    }
}


