/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.computation.s3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@AutoConfiguration
@ConditionalOnProperty(name = "computation.s3.enabled", havingValue = "true")
public class S3AutoConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(S3AutoConfiguration.class);
    @Value("${spring.cloud.aws.bucket:ws-bucket}")
    private String bucketName;

    @Bean
    public ComputationS3Service s3Service(S3Client s3Client) {
        LOGGER.info("Configuring ComputationS3Service with bucket: {}", bucketName);
        return new ComputationS3Service(s3Client, bucketName);
    }
}
