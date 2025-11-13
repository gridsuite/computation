package org.gridsuite.computation;

import com.powsybl.ws.commons.error.BusinessErrorCode;

public enum ComputationBusinessErrorCode implements BusinessErrorCode {
    RESULT_NOT_FOUND("computation.resultNotFound"),
    INVALID_FILTER_FORMAT("computation.invalidFilterFormat"),
    INVALID_SORT_FORMAT("computation.invalidSortFormat"),
    INVALID_FILTER("computation.invalidFilter"),
    NETWORK_NOT_FOUND("computation.networkNotFound"),
    PARAMETERS_NOT_FOUND("computation.parametersNotFound"),
    FILE_EXPORT_ERROR("computation.fileExportError"),
    EVALUATE_FILTER_FAILED("computation.evaluateFilterFailed"),
    LIMIT_REDUCTION_CONFIG_ERROR("computation.limitReductionConfigError"),
    SPECIFIC("computation.specific"),;

    private final String code;

    ComputationBusinessErrorCode(String code) {
        this.code = code;
    }

    public String value() {
        return code;
    }
}
