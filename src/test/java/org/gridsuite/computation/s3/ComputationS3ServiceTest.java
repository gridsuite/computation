/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.computation.s3;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
class ComputationS3ServiceTest {

    public static final String PATH_IN_S3 = "path/in/s3";
    public static final String UPLOAD_FAILED_MESSAGE = "Upload failed";
    public static final String DOWNLOAD_FAILED_MESSAGE = "Download failed";

    private S3Client s3Client;
    private ComputationS3Service computationS3Service;

    @BeforeEach
    void setup() {
        s3Client = mock(S3Client.class);
        computationS3Service = new ComputationS3Service(s3Client, "ws-bucket");
    }

    @Test
    void uploadFileShouldSendSuccessful() throws IOException {
        // setup
        Path tempFile = Files.createTempFile("test", ".txt");
        Files.writeString(tempFile, "Normal case");

        // perform test
        computationS3Service.uploadFile(tempFile, PATH_IN_S3, "test.txt");

        // check result
        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        PutObjectRequest actualRequest = requestCaptor.getValue();

        assertThat(actualRequest.bucket()).isEqualTo("ws-bucket");
        assertThat(actualRequest.key()).isEqualTo(PATH_IN_S3);
        assertThat(actualRequest.metadata()).containsEntry(ComputationS3Service.METADATA_FILE_NAME, "test.txt");
    }

    @Test
    void uploadFileShouldThrowException() throws IOException {
        // setup
        Path tempFile = Files.createTempFile("test", ".txt");
        Files.writeString(tempFile, "Error case");

        // mock exception
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message(UPLOAD_FAILED_MESSAGE).build());

        // perform test and check
        assertThatThrownBy(() -> computationS3Service.uploadFile(tempFile, "key", "name.txt"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining(UPLOAD_FAILED_MESSAGE);
    }

    @Test
    void downloadFileShouldReturnInfos() throws IOException {
        // setup
        GetObjectResponse response = GetObjectResponse.builder()
                .metadata(Map.of(ComputationS3Service.METADATA_FILE_NAME, "download.txt"))
                .contentLength(4086L)
                .build();

        ResponseInputStream<GetObjectResponse> mockedStream =
                new ResponseInputStream<>(response, new ByteArrayInputStream("data".getBytes()));

        // mock return
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenReturn(mockedStream);

        // perform test
        S3InputStreamInfos result = computationS3Service.downloadFile(PATH_IN_S3);

        // check result
        assertThat(result.getFileName()).isEqualTo("download.txt");
        assertThat(result.getFileLength()).isEqualTo(4086L);
        assertThat(result.getInputStream()).isNotNull();
    }

    @Test
    void downloadFileShouldThrowException() {
        // setup
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().message(DOWNLOAD_FAILED_MESSAGE).build());

        // perform test and check
        assertThatThrownBy(() -> computationS3Service.downloadFile("bad-key"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining(DOWNLOAD_FAILED_MESSAGE);
    }
}
