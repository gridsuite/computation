/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation.error;

import com.powsybl.ws.commons.error.BusinessErrorCode;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
public enum ComputationBusinessErrorCode implements BusinessErrorCode {
    RESULT_NOT_FOUND("computation.resultNotFound"),
    PARAMETERS_NOT_FOUND("computation.parametersNotFound"),
    INVALID_SORT_FORMAT("computation.invalidSortFormat"),
    INVALID_EXPORT_PARAMS("computation.invalidExportParams"),
    LIMIT_REDUCTION_CONFIG_ERROR("computation.limitReductionConfigError"),
    RUNNER_ERROR("computation.runnerError");

    private final String code;

    ComputationBusinessErrorCode(String code) {
        this.code = code;
    }

    public String value() {
        return code;
    }
}
