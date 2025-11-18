/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation;

import com.powsybl.ws.commons.error.AbstractBusinessException;
import lombok.Getter;
import lombok.NonNull;

import java.util.Objects;

/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */
@Getter
public class ComputationException extends AbstractBusinessException {

    private final ComputationBusinessErrorCode errorCode;

    @NonNull
    @Override
    public ComputationBusinessErrorCode getBusinessErrorCode() {
        return errorCode;
    }

    public ComputationException(ComputationBusinessErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ComputationException(ComputationBusinessErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode);
    }
}
