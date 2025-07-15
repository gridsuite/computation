/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.computation.service;

import com.powsybl.commons.report.ReportNode;
import org.gridsuite.computation.ComputationConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureWebClient;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * @author Mathieu Deharbe <mathieu.deharbe_externe at rte-france.com
 */
@RestClientTest(ReportService.class)
@AutoConfigureWebClient(registerRestTemplate = true)
@ContextConfiguration(classes = {ComputationConfig.class, ReportService.class})
class ReportServiceTest {
    private static final UUID REPORT_UUID = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");
    private static final UUID REPORT_ERROR_UUID = UUID.fromString("9928181c-7977-4592-ba19-88027e4254e4");
    private static final String REPORT_JSON = "{\"version\":\"3.0\",\"dictionaries\":{\"default\":{\"test\":\"a test\"}},\"reportRoot\":{\"messageKey\":\"test\"}}";

    @Autowired
    private ReportService reportService;

    @Autowired
    private MockRestServiceServer server;

    @AfterEach
    void tearDown() {
        server.verify();
    }

    @Test
    void testSendReport() {
        final ReportNode reportNode = ReportNode.newRootReportNode()
                                .withResourceBundles("i18n.reports")
                                .withMessageTemplate("test")
                                .build();
        server.expect(MockRestRequestMatchers.method(HttpMethod.PUT))
                .andExpect(MockRestRequestMatchers.requestTo("http://report-server/v1/reports/" + REPORT_UUID))
                .andExpect(MockRestRequestMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockRestRequestMatchers.content().json(REPORT_JSON))
                .andRespond(MockRestResponseCreators.withSuccess());
        assertThatNoException().isThrownBy(() -> reportService.sendReport(REPORT_UUID, reportNode));
    }

    @Test
    void testSendReportFailed() {
        final ReportNode reportNode = ReportNode.newRootReportNode()
                                .withResourceBundles("i18n.reports")
                                .withMessageTemplate("test")
                                .build();
        server.expect(MockRestRequestMatchers.method(HttpMethod.PUT))
              .andExpect(MockRestRequestMatchers.requestTo("http://report-server/v1/reports/" + REPORT_ERROR_UUID))
              .andRespond(MockRestResponseCreators.withServerError());
        assertThatThrownBy(() -> reportService.sendReport(REPORT_ERROR_UUID, reportNode)).isInstanceOf(RestClientException.class);
    }

    @Test
    void testDeleteReport() {
        server.expect(MockRestRequestMatchers.method(HttpMethod.DELETE))
                .andExpect(MockRestRequestMatchers.requestTo("http://report-server/v1/reports/" + REPORT_UUID + "?errorOnReportNotFound=false"))
                .andExpect(MockRestRequestMatchers.content().bytes(new byte[0]))
                .andRespond(MockRestResponseCreators.withSuccess());
        assertThatNoException().isThrownBy(() -> reportService.deleteReport(REPORT_UUID));
    }

    @Test
    void testDeleteReportFailed() {
        server.expect(MockRestRequestMatchers.method(HttpMethod.DELETE))
                .andExpect(MockRestRequestMatchers.requestTo("http://report-server/v1/reports/" + REPORT_ERROR_UUID + "?errorOnReportNotFound=false"))
                .andExpect(MockRestRequestMatchers.content().bytes(new byte[0]))
                .andRespond(MockRestResponseCreators.withServerError());
        assertThatThrownBy(() -> reportService.deleteReport(REPORT_ERROR_UUID)).isInstanceOf(RestClientException.class);
    }
}

