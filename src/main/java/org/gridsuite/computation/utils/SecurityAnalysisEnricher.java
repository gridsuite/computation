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
import java.util.Map;
import java.util.Set;

import static com.powsybl.contingency.violations.LimitViolationType.HIGH_VOLTAGE;
import static com.powsybl.contingency.violations.LimitViolationType.HIGH_VOLTAGE_ANGLE;
import static com.powsybl.contingency.violations.LimitViolationType.LOW_VOLTAGE;
import static com.powsybl.contingency.violations.LimitViolationType.LOW_VOLTAGE_ANGLE;

/**
 * @author Radouane Khouadri <redouane.khouadri_externe at rte-france.com>
 */
public final class SecurityAnalysisEnricher {
    private SecurityAnalysisEnricher() {
    }

    public static EnrichedSecurityAnalysisResultDTO enrich(SecurityAnalysisResult result, Network network) {
        PreContingencyResult preContingencyResult = result.getPreContingencyResult();
        Map<String, LimitViolationEnrichmentDTO> preContingencyEnrichment = preContingencyResult != null
            ? enrichPreContingency(preContingencyResult, network)
            : new HashMap<>();
        Map<String, Map<String, LimitViolationEnrichmentDTO>> postContingencyEnrichment = new HashMap<>();

        if (result.getPostContingencyResults() != null) {
            for (PostContingencyResult postContingencyResult : result.getPostContingencyResults()) {
                Map<String, LimitViolationEnrichmentDTO> contingencyEnrichment = new HashMap<>();
                if (postContingencyResult.getLimitViolationsResult() != null) {
                    postContingencyResult.getLimitViolationsResult().getLimitViolations().forEach(violation -> {
                        String violationLocationId = !Set.of(LOW_VOLTAGE, HIGH_VOLTAGE, LOW_VOLTAGE_ANGLE, HIGH_VOLTAGE_ANGLE).contains(violation.getLimitType())
                            ? ComputationResultUtils.getViolationId(violation)
                            : ComputationResultUtils.getViolationLocationId(violation, network);
                        contingencyEnrichment.put(violationLocationId, enrichViolation(violation, network));
                    });
                }
                postContingencyEnrichment.put(postContingencyResult.getContingency().getId(), contingencyEnrichment);
            }
        }

        return EnrichedSecurityAnalysisResultDTO.builder()
            .result(result)
            .preContingencyEnrichment(preContingencyEnrichment)
            .postContingencyEnrichment(postContingencyEnrichment)
            .build();
    }

    private static Map<String, LimitViolationEnrichmentDTO> enrichPreContingency(PreContingencyResult preContingencyResult, Network network) {
        Map<String, LimitViolationEnrichmentDTO> enrichment = new HashMap<>();
        if (preContingencyResult.getLimitViolationsResult() != null) {
            preContingencyResult.getLimitViolationsResult().getLimitViolations().forEach(violation -> {
                enrichment.put(ComputationResultUtils.getViolationId(violation), enrichViolation(violation, network));
            });
        }
        return enrichment;
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
