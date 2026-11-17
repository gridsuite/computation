/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation;

import com.powsybl.contingency.violations.BusBreakerViolationLocation;
import com.powsybl.contingency.violations.LimitViolation;
import com.powsybl.contingency.violations.LimitViolationType;
import com.powsybl.contingency.violations.NodeBreakerViolationLocation;
import com.powsybl.contingency.violations.ViolationLocation;
import com.powsybl.iidm.network.*;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import org.gridsuite.computation.utils.ComputationResultUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Jamal KHEYYAD <jamal.kheyyad at rte-international.com>
 */
class ComputationUtilTest {

    @Test
    void testLoadingUpcomingDurationAndViolationId() {
        LimitViolation currentViolation = createLimitViolation("line", LimitViolationType.CURRENT, "TATL", 600, 200, 150);
        assertEquals(75.0, ComputationResultUtils.computeLoading(currentViolation, currentViolation.getLimit()), 0.001);
        assertEquals(600, ComputationResultUtils.calculateUpcomingOverloadDuration(currentViolation));
        assertEquals("line_CURRENT_ONE_TATL", ComputationResultUtils.getViolationId(currentViolation));

        assertNull(ComputationResultUtils.computeLoading(currentViolation, null));
        assertNull(ComputationResultUtils.calculateUpcomingOverloadDuration(
            createLimitViolation("line", LimitViolationType.CURRENT, "TATL", 600, 200, 200)));
        assertNull(ComputationResultUtils.computeLoading(
            createLimitViolation("bus", LimitViolationType.HIGH_VOLTAGE, "low", 0, 1, 0.9), 1.0));
    }

    @Test
    void testPatlNextAndActualOverloadDuration() {
        Network network = mock(Network.class);
        Branch<?> branch = mock(Branch.class);
        CurrentLimits currentLimits = mock(CurrentLimits.class);
        LoadingLimits.TemporaryLimit tatl = mock(LoadingLimits.TemporaryLimit.class);
        LoadingLimits.TemporaryLimit temporaryLimit = mock(LoadingLimits.TemporaryLimit.class);
        when(network.getBranch("line")).thenReturn(branch);
        when(branch.getCurrentLimits(TwoSides.ONE)).thenReturn(Optional.of(currentLimits));
        when(currentLimits.getPermanentLimit()).thenReturn(100.0);
        when(currentLimits.getTemporaryLimits()).thenReturn(List.of(tatl, temporaryLimit));
        when(tatl.getName()).thenReturn("TATL");
        when(tatl.getValue()).thenReturn(200.0);
        when(tatl.getAcceptableDuration()).thenReturn(600);
        when(temporaryLimit.getName()).thenReturn("1m");
        when(temporaryLimit.getValue()).thenReturn(300.0);
        when(temporaryLimit.getAcceptableDuration()).thenReturn(60);

        LimitViolation permanentViolation = createLimitViolation(
            "line", LimitViolationType.CURRENT, LoadingLimits.DEFAULT_PERMANENT_LIMIT_NAME, 3600, 100, 90);
        assertEquals(100.0, ComputationResultUtils.getPatlLimit(permanentViolation, network));
        assertEquals("TATL", ComputationResultUtils.getNextLimitName(permanentViolation, network));

        LimitViolation tatlViolation = createLimitViolation("line", LimitViolationType.CURRENT, "TATL", 1800, 200, 150);
        assertEquals("1m", ComputationResultUtils.getNextLimitName(tatlViolation, network));
        assertEquals(600, ComputationResultUtils.calculateActualOverloadDuration(tatlViolation, network));
        assertEquals(1800, ComputationResultUtils.calculateUpcomingOverloadDuration(tatlViolation));
        assertNull(ComputationResultUtils.getNextLimitName(
            createLimitViolation("line", LimitViolationType.CURRENT, "1m", 60, 300, 250), network));

        LimitViolation overloadedViolation = createLimitViolation("line", LimitViolationType.CURRENT, "TATL", 60, 200, 250);
        assertEquals(60, ComputationResultUtils.calculateActualOverloadDuration(overloadedViolation, network));
        assertNull(ComputationResultUtils.calculateUpcomingOverloadDuration(overloadedViolation));

        LimitViolation belowPatlViolation = createLimitViolation("line", LimitViolationType.CURRENT, "1m", 60, 300, 90);
        assertNull(ComputationResultUtils.calculateActualOverloadDuration(belowPatlViolation, network));

        LimitViolation belowViolationLimit = createLimitViolation("line", LimitViolationType.CURRENT, "1m", 60, 400, 350);
        assertNull(ComputationResultUtils.calculateActualOverloadDuration(belowViolationLimit, network));
    }

