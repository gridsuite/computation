/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.computation.ComputationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * @author Mathieu Deharbe <mathieu.deharbe at rte-france.com>
 * @param <R> powsybl Result class specific to the computation
 * @param <C> Run context specific to a computation, including parameters
 * @param <P> powsybl and gridsuite Parameters specifics to the computation
 * @param <S> result service specific to the computation
 */
public abstract class AbstractWorkerService<R, C extends AbstractComputationRunContext<P>, P, S extends AbstractComputationResultService<?>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractWorkerService.class);

    protected final Lock lockRunAndCancel = new ReentrantLock();
    protected final ObjectMapper objectMapper;
    protected final NetworkStoreService networkStoreService;
    protected final ReportService reportService;
    protected final ExecutionService executionService;
    protected final NotificationService notificationService;
    protected final AbstractComputationObserver<R, P> observer;
    protected final Map<UUID, CompletableFuture<R>> futures = new ConcurrentHashMap<>();
    protected final Map<UUID, CancelContext> cancelComputationRequests = new ConcurrentHashMap<>();
    protected final S resultService;

    protected AbstractWorkerService(NetworkStoreService networkStoreService,
                                    NotificationService notificationService,
                                    ReportService reportService,
                                    S resultService,
                                    ExecutionService executionService,
                                    AbstractComputationObserver<R, P> observer,
                                    ObjectMapper objectMapper) {
        this.networkStoreService = networkStoreService;
        this.notificationService = notificationService;
        this.reportService = reportService;
        this.resultService = resultService;
        this.executionService = executionService;
        this.observer = observer;
        this.objectMapper = objectMapper;
    }

    protected PreloadingStrategy getNetworkPreloadingStrategy() {
        return PreloadingStrategy.COLLECTION;
    }

    protected Network getNetwork(UUID networkUuid, String variantId) {
        try {
            Network network = networkStoreService.getNetwork(networkUuid, getNetworkPreloadingStrategy());
            String variant = StringUtils.isBlank(variantId) ? VariantManagerConstants.INITIAL_VARIANT_ID : variantId;
            network.getVariantManager().setWorkingVariant(variant);
            return network;
        } catch (PowsyblException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    protected void cleanResultsAndPublishCancel(UUID resultUuid, String receiver) {
        resultService.delete(resultUuid);
        notificationService.publishStop(resultUuid, receiver, getComputationType());
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("{} (resultUuid='{}')",
                    NotificationService.getCancelMessage(getComputationType()),
                    resultUuid);
        }
    }

    private boolean cancelAsync(CancelContext cancelContext) {
        lockRunAndCancel.lock();
        boolean isCanceled = false;
        try {
            cancelComputationRequests.put(cancelContext.resultUuid(), cancelContext);

            // find the completableFuture associated with result uuid
            CompletableFuture<R> future = futures.get(cancelContext.resultUuid());
            if (future != null) {
                isCanceled = future.cancel(true);  // cancel computation in progress
                if (isCanceled) {
                    cleanResultsAndPublishCancel(cancelContext.resultUuid(), cancelContext.receiver());
                }
            }
        } finally {
            lockRunAndCancel.unlock();
        }
        return isCanceled;
    }

    protected abstract AbstractResultContext<C> fromMessage(Message<String> message);

    protected boolean resultCanBeSaved(R result) {
        return result != null;
    }

    public Consumer<Message<String>> consumeRun() {
        return message -> {
            AbstractResultContext<C> resultContext = fromMessage(message);
            AtomicReference<ReportNode> rootReporter = new AtomicReference<>(ReportNode.NO_OP);
            try {
                Network network = getNetwork(resultContext.getRunContext().getNetworkUuid(),
                        resultContext.getRunContext().getVariantId());
                resultContext.getRunContext().setNetwork(network);
                observer.observe("global.run", resultContext.getRunContext(), () -> {
                    long startTime = System.nanoTime();
                    R result = run(resultContext.getRunContext(), resultContext.getResultUuid(), rootReporter);

                    LOGGER.info("Just run in {}s", TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime));

                    if (resultCanBeSaved(result)) {
                        startTime = System.nanoTime();
                        observer.observe("results.save", resultContext.getRunContext(), () -> saveResult(network, resultContext, result));

                        LOGGER.info("Stored in {}s", TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime));

                        sendResultMessage(resultContext, result);
                        LOGGER.info("{} complete (resultUuid='{}')", getComputationType(), resultContext.getResultUuid());
                    }
                });
            } catch (CancellationException e) {
                // Do nothing
            } catch (Exception e) {
                resultService.delete(resultContext.getResultUuid());
                this.handleNonCancellationException(resultContext, e, rootReporter);
                throw new ComputationException(String.format("%s: %s", NotificationService.getFailedMessage(getComputationType()), e.getMessage()), e.getCause());
            } finally {
                clean(resultContext);
            }
        };
    }

    /**
     * Perform cleaning
     * @param resultContext The context of the computation
     */
    protected void clean(AbstractResultContext<C> resultContext) {
        futures.remove(resultContext.getResultUuid());
        cancelComputationRequests.remove(resultContext.getResultUuid());
    }

    /**
     * Handle exception in consumeRun that is not a CancellationException
     * @param resultContext The context of the computation
     * @param exception The exception to handle
     */
    protected void handleNonCancellationException(AbstractResultContext<C> resultContext, Exception exception, AtomicReference<ReportNode> rootReporter) {
    }

    public Consumer<Message<String>> consumeCancel() {
        return message -> {
            CancelContext cancelContext = CancelContext.fromMessage(message);
            boolean isCancelled = cancelAsync(cancelContext);
            if (!isCancelled) {
                notificationService.publishCancelFailed(cancelContext.resultUuid(), cancelContext.receiver(), getComputationType(), cancelContext.userId());
            }
        };
    }

    protected abstract void saveResult(Network network, AbstractResultContext<C> resultContext, R result);

    protected void sendResultMessage(AbstractResultContext<C> resultContext, R ignoredResult) {
        notificationService.sendResultMessage(resultContext.getResultUuid(), resultContext.getRunContext().getReceiver(),
                resultContext.getRunContext().getUserId(), null);
    }

    /**
     * Do some extra task before running the computation, e.g. print log or init extra data for the run context
     * @param ignoredRunContext This context may be used for further computation in overriding classes
     */
    protected void preRun(C ignoredRunContext) {
        LOGGER.info("Run {} computation...", getComputationType());
    }

    protected R run(C runContext, UUID resultUuid, AtomicReference<ReportNode> rootReporter) {
        String provider = runContext.getProvider();
        ReportNode reportNode = ReportNode.NO_OP;

        if (runContext.getReportInfos() != null && runContext.getReportInfos().reportUuid() != null) {
            final String reportType = runContext.getReportInfos().computationType();
            String rootReporterId = runContext.getReportInfos().reporterId();
            ReportNode rootReporterNode = ReportNode.newRootReportNode()
                    .withAllResourceBundlesFromClasspath()
                    .withMessageTemplate("ws.commons.rootReporterId")
                    .withUntypedValue("rootReporterId", rootReporterId).build();
            rootReporter.set(rootReporterNode);
            reportNode = rootReporter.get().newReportNode().withMessageTemplate("ws.commons.reportType")
                    .withUntypedValue("reportType", reportType)
                    .withUntypedValue("optionalProvider", provider != null ? " (" + provider + ")" : "").add();
            // Delete any previous computation logs
            observer.observe("report.delete",
                    runContext, () -> reportService.deleteReport(runContext.getReportInfos().reportUuid()));
        }
        runContext.setReportNode(reportNode);

        preRun(runContext);
        CompletableFuture<R> future = runAsync(runContext, provider, resultUuid);
        R result = future == null ? null : observer.observeRun("run", runContext, future::join);
        postRun(runContext, rootReporter, result);
        return result;
    }

    /**
     * Do some extra task after running the computation
     * @param runContext This context may be used for extra task in overriding classes
     * @param rootReportNode root of the reporter tree
     * @param ignoredResult The result of the computation
     */
    protected void postRun(C runContext, AtomicReference<ReportNode> rootReportNode, R ignoredResult) {
        if (runContext.getReportInfos().reportUuid() != null) {
            observer.observe("report.send", runContext, () -> reportService.sendReport(runContext.getReportInfos().reportUuid(), rootReportNode.get()));
        }
    }

    protected CompletableFuture<R> runAsync(
            C runContext,
            String provider,
            UUID resultUuid) {
        lockRunAndCancel.lock();
        try {
            if (resultUuid != null && cancelComputationRequests.get(resultUuid) != null) {
                return null;
            }
            CompletableFuture<R> future = getCompletableFuture(runContext, provider, resultUuid);
            if (resultUuid != null) {
                futures.put(resultUuid, future);
            }
            return future;
        } finally {
            lockRunAndCancel.unlock();
        }
    }

    protected abstract String getComputationType();

    protected abstract CompletableFuture<R> getCompletableFuture(C runContext, String provider, UUID resultUuid);
}
