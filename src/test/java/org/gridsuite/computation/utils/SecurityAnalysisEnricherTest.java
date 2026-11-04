/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation.utils;

import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.violations.BusBreakerViolationLocation;
import com.powsybl.contingency.violations.LimitViolation;
import com.powsybl.contingency.violations.LimitViolationType;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import com.powsybl.security.LimitViolationsResult;
import com.powsybl.security.PostContingencyComputationStatus;
import com.powsybl.security.SecurityAnalysisResult;
import com.powsybl.security.results.ConnectivityResult;
import com.powsybl.security.results.NetworkResult;
import com.powsybl.security.results.PostContingencyResult;
import org.gridsuite.computation.dto.securityanalysis.EnrichedSecurityAnalysisResultDTO;
import org.gridsuite.computation.dto.securityanalysis.LimitViolationEnrichmentDTO;
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
        LimitViolation firstVoltageViolation = new LimitViolation("VLGEN", null, null, LimitViolationType.HIGH_VOLTAGE,
            "high", 60, 1.05, 1, 1.1, ThreeSides.ONE, new BusBreakerViolationLocation(List.of("NGEN")));
        LimitViolation secondVoltageViolation = new LimitViolation("VLGEN", null, null, LimitViolationType.HIGH_VOLTAGE,
            "high", 60, 1.05, 1, 1.2, ThreeSides.ONE, new BusBreakerViolationLocation(List.of("NGEN2")));
        SecurityAnalysisResult result = new SecurityAnalysisResult(
            new LimitViolationsResult(List.of(violation, firstVoltageViolation, secondVoltageViolation)),
            LoadFlowResult.ComponentResult.Status.CONVERGED,
            List.of(
                createPostContingencyResult("with-violation", new LimitViolationsResult(List.of(violation, firstVoltageViolation, secondVoltageViolation))),
                createPostContingencyResult("empty", null)
            )
        );

        EnrichedSecurityAnalysisResultDTO enrichedResult = SecurityAnalysisEnricher.enrich(result, createBusBreakerNetwork());
        assertSame(result, enrichedResult.getResult());
        assertEquals(3, enrichedResult.getPreContingencyEnrichment().size());
        assertEnrichment(enrichedResult.getPreContingencyEnrichment().getFirst());
        assertEquals("VLGEN_0", enrichedResult.getPreContingencyEnrichment().get(1).getLocationId());
        assertEquals("VLGEN_1", enrichedResult.getPreContingencyEnrichment().get(2).getLocationId());
        List<LimitViolationEnrichmentDTO> postEnrichment = enrichedResult.getPostContingencyEnrichment().get("with-violation");
        assertEquals(3, postEnrichment.size());
        assertEnrichment(postEnrichment.getFirst());
        assertEquals("VLGEN_0", postEnrichment.get(1).getLocationId());
        assertEquals("VLGEN_1", postEnrichment.get(2).getLocationId());
        assertEquals(ComputationResultUtils.getViolationId(firstVoltageViolation), ComputationResultUtils.getViolationId(secondVoltageViolation));
        assertTrue(enrichedResult.getPostContingencyEnrichment().get("empty").isEmpty());
    }

    private static Network createBusBreakerNetwork() {
        Network network = new NetworkFactoryImpl().createNetwork("network", "test");
        Substation substation = network.newSubstation()
            .setId("P1")
            .setCountry(Country.FR)
            .add();
        VoltageLevel voltageLevel = substation.newVoltageLevel()
            .setId("VLGEN")
            .setNominalV(24.0)
            .setTopologyKind(TopologyKind.BUS_BREAKER)
            .add();
        voltageLevel.getBusBreakerView().newBus().setId("NGEN").add();
        voltageLevel.getBusBreakerView().newBus().setId("NGEN2").add();
        voltageLevel.newLoad()
            .setId("LD")
            .setBus("NGEN")
            .setConnectableBus("NGEN")
            .setP0(600.0)
            .setQ0(200.0)
            .add();
        voltageLevel.newLoad()
            .setId("LD2")
            .setBus("NGEN2")
            .setConnectableBus("NGEN2")
            .setP0(600.0)
            .setQ0(200.0)
            .add();
        return network;
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