    @Test
    void testMissingBranchHasNoPatlOrNextLimit() {
        Network network = mock(Network.class);
        LimitViolation violation = createLimitViolation("missing", LimitViolationType.CURRENT, "TATL", 60, 200, 150);

        assertNull(ComputationResultUtils.getPatlLimit(violation, network));
        assertNull(ComputationResultUtils.getNextLimitName(violation, network));
        assertNull(ComputationResultUtils.calculateActualOverloadDuration(violation, network));

        Branch<?> branchWithoutLimits = mock(Branch.class);
        when(network.getBranch("no-limits")).thenReturn(branchWithoutLimits);
        when(branchWithoutLimits.getCurrentLimits(TwoSides.ONE)).thenReturn(Optional.empty());
        LimitViolation noLimitsViolation = createLimitViolation("no-limits", LimitViolationType.CURRENT, "TATL", 60, 200, 150);
        assertNull(ComputationResultUtils.getPatlLimit(noLimitsViolation, network));
        assertNull(ComputationResultUtils.getNextLimitName(noLimitsViolation, network));
        assertNull(ComputationResultUtils.calculateActualOverloadDuration(noLimitsViolation, network));
    }

    static Network createBusBreakerNetwork() {
        Network network = new NetworkFactoryImpl().createNetwork("network", "test");
        Substation p1 = network.newSubstation()
                .setId("P1")
                .setCountry(Country.FR)
                .setTso("RTE")
                .setGeographicalTags("A")
                .add();
        VoltageLevel vl = p1.newVoltageLevel()
                .setId("VLGEN")
                .setNominalV(24.0)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();

        Bus ngen = vl.getBusBreakerView().newBus()
                .setId("NGEN")
                .add();

        Bus ngen2 = vl.getBusBreakerView().newBus()
            .setId("NGEN2")
            .add();

        vl.newLoad()
            .setId("LD")
            .setBus(ngen.getId())
            .setConnectableBus(ngen.getId())
            .setP0(600.0)
            .setQ0(200.0)
            .add();

        vl.newLoad()
            .setId("LD2")
            .setBus(ngen2.getId())
            .setConnectableBus(ngen2.getId())
            .setP0(600.0)
            .setQ0(200.0)
            .add();

        return network;
    }

    private static LimitViolation createLimitViolation(String subjectId,
                                                       LimitViolationType type,
                                                       String limitName,
                                                       int acceptableDuration,
                                                       double limit,
                                                       double value) {
        return new LimitViolation(subjectId, null, null, type, limitName, acceptableDuration, limit, 1.0, value, ThreeSides.ONE, null);
    }

    public static Network createNodeBreakerNetwork() {
        Network network = Network.create("network", "test");
        Substation s = network.newSubstation()
                .setId("S")
                .add();
        VoltageLevel vl = s.newVoltageLevel()
                .setId("VL1")
                .setNominalV(400)
                .setLowVoltageLimit(370.)
                .setHighVoltageLimit(420.)
                .setTopologyKind(TopologyKind.NODE_BREAKER)
                .add();
        vl.getNodeBreakerView().newBusbarSection()
                .setId("BBS1")
                .setNode(0)
                .add();
        vl.getNodeBreakerView().newBusbarSection()
                .setId("BBS2")
                .setNode(1)
                .add();

        vl.newLoad()
            .setId("LD")
            .setNode(3)
            .setP0(600.0)
            .setQ0(200.0)
            .add();

        vl.getNodeBreakerView().newBreaker()
            .setId("BR")
            .setOpen(false)
            .setNode1(0)
            .setNode2(3)
            .add();

        return network;
    }

