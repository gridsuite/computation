/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation.utils.annotations;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedList;
import java.util.List;

/**
 * @author Anis Touri <anis.touri at rte-france.com
 */
@Component
public class PostCompletionAdapter implements TransactionSynchronization {
    private static final ThreadLocal<List<Runnable>> RUNNABLE = new ThreadLocal<>();

    // register a new runnable for post completion execution
    public void execute(Runnable runnable) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            List<Runnable> runnables = RUNNABLE.get();
            if (runnables == null) {
                runnables = new LinkedList<>();
                RUNNABLE.set(runnables);
            }
            runnables.add(runnable);
            TransactionSynchronizationManager.registerSynchronization(this);
        } else {
            // if transaction synchronisation is not active
            runnable.run();
        }
    }

    @Override
    public void afterCompletion(int status) {
        List<Runnable> runnables = RUNNABLE.get();
        runnables.forEach(Runnable::run);
        RUNNABLE.remove();
    }
}
