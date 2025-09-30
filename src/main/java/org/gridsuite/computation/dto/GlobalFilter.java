/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation.dto;

import com.powsybl.security.LimitViolationType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * @author maissa Souissi <maissa.souissi at rte-france.com>
 */
@Getter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class GlobalFilter extends org.gridsuite.filter.globalfilter.GlobalFilter {
    // TODO move it to filterparameters in loadflow-server or manage it in server endpoint
    private List<LimitViolationType> limitViolationsTypes;
}
