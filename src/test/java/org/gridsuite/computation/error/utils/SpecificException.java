/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation.error.utils;

import com.powsybl.ws.commons.error.AbstractBusinessException;
import lombok.NonNull;

import java.util.Objects;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
public class SpecificException extends AbstractBusinessException {

    private final SpecificErrorCode errorCode;

    public SpecificException(SpecificErrorCode errorCode, String message) {
        super(Objects.requireNonNull(message, "message must not be null"));
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    @Override
    public @NonNull SpecificErrorCode getBusinessErrorCode() {
        return errorCode;
    }
}
