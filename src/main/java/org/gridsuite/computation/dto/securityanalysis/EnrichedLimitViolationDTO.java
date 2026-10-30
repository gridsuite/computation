/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation.dto.securityanalysis;

import com.powsybl.contingency.violations.LimitViolation;
import com.powsybl.contingency.violations.LimitViolationType;
import com.powsybl.iidm.network.ThreeSides;
import lombok.Getter;
import org.gridsuite.computation.utils.ComputationResultUtils;
import org.springframework.lang.Nullable;

@Getter
public class EnrichedLimitViolationDTO {
    private final String subjectId;
    private final double limit;
    private final String limitName;
    private final String operationalLimitsGroupId;
    private final LimitViolationType limitType;
    private final double limitReduction;
    private final double value;
    private final ThreeSides side;
    private final Double loading;
    private final Integer upcomingAcceptableDuration;
    private final Double patlLimit;
    private final Double patlLoading;
    private final String nextLimitName;
    private final String locationId;
    private final Integer acceptableDuration;

    public EnrichedLimitViolationDTO(LimitViolation limitViolation, @Nullable LimitViolationEnrichmentDTO enrichment) {
        subjectId = limitViolation.getSubjectId();
        limit = limitViolation.getLimit();
        limitName = limitViolation.getLimitName();
        operationalLimitsGroupId = limitViolation.getOperationalLimitsGroupId();
        limitType = limitViolation.getLimitType();
        limitReduction = limitViolation.getLimitReduction();
        value = limitViolation.getValue();
        side = limitViolation.getSide();
        loading = ComputationResultUtils.computeLoading(limitViolation, limitViolation.getLimit());
        upcomingAcceptableDuration = ComputationResultUtils.calculateUpcomingOverloadDuration(limitViolation);
        patlLimit = enrichment != null ? enrichment.getPatlLimit() : null;
        patlLoading = enrichment != null ? enrichment.getPatlLoading() : null;
        nextLimitName = enrichment != null ? enrichment.getNextLimitName() : null;
        locationId = enrichment != null ? enrichment.getLocationId() : null;
        acceptableDuration = enrichment != null ? enrichment.getAcceptableDuration() : null;
    }
}
