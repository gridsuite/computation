/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.powsybl.commons.PowsyblException;
import com.powsybl.computation.local.LocalComputationConfig;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManager;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.ws.commons.ZipUtils;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.WithAssertions;
import org.gridsuite.computation.dto.ReportInfos;
import org.gridsuite.computation.s3.ComputationS3Service;
import org.gridsuite.computation.s3.S3InputStreamInfos;
import org.gridsuite.computation.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

import static org.gridsuite.computation.s3.ComputationS3Service.S3_DELIMITER;
import static org.gridsuite.computation.s3.ComputationS3Service.S3_SERVICE_NOT_AVAILABLE_MESSAGE;
import static org.gridsuite.computation.service.NotificationService.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith({ MockitoExtension.class })
@Slf4j
class ComputationTest implements WithAssertions {
    private static final String COMPUTATION_TYPE = "mockComputation";
    public static final String S3_DEBUG_SUBPATH = "debug";

    public static final String WORKING_DIR = "test";
    public static final String S3_DEBUG_FILE_ZIP = WORKING_DIR + ".zip";
    public static final String S3_KEY = S3_DEBUG_SUBPATH + S3_DELIMITER + S3_DEBUG_FILE_ZIP;

    protected FileSystem fileSystem;
    protected Path tmpDir;

    @Mock
    private VariantManager variantManager;
    @Mock
    private NetworkStoreService networkStoreService;
    @Mock
    private ReportService reportService;
    @Mock
    private ExecutionService executionService;
    private final UuidGeneratorService uuidGeneratorService = new UuidGeneratorService();
    @Mock
    private StreamBridge publisher;
    private NotificationService notificationService;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private Network network;
    @Mock
    private ComputationS3Service computationS3Service;

    private enum MockComputationStatus {
        NOT_DONE,
        RUNNING,
        COMPLETED
    }

    private static class MockComputationResultService extends AbstractComputationResultService<MockComputationStatus> {
        Map<UUID, MockComputationStatus> mockDBStatus = new HashMap<>();

        @Override
        public void insertStatus(List<UUID> resultUuids, MockComputationStatus status) {
            resultUuids.forEach(uuid ->
                    mockDBStatus.put(uuid, status));
        }

        @Override
        public void delete(UUID resultUuid) {
            mockDBStatus.remove(resultUuid);
        }

        @Override
        public void deleteAll() {
            mockDBStatus.clear();
        }

        @Override
        public MockComputationStatus findStatus(UUID resultUuid) {
            return mockDBStatus.get(resultUuid);
        }
    }

    private static class MockComputationObserver extends AbstractComputationObserver<Object, Object> {
        protected MockComputationObserver(@NonNull ObservationRegistry observationRegistry, @NonNull MeterRegistry meterRegistry) {
            super(observationRegistry, meterRegistry);
        }

        @Override
        protected String getComputationType() {
            return COMPUTATION_TYPE;
        }

        @Override
        protected String getResultStatus(Object res) {
            return res != null ? "OK" : "NOK";
        }
    }

    private static class MockComputationRunContext extends AbstractComputationRunContext<Object> {
        // makes the mock computation to behave in a specific way
        @Getter @Setter
        ComputationResultWanted computationResWanted = ComputationResultWanted.SUCCESS;

        protected MockComputationRunContext(UUID networkUuid, String variantId, String receiver, ReportInfos reportInfos,
                                            String userId, String provider, Object parameters) {
            super(networkUuid, variantId, receiver, reportInfos, userId, provider, parameters);
        }
    }

    private static class MockComputationResultContext extends AbstractResultContext<MockComputationRunContext> {
        protected MockComputationResultContext(UUID resultUuid, MockComputationRunContext runContext) {
            super(resultUuid, runContext);
        }
    }

    private static class MockComputationService extends AbstractComputationService<MockComputationRunContext, MockComputationResultService, MockComputationStatus> {
        protected MockComputationService(NotificationService notificationService, MockComputationResultService resultService, ComputationS3Service computationS3Service, ObjectMapper objectMapper, UuidGeneratorService uuidGeneratorService, String defaultProvider) {
            super(notificationService, resultService, computationS3Service, objectMapper, uuidGeneratorService, defaultProvider);
        }

        @Override
        public List<String> getProviders() {
            return List.of();
        }

        @Override
        public UUID runAndSaveResult(MockComputationRunContext runContext) {
            return RESULT_UUID;
        }
    }

    private enum ComputationResultWanted {
        SUCCESS,
        FAIL,
        CANCELLED,
        COMPLETED
    }

