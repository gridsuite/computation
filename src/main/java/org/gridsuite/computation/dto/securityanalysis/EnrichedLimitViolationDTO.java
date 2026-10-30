/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation.dto.securityanalysis;

import com.powsybl.contingency.violations.LimitViolationType;
import com.powsybl.iidm.network.ThreeSides;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder(toBuilder = true)
@Jacksonized
public class EnrichedLimitViolationDTO {
    private final String subjectId;
    private final String subjectName;
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

}
