/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation.error.utils;

import com.powsybl.ws.commons.error.PowsyblWsProblemDetail;
import org.gridsuite.computation.error.ComputationException;
import org.gridsuite.computation.error.ComputationRestResponseEntityExceptionHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.HttpClientErrorException;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
public class TestRestResponseEntityExceptionHandler extends ComputationRestResponseEntityExceptionHandler {
    public TestRestResponseEntityExceptionHandler() {
        super(() -> "computation-server");
    }

    public ResponseEntity<PowsyblWsProblemDetail> invokeHandleDomainException(ComputationException exception, MockHttpServletRequest request) {
        return super.handleDomainException(exception, request);
    }

    public ResponseEntity<PowsyblWsProblemDetail> invokeHandleRemoteException(HttpClientErrorException exception, MockHttpServletRequest request) {
        return super.handleRemoteException(exception, request);
    }
}