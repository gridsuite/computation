/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation.dto.securityanalysis;

import com.powsybl.security.results.PreContingencyResult;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

@Getter
@Builder
@Jacksonized
public class EnrichedPreContingencyResultDTO {
    @NonNull
    private final PreContingencyResult result;
    @NonNull
    private final List<EnrichedLimitViolationDTO> enrichedLimitViolations;
}
