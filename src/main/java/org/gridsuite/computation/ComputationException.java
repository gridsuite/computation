/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation;

import com.powsybl.ws.commons.error.AbstractBusinessException;
import com.powsybl.ws.commons.error.BusinessErrorCode;
import lombok.Getter;
import lombok.NonNull;

import java.util.Objects;

/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */
@Getter
public class ComputationException extends AbstractBusinessException {

    protected final BusinessErrorCode errorCode;

    public ComputationException(ComputationBusinessErrorCode exceptionType) {
        super(Objects.requireNonNull(exceptionType.value()));
        this.errorCode = Objects.requireNonNull(exceptionType);
    }

    public ComputationException(String message) {
        super(message);
        this.errorCode = ComputationBusinessErrorCode.SPECIFIC;
    }

    public ComputationException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = ComputationBusinessErrorCode.SPECIFIC;
    }

    @NonNull
    @Override
    public BusinessErrorCode getBusinessErrorCode() {
        return errorCode;
    }

    public ComputationException(BusinessErrorCode exceptionType, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(exceptionType);
    }

    public ComputationException(BusinessErrorCode exceptionType, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(exceptionType);
    }
}
