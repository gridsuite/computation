/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation.dto.securityanalysis;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.powsybl.security.SecurityAnalysisResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * @author Radouane Khouadri <redouane.khouadri_externe at rte-france.com>
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrichedSecurityAnalysisResultDTO {
    private SecurityAnalysisResult result;
    private Map<String, EnrichedLimitViolationDTO> preContingencyEnrichment;
    private Map<String, Map<String, EnrichedLimitViolationDTO>> postContingencyEnrichment;

    @JsonIgnore
    public EnrichedPreContingencyResultDTO getEnrichedPreContingencyResult() {
        return new EnrichedPreContingencyResultDTO(result.getPreContingencyResult(), preContingencyEnrichment);
    }

    @JsonIgnore
    public List<EnrichedPostContingencyResultDTO> getEnrichedPostContingencyResults() {
        return result.getPostContingencyResults().stream()
            .map(postContingencyResult -> new EnrichedPostContingencyResultDTO(postContingencyResult,
                postContingencyEnrichment != null ? postContingencyEnrichment.get(postContingencyResult.getContingency().getId()) : null))
            .toList();
    }
}