    private static class MockComputationWorkerService extends AbstractWorkerService<Object, MockComputationRunContext, Object, MockComputationResultService> {
        protected MockComputationWorkerService(NetworkStoreService networkStoreService, NotificationService notificationService, ReportService reportService, MockComputationResultService resultService, ComputationS3Service computationS3Service, ExecutionService executionService, AbstractComputationObserver<Object, Object> observer, ObjectMapper objectMapper) {
            super(networkStoreService, notificationService, reportService, resultService, computationS3Service, executionService, observer, objectMapper);
        }

        @Override
        protected AbstractResultContext<MockComputationRunContext> fromMessage(Message<String> message) {
            return resultContext;
        }

        @Override
        protected void saveResult(Network network, AbstractResultContext<MockComputationRunContext> resultContext, Object result) {
            // Empty implementation - this is a mock/test implementation that doesn't need to persist results
            // The actual result saving is handled by the real implementation or is not needed for testing
        }

        @Override
        protected String getComputationType() {
            return COMPUTATION_TYPE;
        }

        @Override
        protected CompletableFuture<Object> getCompletableFuture(MockComputationRunContext runContext, String provider, UUID resultUuid) {
            final CompletableFuture<Object> completableFuture = new CompletableFuture<>();
            switch (runContext.getComputationResWanted()) {
                case CANCELLED:
                    completableFuture.completeExceptionally(new CancellationException("Computation cancelled"));
                    break;
                case FAIL:
                    completableFuture.completeExceptionally(new RuntimeException("Computation failed"));
                    break;
                case SUCCESS:
                    return CompletableFuture.supplyAsync(Object::new);
                case COMPLETED:
                    return CompletableFuture.completedFuture(null);
            }
            return completableFuture;
        }

        public void addFuture(UUID id, CompletableFuture<Object> future) {
            this.futures.put(id, future);
        }
    }

    private MockComputationWorkerService workerService;
    private MockComputationService computationService;
    private static MockComputationResultContext resultContext;
    final UUID networkUuid = UUID.fromString("11111111-1111-1111-1111-111111111111");
    final UUID reportUuid = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final UUID RESULT_UUID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    final String reporterId = "44444444-4444-4444-4444-444444444444";
    final String userId = "MockComputation_UserId";
    final String receiver = "MockComputation_Receiver";
    final String provider = "MockComputation_Provider";
    Message<String> message;
    MockComputationRunContext runContext;
    @Spy
    MockComputationResultService resultService;

    @BeforeEach
    void init() throws IOException {
        // used to initialize the computation manager
        fileSystem = Jimfs.newFileSystem(Configuration.unix());
        tmpDir = Files.createDirectory(fileSystem.getPath("tmp"));

        notificationService = new NotificationService(publisher);
        workerService = new MockComputationWorkerService(
                networkStoreService,
                notificationService,
                reportService,
                resultService,
                computationS3Service,
                executionService,
                new MockComputationObserver(ObservationRegistry.create(), new SimpleMeterRegistry()),
                objectMapper
        );
        computationService = new MockComputationService(notificationService, resultService, computationS3Service, objectMapper, uuidGeneratorService, provider);

        MessageBuilder<String> builder = MessageBuilder
                .withPayload("")
                .setHeader(HEADER_RESULT_UUID, RESULT_UUID.toString())
                .setHeader(HEADER_RECEIVER, receiver)
                .setHeader(HEADER_USER_ID, userId);
        message = builder.build();

        runContext = new MockComputationRunContext(networkUuid, null, receiver,
                new ReportInfos(reportUuid, reporterId, COMPUTATION_TYPE), userId, provider, new Object());
        resultContext = new MockComputationResultContext(RESULT_UUID, runContext);
    }

    @AfterEach
    void tearDown() throws IOException {
        fileSystem.close();
    }

    private void initComputationExecution() {
        when(networkStoreService.getNetwork(eq(networkUuid), any(PreloadingStrategy.class)))
                .thenReturn(network);
        when(network.getVariantManager()).thenReturn(variantManager);
    }

    @Test
    void testComputationSuccess() {
        // inits
        initComputationExecution();
        runContext.setComputationResWanted(ComputationResultWanted.SUCCESS);

        // execution / cleaning
        workerService.consumeRun().accept(message);

        // test the course
        verify(notificationService.getPublisher(), times(1)).send(eq("publishResult-out-0"), isA(Message.class));
    }

    @Test
    void testComputationFailed() {
        // inits
        initComputationExecution();
        runContext.setComputationResWanted(ComputationResultWanted.FAIL);

        // execution / cleaning
        assertThrows(ComputationException.class, () -> workerService.consumeRun().accept(message));
        assertNull(resultService.findStatus(RESULT_UUID));
    }

