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
import org.gridsuite.computation.utils.ComputationResultUtils;

import java.util.Objects;

/**
 * @author Radouane Khouadri <redouane.khouadri_externe at rte-france.com>
 */
public final class SecurityAnalysisEnricher {
    private SecurityAnalysisEnricher() {
    }

    public static EnrichedSecurityAnalysisResultDTO enrich(SecurityAnalysisResult result, Network network) {
        Objects.requireNonNull(network);
        return EnrichedSecurityAnalysisResultDTO.builder()
            .enrichedPreContingencyResult(EnrichedPreContingencyResultDTO.builder()
                .result(result.getPreContingencyResult())
                .enrichedLimitViolations(result.getPreContingencyResult().getLimitViolationsResult().getLimitViolations().stream()
                    .map(violation -> enrichViolation(violation, network)).toList())
                .build())
            .enrichedPostContingencyResults(result.getPostContingencyResults().stream()
                .map(post -> enrichPostContingency(post, network)).toList())
            .build();
    }

    private static EnrichedPostContingencyResultDTO enrichPostContingency(PostContingencyResult result, Network network) {
        return EnrichedPostContingencyResultDTO.builder()
            .result(result)
            .enrichedLimitViolations(result.getLimitViolationsResult().getLimitViolations().stream()
                .map(violation -> enrichViolation(violation, network)).toList())
            .build();
    }

    public static EnrichedLimitViolationDTO enrichViolation(LimitViolation violation, Network network) {
        Double patlLimit = ComputationResultUtils.getPatlLimit(violation, network);
        return EnrichedLimitViolationDTO.builder()
            .subjectId(violation.getSubjectId())
            .subjectName(violation.getSubjectName())
            .limit(violation.getLimit())
            .limitName(violation.getLimitName())
            .operationalLimitsGroupId(violation.getOperationalLimitsGroupId())
            .limitType(violation.getLimitType())
            .limitReduction(violation.getLimitReduction())
            .value(violation.getValue())
            .side(violation.getSide())
            .loading(ComputationResultUtils.computeLoading(violation, violation.getLimit()))
            .upcomingAcceptableDuration(ComputationResultUtils.calculateUpcomingOverloadDuration(violation))
            .patlLimit(patlLimit)
            .patlLoading(ComputationResultUtils.computeLoading(violation, patlLimit))
            .nextLimitName(ComputationResultUtils.getNextLimitName(violation, network))
            .locationId(ComputationResultUtils.getViolationLocationId(violation, network))
            .acceptableDuration(ComputationResultUtils.calculateActualOverloadDuration(violation, network))
            .build();
    }
}
