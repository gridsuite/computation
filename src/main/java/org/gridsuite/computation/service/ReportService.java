/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Objects;
import java.util.UUID;

/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */
@Service
public class ReportService {

    static final String REPORT_API_VERSION = "v1";
    private static final String DELIMITER = "/";
    private static final String QUERY_PARAM_REPORT_THROW_ERROR = "errorOnReportNotFound";
    @Setter
    private String reportServerBaseUri;

    private final RestClient restClient;

    private final ObjectMapper objectMapper;

    public ReportService(ObjectMapper objectMapper,
                         @Value("${gridsuite.services.report-server.base-uri:http://report-server/}") String reportServerBaseUri,
                         RestClient.Builder restClientBuilder) {
        this.reportServerBaseUri = reportServerBaseUri;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.defaultStatusHandler(new DefaultResponseErrorHandler()).build();
    }

    private String getReportServerURI() {
        return this.reportServerBaseUri + DELIMITER + REPORT_API_VERSION + DELIMITER + "reports" + DELIMITER;
    }

    public void sendReport(UUID reportUuid, ReportNode reportNode) {
        Objects.requireNonNull(reportUuid);

        var path = UriComponentsBuilder.fromPath("{reportUuid}")
            .buildAndExpand(reportUuid)
            .toUriString();
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            String str = objectMapper.writeValueAsString(reportNode);
            restClient.method(HttpMethod.PUT)
                    .uri(getReportServerURI() + path)
                    .headers(httpHeaders -> httpHeaders.addAll(headers))
                    .body(str)
                    .retrieve()
                    .toEntity(ReportNode.class);
        } catch (JsonProcessingException error) {
            throw new PowsyblException("Error sending report", error);
        }
    }

    public void deleteReport(UUID reportUuid) {
        Objects.requireNonNull(reportUuid);

        var path = UriComponentsBuilder.fromPath("{reportUuid}")
                .queryParam(QUERY_PARAM_REPORT_THROW_ERROR, false)
                .buildAndExpand(reportUuid)
                .toUriString();
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        restClient.method(HttpMethod.DELETE)
                .uri(getReportServerURI() + path)
                .headers(httpHeaders -> httpHeaders.addAll(headers))
                .retrieve()
                .toBodilessEntity();
    }
}