    @Test
    void testStopComputationSendsCancelMessage() {
        computationService.stop(RESULT_UUID, receiver);
        verify(notificationService.getPublisher(), times(1)).send(eq("publishCancel-out-0"), isA(Message.class));
    }

    @Test
    void testComputationCancelledInConsumeRun() {
        // inits
        initComputationExecution();
        runContext.setComputationResWanted(ComputationResultWanted.CANCELLED);

        // execution / cleaning
        workerService.consumeRun().accept(message);

        // test the course
        assertNull(resultService.findStatus(RESULT_UUID));
        verify(notificationService.getPublisher(), times(0)).send(eq("publishResult-out-0"), isA(Message.class));
    }

    @Test
    void testComputationCancelledInConsumeCancel() {
        MockComputationStatus baseStatus = MockComputationStatus.RUNNING;
        computationService.setStatus(List.of(RESULT_UUID), baseStatus);
        assertEquals(baseStatus, computationService.getStatus(RESULT_UUID));

        CompletableFuture<Object> futureThatCouldBeCancelled = Mockito.mock(CompletableFuture.class);
        when(futureThatCouldBeCancelled.cancel(true)).thenReturn(true);
        workerService.addFuture(RESULT_UUID, futureThatCouldBeCancelled);

        workerService.consumeCancel().accept(message);
        assertNull(resultService.findStatus(RESULT_UUID));
        verify(notificationService.getPublisher(), times(1)).send(eq("publishStopped-out-0"), isA(Message.class));
    }

    @Test
    void testComputationCancelFailed() {
        MockComputationStatus baseStatus = MockComputationStatus.RUNNING;
        computationService.setStatus(List.of(RESULT_UUID), baseStatus);
        assertEquals(baseStatus, computationService.getStatus(RESULT_UUID));

        CompletableFuture<Object> futureThatCouldNotBeCancelled = Mockito.mock(CompletableFuture.class);
        when(futureThatCouldNotBeCancelled.cancel(true)).thenReturn(false);
        workerService.addFuture(RESULT_UUID, futureThatCouldNotBeCancelled);

        workerService.consumeCancel().accept(message);
        assertNotNull(resultService.findStatus(RESULT_UUID));
        verify(notificationService.getPublisher(), times(1)).send(eq("publishCancelFailed-out-0"), isA(Message.class));
    }

    @Test
    void testComputationCancelFailsIfNoMatchingFuture() {
        workerService.consumeCancel().accept(message);
        assertNull(resultService.findStatus(RESULT_UUID));
        verify(notificationService.getPublisher(), times(1)).send(eq("publishCancelFailed-out-0"), isA(Message.class));
    }

    @Test
    void testComputationCancelledBeforeRunReturnsNoResult() {
        workerService.consumeCancel().accept(message);

        initComputationExecution();
        workerService.consumeRun().accept(message);
        verify(notificationService.getPublisher(), times(0)).send(eq("publishResult-out-0"), isA(Message.class));
    }

    @Test
    void testProcessDebugWithS3Service() throws IOException {
        // Setup
        initComputationExecution();
        when(executionService.getComputationManager()).thenReturn(new LocalComputationManager(new LocalComputationConfig(tmpDir, 1), ForkJoinPool.commonPool()));
        runContext.setComputationResWanted(ComputationResultWanted.SUCCESS);
        runContext.setDebug(true);

        // Mock ZipUtils
        try (var mockedStatic = mockStatic(ZipUtils.class)) {
            mockedStatic.when(() -> ZipUtils.zip(any(Path.class), any(Path.class))).thenAnswer(invocation -> null);
            workerService.consumeRun().accept(message);

            // Verify interactions
            verify(resultService).saveDebugFileLocation(eq(RESULT_UUID), anyString());
            verify(computationS3Service).uploadFile(any(Path.class), anyString(), anyString(), eq(30));
            verify(notificationService.getPublisher(), times(1 /* for result message */))
                    .send(eq("publishResult-out-0"), isA(Message.class));
            verify(notificationService.getPublisher(), times(1 /* for debug message */))
                    .send(eq("publishDebug-out-0"), isA(Message.class));
        }
    }

    @Test
    void testConsumeRunWithoutDebug() {
        // Setup
        initComputationExecution();
        runContext.setComputationResWanted(ComputationResultWanted.SUCCESS);
        runContext.setDebug(null);

        // Execute
        workerService.consumeRun().accept(message);

        // Verify interactions
        verifyNoInteractions(computationS3Service, resultService);
        verify(notificationService.getPublisher(), times(1 /* only result */))
                .send(eq("publishResult-out-0"), isA(Message.class));
        verify(notificationService.getPublisher(), times(0 /* no debug */))
                .send(eq("publishDebug-out-0"), isA(Message.class));
    }

