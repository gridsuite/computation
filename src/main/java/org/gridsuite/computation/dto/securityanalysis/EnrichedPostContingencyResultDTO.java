/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation.dto.securityanalysis;

import com.powsybl.contingency.violations.LimitViolation;
import com.powsybl.security.results.PostContingencyResult;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.gridsuite.computation.utils.ComputationResultUtils;

import java.util.Map;

@Getter
@AllArgsConstructor
public class EnrichedPostContingencyResultDTO {
    private final PostContingencyResult result;
    private final Map<String, EnrichedLimitViolationDTO> limitViolationEnrichment;

    public EnrichedLimitViolationDTO getEnrichment(LimitViolation violation) {
        return limitViolationEnrichment != null ? limitViolationEnrichment.get(ComputationResultUtils.getViolationId(violation)) : null;
    }
}
