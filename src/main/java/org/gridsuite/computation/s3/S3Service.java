/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.computation.s3;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
public class S3Service {

    public static final String S3_DELIMITER = "/";
    public static final String S3_SERVICE_NOT_AVAILABLE_MESSAGE = "S3 service not available";

    public static final String METADATA_FILE_NAME = "file-name";

    private final S3Client s3Client;

    private final String bucketName;

    public S3Service(S3Client s3Client, String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    public void uploadFile(Path filePath, String s3Key, String fileName, Integer expireAfterMinutes) throws IOException {
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .metadata(Map.of(METADATA_FILE_NAME, fileName))
                    .tagging(expireAfterMinutes != null ? "expire-after-minutes=" + expireAfterMinutes : null)
                    .build();
            s3Client.putObject(putRequest, RequestBody.fromFile(filePath));
        } catch (SdkException e) {
            throw new IOException("Error occurred while uploading file to S3: " + e.getMessage());
        }
    }

    public S3InputStreamInfos downloadFile(String s3Key) throws IOException {
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();
            ResponseInputStream<GetObjectResponse> inputStream = s3Client.getObject(getRequest);
            return S3InputStreamInfos.builder()
                    .inputStream(inputStream)
                    .fileName(inputStream.response().metadata().get(METADATA_FILE_NAME))
                    .fileLength(inputStream.response().contentLength())
                    .build();
        } catch (SdkException e) {
            throw new IOException("Error occurred while downloading file from S3: " + e.getMessage());
        }
    }
}
