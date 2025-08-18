/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.computation.s3;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
class S3AutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(S3AutoConfiguration.class))
            .withBean(S3Client.class, () -> mock(S3Client.class));

    @Test
    void s3ServiceBeanShouldBeCreatedWhenS3Enabled() {
        contextRunner
                .withPropertyValues(
                        "computation.s3.enabled=true",
                        "spring.cloud.aws.bucket=test-bucket"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(ComputationS3Service.class);
                    ComputationS3Service service = context.getBean(ComputationS3Service.class);
                    assertThat(service).isNotNull();
                });
    }

    @Test
    void s3ServiceBeanShouldNotBeCreatedWhenS3EnabledMissingOrFalse() {
        contextRunner
                .run(context -> assertThat(context).doesNotHaveBean(ComputationS3Service.class));

        contextRunner
                .withPropertyValues("computation.s3.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ComputationS3Service.class));
    }
}

