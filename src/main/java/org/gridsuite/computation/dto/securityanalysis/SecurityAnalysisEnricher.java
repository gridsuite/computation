/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation.dto.securityanalysis;

import com.powsybl.contingency.violations.LimitViolation;
import com.powsybl.iidm.network.Network;
import com.powsybl.security.SecurityAnalysisResult;
import com.powsybl.security.results.PostContingencyResult;
import com.powsybl.security.results.PreContingencyResult;
import org.gridsuite.computation.utils.ComputationResultUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Radouane Khouadri <redouane.khouadri_externe at rte-france.com>
 */
public final class SecurityAnalysisEnricher {
    private SecurityAnalysisEnricher() {
    }

    public static EnrichedSecurityAnalysisResultDTO enrich(SecurityAnalysisResult result, Network network) {
        PreContingencyResult preContingencyResult = result.getPreContingencyResult();
        Map<String, EnrichedLimitViolationDTO> preContingencyEnrichment = preContingencyResult != null
            ? enrichPreContingency(preContingencyResult, network)
            : new HashMap<>();
        Map<String, Map<String, EnrichedLimitViolationDTO>> postContingencyEnrichment = new HashMap<>();

        if (result.getPostContingencyResults() != null) {
            for (PostContingencyResult postContingencyResult : result.getPostContingencyResults()) {
                if (postContingencyResult.getLimitViolationsResult() != null) {
                    Map<String, EnrichedLimitViolationDTO> contingencyEnrichment = new HashMap<>();
                    postContingencyResult.getLimitViolationsResult().getLimitViolations().forEach(violation -> {
                        contingencyEnrichment.put(ComputationResultUtils.getViolationId(violation), enrichViolation(violation, network));
                    });
                    postContingencyEnrichment.put(postContingencyResult.getContingency().getId(), contingencyEnrichment);
                }
            }
        }

        return EnrichedSecurityAnalysisResultDTO.builder()
            .result(result)
            .preContingencyEnrichment(preContingencyEnrichment)
            .postContingencyEnrichment(postContingencyEnrichment)
            .build();
    }

    private static Map<String, EnrichedLimitViolationDTO> enrichPreContingency(PreContingencyResult preContingencyResult, Network network) {
        Map<String, EnrichedLimitViolationDTO> enrichment = new HashMap<>();
        if (preContingencyResult.getLimitViolationsResult() != null) {
            preContingencyResult.getLimitViolationsResult().getLimitViolations().forEach(violation -> {
                enrichment.put(ComputationResultUtils.getViolationId(violation), enrichViolation(violation, network));
            });
        }
        return enrichment;
    }

    private static EnrichedLimitViolationDTO enrichViolation(LimitViolation violation, Network network) {
        Double patlLimit = ComputationResultUtils.getPatlLimit(violation, network);
        return EnrichedLimitViolationDTO.builder()
            .patlLimit(patlLimit)
            .patlLoading(ComputationResultUtils.computeLoading(violation, patlLimit))
            .nextLimitName(ComputationResultUtils.getNextLimitName(violation, network))
            .locationId(ComputationResultUtils.getViolationLocationId(violation, network))
            .acceptableDuration(ComputationResultUtils.calculateActualOverloadDuration(violation, network))
            .build();
    }
}
