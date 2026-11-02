/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation.dto.securityanalysis;

import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.violations.LimitViolation;
import com.powsybl.contingency.violations.LimitViolationType;
import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import com.powsybl.security.LimitViolationsResult;
import com.powsybl.security.PostContingencyComputationStatus;
import com.powsybl.security.SecurityAnalysisResult;
import com.powsybl.security.results.ConnectivityResult;
import com.powsybl.security.results.NetworkResult;
import com.powsybl.security.results.PostContingencyResult;
import org.gridsuite.computation.utils.ComputationResultUtils;
import org.gridsuite.computation.utils.SecurityAnalysisEnricher;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Radouane Khouadri <redouane.khouadri_externe at rte-france.com>
 */
class SecurityAnalysisEnricherTest {
    @Test
    void enrichesPreContingencyPostContingencyAndEmptyResults() {
        LimitViolation violation = new LimitViolation("line", null, null, LimitViolationType.CURRENT, "TATL", 600, 200, 1, 150, ThreeSides.ONE, null);
        SecurityAnalysisResult result = new SecurityAnalysisResult(
            new LimitViolationsResult(List.of(violation)),
            LoadFlowResult.ComponentResult.Status.CONVERGED,
            List.of(
                createPostContingencyResult("with-violation", new LimitViolationsResult(List.of(violation))),
                createPostContingencyResult("empty", null)
            )
        );

        EnrichedSecurityAnalysisResultDTO enrichedResult = SecurityAnalysisEnricher.enrich(
            result,
            new NetworkFactoryImpl().createNetwork("network", "test")
        );
        String violationId = ComputationResultUtils.getViolationId(violation);

        assertSame(result, enrichedResult.getResult());
        assertEnrichment(enrichedResult.getPreContingencyEnrichment().get(violationId));
        assertEnrichment(enrichedResult.getPostContingencyEnrichment().get("with-violation").get(violationId));
        assertTrue(enrichedResult.getPostContingencyEnrichment().get("empty").isEmpty());
    }

    private static void assertEnrichment(LimitViolationEnrichmentDTO enrichment) {
        assertNotNull(enrichment);
        assertEquals(75.0, enrichment.getLoading(), 0.001);
        assertEquals(600, enrichment.getUpcomingAcceptableDuration());
    }

    private static PostContingencyResult createPostContingencyResult(String id, LimitViolationsResult violationsResult) {
        return new PostContingencyResult(
            new Contingency(id, new BranchContingency("line")),
            PostContingencyComputationStatus.CONVERGED,
            violationsResult,
            NetworkResult.empty(),
            ConnectivityResult.empty(),
            1.0
        );
    }
}
