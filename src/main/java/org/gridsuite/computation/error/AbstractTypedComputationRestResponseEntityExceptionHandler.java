/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation.error;

import com.powsybl.ws.commons.error.BusinessErrorCode;
import com.powsybl.ws.commons.error.ServerNameProvider;
import org.springframework.http.HttpStatus;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */

public abstract class AbstractTypedComputationRestResponseEntityExceptionHandler<C extends BusinessErrorCode> extends ComputationRestResponseEntityExceptionHandler {
    private final Class<C> specificComputationBusinessErrorCode;

    protected AbstractTypedComputationRestResponseEntityExceptionHandler(ServerNameProvider serverNameProvider, Class<C> specificComputationBusinessErrorCode) {
        super(serverNameProvider);
        this.specificComputationBusinessErrorCode = specificComputationBusinessErrorCode;
    }

    @Override
    protected HttpStatus mapStatus(BusinessErrorCode businessErrorCode) {
        if (businessErrorCode instanceof ComputationBusinessErrorCode computationCode) {
            return super.mapStatus(computationCode);
        } else if (specificComputationBusinessErrorCode.isInstance(businessErrorCode)) {
            return mapSpecificStatus(specificComputationBusinessErrorCode.cast(businessErrorCode));
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    protected abstract HttpStatus mapSpecificStatus(C code);
}
