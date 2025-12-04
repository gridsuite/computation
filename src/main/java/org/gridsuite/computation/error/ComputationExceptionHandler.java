/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation.error;

import com.powsybl.ws.commons.error.AbstractBusinessExceptionHandler;
import com.powsybl.ws.commons.error.PowsyblWsProblemDetail;
import com.powsybl.ws.commons.error.ServerNameProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */

@ControllerAdvice
public class ComputationExceptionHandler extends AbstractBusinessExceptionHandler<ComputationException, ComputationBusinessErrorCode> {

    protected ComputationExceptionHandler(ServerNameProvider serverNameProvider) {
        super(serverNameProvider);
    }

    @Override
    protected @NonNull ComputationBusinessErrorCode getBusinessCode(ComputationException e) {
        return e.getBusinessErrorCode();
    }

    @Override
    protected HttpStatus mapStatus(ComputationBusinessErrorCode businessErrorCode) {
        return switch (businessErrorCode) {
            case RESULT_NOT_FOUND, PARAMETERS_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_SORT_FORMAT, INVALID_EXPORT_PARAMS -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    @ExceptionHandler(ComputationException.class)
    public ResponseEntity<PowsyblWsProblemDetail> handleComputationException(ComputationException exception, HttpServletRequest request) {
        return super.handleDomainException(exception, request);
    }
}