    @Test
    void testProcessDebugWithoutS3Service() {
        // Setup worker service without ComputationS3Service
        workerService = new MockComputationWorkerService(
                networkStoreService,
                notificationService,
                reportService,
                resultService,
                null,
                executionService,
                new MockComputationObserver(ObservationRegistry.create(), new SimpleMeterRegistry()),
                objectMapper
        );
        initComputationExecution();
        runContext.setComputationResWanted(ComputationResultWanted.SUCCESS);
        runContext.setDebug(true);

        // Execute
        workerService.consumeRun().accept(message);

        // Verify
        verify(notificationService.getPublisher()).send(eq("publishDebug-out-0"), argThat((Message<String> msg) ->
                msg.getHeaders().get(HEADER_ERROR_MESSAGE).equals(S3_SERVICE_NOT_AVAILABLE_MESSAGE)));
        verifyNoInteractions(computationS3Service, resultService);
    }

    @Test
    void testProcessDebugWithIOException() throws IOException {
        // Setup
        initComputationExecution();
        when(executionService.getComputationManager()).thenReturn(new LocalComputationManager(new LocalComputationConfig(tmpDir, 1), ForkJoinPool.commonPool()));
        runContext.setComputationResWanted(ComputationResultWanted.SUCCESS);
        runContext.setDebug(true);

        // Mock ZipUtils to throw IOException
        try (var mockedStatic = mockStatic(ZipUtils.class)) {
            mockedStatic.when(() -> ZipUtils.zip(any(Path.class), any(Path.class)))
                    .thenThrow(new UncheckedIOException("Zip error", new IOException()));
            workerService.consumeRun().accept(message);

            // Verify interactions
            verify(computationS3Service, never()).uploadFile(any(), any(), any(), anyInt());
            verify(resultService, never()).saveDebugFileLocation(any(), any());
            verify(notificationService.getPublisher()).send(eq("publishDebug-out-0"), argThat((Message<String> msg) ->
                    msg.getHeaders().get(HEADER_ERROR_MESSAGE).equals("Zip error")));
        }
    }

    @Test
    void testDownloadDebugFileSuccess() throws IOException {
        // Setup
        String fileName = S3_DEBUG_FILE_ZIP;
        long fileLength = 1024L;
        ByteArrayInputStream inputStream = new ByteArrayInputStream(new byte[1024]);
        S3InputStreamInfos s3InputStreamInfos = S3InputStreamInfos.builder()
                .inputStream(inputStream)
                .fileName(fileName)
                .fileLength(fileLength)
                .build();
        when(resultService.findDebugFileLocation(RESULT_UUID)).thenReturn(S3_KEY);
        when(computationS3Service.downloadFile(S3_KEY)).thenReturn(s3InputStreamInfos);

        // Execute
        ResponseEntity<?> response = computationService.downloadDebugFile(RESULT_UUID);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(InputStreamResource.class);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(response.getHeaders().getContentLength()).isEqualTo(fileLength);
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_DISPOSITION)).contains("attachment; filename=\"" + fileName + "\"");
        verify(computationS3Service).downloadFile(S3_KEY);
    }

    @Test
    void testDownloadDebugFileS3NotAvailable() throws IOException {
        // Setup
        computationService = new MockComputationService(notificationService, resultService, null, objectMapper, uuidGeneratorService, "defaultProvider");

        // Execute & Check
        assertThrows(PowsyblException.class, () -> computationService.downloadDebugFile(RESULT_UUID), "S3 service not available");
        verify(computationS3Service, never()).downloadFile(any());
    }

    @Test
    void testDownloadDebugFileNotFound() throws IOException {
        // Setup
        when(resultService.findDebugFileLocation(RESULT_UUID)).thenReturn(null);

        // Execute
        ResponseEntity<?> response = computationService.downloadDebugFile(RESULT_UUID);

        // Check
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(computationS3Service, never()).downloadFile(any());
    }

    @Test
    void testDownloadDebugFileIOException() throws IOException {
        // Setup
        when(resultService.findDebugFileLocation(RESULT_UUID)).thenReturn(S3_KEY);
        when(computationS3Service.downloadFile(S3_KEY)).thenThrow(new IOException("S3 error"));

        // Act
        ResponseEntity<?> response = computationService.downloadDebugFile(RESULT_UUID);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(computationS3Service).downloadFile(S3_KEY);
    }

}
