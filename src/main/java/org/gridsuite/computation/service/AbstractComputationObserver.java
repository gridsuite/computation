/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Mathieu Deharbe <mathieu.deharbe at rte-france.com>
 * @param <R> powsybl Result class specific to the computation
 * @param <P> powsybl and gridsuite parameters specifics to the computation
 */
@Getter(AccessLevel.PROTECTED)
public abstract class AbstractComputationObserver<R, P> {
    protected static final String OBSERVATION_PREFIX = "app.computation.";
    protected static final String PROVIDER_TAG_NAME = "provider";
    protected static final String TYPE_TAG_NAME = "type";
    protected static final String STATUS_TAG_NAME = "status";
    protected static final String COMPUTATION_TOTAL_COUNTER_NAME = OBSERVATION_PREFIX + "count";
    protected static final String COMPUTATION_CURRENT_COUNTER_NAME = OBSERVATION_PREFIX + "current.count";
    private static final String UNKNOWN_PROVIDER = "unknown-provider";

    private final ObservationRegistry observationRegistry;
    private final MeterRegistry meterRegistry;

    private final Map<String, Integer> currentComputationsCount = new ConcurrentHashMap<>();

    protected AbstractComputationObserver(@NonNull ObservationRegistry observationRegistry, @NonNull MeterRegistry meterRegistry) {
        this.observationRegistry = observationRegistry;
        this.meterRegistry = meterRegistry;
    }

    protected abstract String getComputationType();

    protected Observation createObservation(String name, AbstractComputationRunContext<P> runContext) {
        return Observation.createNotStarted(OBSERVATION_PREFIX + name, observationRegistry)
                .lowCardinalityKeyValue(TYPE_TAG_NAME, getComputationType())
                .lowCardinalityKeyValue(PROVIDER_TAG_NAME, runContext.getProvider() != null ? runContext.getProvider() : UNKNOWN_PROVIDER);
    }

    public <E extends Throwable> void observe(String name, AbstractComputationRunContext<P> runContext, Observation.CheckedRunnable<E> callable) throws E {
        createObservation(name, runContext).observeChecked(callable);
    }

    public <T, E extends Throwable> T observe(String name, AbstractComputationRunContext<P> runContext, Observation.CheckedCallable<T, E> callable) throws E {
        return createObservation(name, runContext).observeChecked(callable);
    }

    public <T extends R, E extends Throwable> T observeRun(
            String name, AbstractComputationRunContext<P> runContext, Observation.CheckedCallable<T, E> callable) throws E {
        T result;
        String provider = runContext.getProvider() != null ? runContext.getProvider() : UNKNOWN_PROVIDER;
        try {
            incrementCurrentCount(provider);
            result = createObservation(name, runContext).observeChecked(callable);
        } finally {
            decrementCurrentCount(provider);
        }
        incrementTotalCount(provider, result);
        return result;
    }

    private void incrementCurrentCount(String provider) {
        currentComputationsCount.compute(provider, (k, v) -> (v == null) ? 1 : v + 1);
        updateCurrentCountMetric(provider);
    }

    private void decrementCurrentCount(String provider) {
        currentComputationsCount.compute(provider, (k, v) -> v > 1 ? v - 1 : 0);
        updateCurrentCountMetric(provider);
    }

    private void updateCurrentCountMetric(String provider) {
        Gauge.builder(COMPUTATION_CURRENT_COUNTER_NAME, () -> currentComputationsCount.get(provider))
            .tag(TYPE_TAG_NAME, getComputationType())
            .tag(PROVIDER_TAG_NAME, provider)
            .register(meterRegistry);
    }

    private void incrementTotalCount(String provider, R result) {
        Counter.builder(COMPUTATION_TOTAL_COUNTER_NAME)
                .tag(PROVIDER_TAG_NAME, provider)
                .tag(TYPE_TAG_NAME, getComputationType())
                .tag(STATUS_TAG_NAME, getResultStatus(result))
                .register(meterRegistry)
                .increment();
    }

    protected abstract String getResultStatus(R res);
}
