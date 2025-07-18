/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
class ComputationExceptionTest {

    @Test
    void testMessageConstructor() {
        var e = new ComputationException("test");
        assertEquals("test", e.getMessage());
    }

    @Test
    void testMessageAndThrowableConstructor() {
        var cause = new RuntimeException("test");
        var e = new ComputationException("test", cause);
        assertEquals("test", e.getMessage());
        assertEquals(cause, e.getCause());
    }
}
