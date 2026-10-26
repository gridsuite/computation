/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation.dto.securityanalysis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author Radouane Khouadri <redouane.khouadri_externe at rte-france.com>
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrichedLimitViolationDTO {
    private Double patlLimit;
    private Double patlLoading;
    private String nextLimitName;
    private String locationId;
    private Integer acceptableDuration;
}
