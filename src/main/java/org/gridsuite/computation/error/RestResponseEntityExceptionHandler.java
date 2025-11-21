package org.gridsuite.computation.error;

import com.powsybl.ws.commons.error.AbstractBaseRestExceptionHandler;
import com.powsybl.ws.commons.error.AbstractBusinessException;
import com.powsybl.ws.commons.error.BusinessErrorCode;
import com.powsybl.ws.commons.error.ServerNameProvider;
import lombok.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;

import static org.gridsuite.computation.error.ComputationBusinessErrorCode.INVALID_EXPORT_PARAMS;
import static org.gridsuite.computation.error.ComputationBusinessErrorCode.INVALID_SORT_FORMAT;
import static org.gridsuite.computation.error.ComputationBusinessErrorCode.PARAMETERS_NOT_FOUND;
import static org.gridsuite.computation.error.ComputationBusinessErrorCode.RESULT_NOT_FOUND;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */

@ControllerAdvice
public class RestResponseEntityExceptionHandler extends AbstractBaseRestExceptionHandler<AbstractBusinessException, BusinessErrorCode> {

    protected RestResponseEntityExceptionHandler(ServerNameProvider serverNameProvider) {
        super(serverNameProvider);
    }

    @Override
    protected @NonNull BusinessErrorCode getBusinessCode(AbstractBusinessException e) {
        return e.getBusinessErrorCode();
    }

    @Override
    protected HttpStatus mapStatus(BusinessErrorCode businessErrorCode) {
        return switch (businessErrorCode) {
            case RESULT_NOT_FOUND, PARAMETERS_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_SORT_FORMAT,
                 INVALID_EXPORT_PARAMS -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
