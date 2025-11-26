/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation.error.utils;

import com.powsybl.ws.commons.error.BusinessErrorCode;


/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
public enum SpecificErrorCode implements BusinessErrorCode {
    MISC("specific.misc");

    private final String code;

    SpecificErrorCode(String code) {
        this.code = code;
    }

    public String value() {
        return code;
    }
}