    @Test
    void testViolationLocationIdBusBreaker() {
        Network network = createBusBreakerNetwork();

        LimitViolation limitViolation = mock(LimitViolation.class);

        when(limitViolation.getLimitType()).thenReturn(LimitViolationType.HIGH_VOLTAGE);
        when(limitViolation.getViolationLocation()).thenReturn(Optional.of(new BusBreakerViolationLocation(List.of("NGEN"))));
        assertEquals("VLGEN_0", ComputationResultUtils.getViolationLocationId(limitViolation, network));

        when(limitViolation.getViolationLocation()).thenReturn(Optional.of(new BusBreakerViolationLocation(List.of("NGEN2"))));
        assertEquals("VLGEN_1", ComputationResultUtils.getViolationLocationId(limitViolation, network));

        when(limitViolation.getViolationLocation()).thenReturn(Optional.of(new BusBreakerViolationLocation(List.of("NGEN", "NGEN2"))));
        when(limitViolation.getSubjectId()).thenReturn("VLGEN");
        assertEquals("VLGEN (VLGEN_0, VLGEN_1)", ComputationResultUtils.getViolationLocationId(limitViolation, network));

        when(limitViolation.getViolationLocation()).thenReturn(Optional.of(new BusBreakerViolationLocation(List.of())));
        when(limitViolation.getSubjectId()).thenReturn("VLGEN");
        assertEquals("VLGEN", ComputationResultUtils.getViolationLocationId(limitViolation, network));
    }

    @Test
    void testNoViolationLocationIdNodeBreaker() {
        Network network = createNodeBreakerNetwork();
        LimitViolation limitViolation = mock(LimitViolation.class);

        when(limitViolation.getLimitType()).thenReturn(LimitViolationType.CURRENT);
        when(limitViolation.getViolationLocation()).thenReturn(Optional.empty());
        when(limitViolation.getSubjectId()).thenReturn("subjectId");
        assertEquals(null, ComputationResultUtils.getViolationLocationId(limitViolation, network));
    }

    @Test
    void testNoViolationLocationIdBusBreaker() {
        Network network = createBusBreakerNetwork();
        LimitViolation limitViolation = mock(LimitViolation.class);

        when(limitViolation.getLimitType()).thenReturn(LimitViolationType.HIGH_VOLTAGE);
        when(limitViolation.getViolationLocation()).thenReturn(Optional.empty());
        when(limitViolation.getSubjectId()).thenReturn("subjectId");
        assertEquals("subjectId", ComputationResultUtils.getViolationLocationId(limitViolation, network));
    }

    @Test
    void testViolationLocationIdNodeBreaker() {
        Network network = createNodeBreakerNetwork();

        NodeBreakerViolationLocation nodeBreakerViolationLocation = mock(NodeBreakerViolationLocation.class);
        when(nodeBreakerViolationLocation.getType()).thenReturn(ViolationLocation.Type.NODE_BREAKER);
        when(nodeBreakerViolationLocation.getVoltageLevelId()).thenReturn("VL1");
        when(nodeBreakerViolationLocation.getNodes()).thenReturn(List.of());

        LimitViolation limitViolation = mock(LimitViolation.class);
        when(limitViolation.getLimitType()).thenReturn(LimitViolationType.HIGH_VOLTAGE);
        when(limitViolation.getViolationLocation()).thenReturn(Optional.of(nodeBreakerViolationLocation));
        when(limitViolation.getSubjectId()).thenReturn("VLHV1");

        String locationId = ComputationResultUtils.getViolationLocationId(limitViolation, network);
        assertEquals("VL1", locationId);

        when(nodeBreakerViolationLocation.getNodes()).thenReturn(List.of(0, 1));
        locationId = ComputationResultUtils.getViolationLocationId(limitViolation, network);
        assertEquals("VL1 (BBS1, BBS2)", locationId);

        when(nodeBreakerViolationLocation.getNodes()).thenReturn(List.of(0));
        locationId = ComputationResultUtils.getViolationLocationId(limitViolation, network);
        assertEquals("VL1_0", locationId);

        when(nodeBreakerViolationLocation.getNodes()).thenReturn(List.of(1));
        locationId = ComputationResultUtils.getViolationLocationId(limitViolation, network);
        assertEquals("VL1 (BBS2)", locationId);
    }
}
