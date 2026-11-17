/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation.utils;

import com.powsybl.contingency.violations.LimitViolation;
import com.powsybl.iidm.network.Network;
import com.powsybl.security.SecurityAnalysisResult;
import com.powsybl.security.results.PostContingencyResult;
import com.powsybl.security.results.PreContingencyResult;
import org.gridsuite.computation.dto.securityanalysis.EnrichedSecurityAnalysisResultDTO;
import org.gridsuite.computation.dto.securityanalysis.LimitViolationEnrichmentDTO;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Radouane Khouadri <redouane.khouadri_externe at rte-france.com>
 */
public final class SecurityAnalysisEnricher {
    private SecurityAnalysisEnricher() {
    }

    public static EnrichedSecurityAnalysisResultDTO enrich(SecurityAnalysisResult result, Network network) {
        PreContingencyResult preContingencyResult = result.getPreContingencyResult();
        List<LimitViolationEnrichmentDTO> preContingencyEnrichment = preContingencyResult != null
            ? enrichPreContingency(preContingencyResult, network)
            : List.of();
        Map<String, List<LimitViolationEnrichmentDTO>> postContingencyEnrichment = new HashMap<>();

        if (result.getPostContingencyResults() != null) {
            for (PostContingencyResult postContingencyResult : result.getPostContingencyResults()) {
                List<LimitViolationEnrichmentDTO> contingencyEnrichment = postContingencyResult.getLimitViolationsResult() == null
                    ? List.of()
                    : postContingencyResult.getLimitViolationsResult().getLimitViolations().stream()
                        .map(violation -> enrichViolation(violation, network))
                        .toList();
                postContingencyEnrichment.put(postContingencyResult.getContingency().getId(), contingencyEnrichment);
            }
        }

        return EnrichedSecurityAnalysisResultDTO.builder()
            .result(result)
            .preContingencyEnrichment(preContingencyEnrichment)
            .postContingencyEnrichment(postContingencyEnrichment)
            .build();
    }

    private static List<LimitViolationEnrichmentDTO> enrichPreContingency(PreContingencyResult preContingencyResult, Network network) {
        return preContingencyResult.getLimitViolationsResult() == null
            ? List.of()
            : preContingencyResult.getLimitViolationsResult().getLimitViolations().stream()
                .map(violation -> enrichViolation(violation, network))
                .toList();
    }

    private static LimitViolationEnrichmentDTO enrichViolation(LimitViolation violation, Network network) {
        Double patlLimit = ComputationResultUtils.getPatlLimit(violation, network);
        return LimitViolationEnrichmentDTO.builder()
            .loading(ComputationResultUtils.computeLoading(violation, violation.getLimit()))
            .patlLimit(patlLimit)
            .patlLoading(ComputationResultUtils.computeLoading(violation, patlLimit))
            .nextLimitName(ComputationResultUtils.getNextLimitName(violation, network))
            .locationId(ComputationResultUtils.getViolationLocationId(violation, network))
            .acceptableDuration(ComputationResultUtils.calculateActualOverloadDuration(violation, network))
            .upcomingAcceptableDuration(ComputationResultUtils.calculateUpcomingOverloadDuration(violation))
            .build();
    }
}
