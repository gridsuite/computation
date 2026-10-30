/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation.utils;

import com.powsybl.contingency.violations.BusBreakerViolationLocation;
import com.powsybl.contingency.violations.LimitViolation;
import com.powsybl.contingency.violations.LimitViolationType;
import com.powsybl.contingency.violations.NodeBreakerViolationLocation;
import com.powsybl.contingency.violations.ViolationLocation;
import com.powsybl.iidm.network.*;
import org.springframework.lang.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static com.powsybl.contingency.violations.LimitViolationType.HIGH_VOLTAGE;
import static com.powsybl.contingency.violations.LimitViolationType.HIGH_VOLTAGE_ANGLE;
import static com.powsybl.contingency.violations.LimitViolationType.LOW_VOLTAGE;
import static com.powsybl.contingency.violations.LimitViolationType.LOW_VOLTAGE_ANGLE;
import static com.powsybl.contingency.violations.ViolationLocation.Type.NODE_BREAKER;
import static com.powsybl.iidm.network.IdentifiableType.BUSBAR_SECTION;

/**
 * @author Jamal KHEYYAD <jamal.kheyyad at rte-international.com>
 */
public final class ComputationResultUtils {

    private ComputationResultUtils() {
    }

    public static Double computeLoading(LimitViolation limitViolation, @Nullable Double limit) {
        return LimitViolationType.CURRENT.equals(limitViolation.getLimitType()) && limit != null
                ? 100 * limitViolation.getValue() / limit
                : null;
    }

    public static Double getPatlLimit(LimitViolation limitViolation, Network network) {
        String equipmentId = limitViolation.getSubjectId();
        Branch<?> branch = network.getBranch(equipmentId);
        ThreeSides limitViolationSide = limitViolation.getSide();
        if (branch == null || limitViolationSide == null) {
            return null;
        }

        Optional<CurrentLimits> currentLimits = branch.getCurrentLimits(limitViolationSide.toTwoSides());
        if (currentLimits.isPresent()) {
            return currentLimits.get().getPermanentLimit();
        }
        return null;
    }

    public static String getNextLimitName(LimitViolation limitViolation, Network network) {
        String equipmentId = limitViolation.getSubjectId();
        Branch<?> branch = network.getBranch(equipmentId);
        if (branch == null) {
            return null;
        }
        LoadingLimits.TemporaryLimit temporaryLimit = getNextTemporaryLimit(branch, limitViolation);
        return temporaryLimit != null ? temporaryLimit.getName() : null;
    }

    private static LoadingLimits.TemporaryLimit getNextTemporaryLimit(Branch<?> branch, LimitViolation limitViolation) {
        // limits are returned from the store by DESC duration / ASC value
        ThreeSides limitViolationSide = limitViolation.getSide();
        String limitName = limitViolation.getLimitName();
        if (limitViolationSide == null || limitName == null) {
            return null;
        }

        Optional<CurrentLimits> currentLimits = branch.getCurrentLimits(limitViolationSide.toTwoSides());
        if (currentLimits.isEmpty()) {
            return null;
        }

        Collection<LoadingLimits.TemporaryLimit> temporaryLimits = currentLimits.get().getTemporaryLimits();
        if (limitName.equals(LoadingLimits.DEFAULT_PERMANENT_LIMIT_NAME)) {
            return temporaryLimits.stream().findFirst().orElse(null);
        }

        Iterator<LoadingLimits.TemporaryLimit> temporaryLimitIterator = temporaryLimits.iterator();
        while (temporaryLimitIterator.hasNext()) {
            LoadingLimits.TemporaryLimit currentTemporaryLimit = temporaryLimitIterator.next();
            if (currentTemporaryLimit.getName().equals(limitViolation.getLimitName())) {
                return temporaryLimitIterator.hasNext() ? temporaryLimitIterator.next() : null;
            }
        }

        return null;
    }

    public static Integer calculateActualOverloadDuration(LimitViolation limitViolation, Network network) {
        if (limitViolation.getValue() > limitViolation.getLimit()) {
            return limitViolation.getAcceptableDuration();
        } else {
            String equipmentId = limitViolation.getSubjectId();
            LoadingLimits.TemporaryLimit tempLimit = null;

            Branch<?> branch = network.getBranch(equipmentId);
            if (branch != null) {
                tempLimit = getBranchLimitViolationAboveCurrentValue(branch, limitViolation);
            }
            return (tempLimit != null) ? tempLimit.getAcceptableDuration() : null;
        }
    }

    public static Integer calculateUpcomingOverloadDuration(LimitViolation limitViolation) {
        if (limitViolation.getValue() < limitViolation.getLimit()) {
            return limitViolation.getAcceptableDuration();
        }
        return null;
    }

    private static LoadingLimits.TemporaryLimit getBranchLimitViolationAboveCurrentValue(Branch<?> branch, LimitViolation limitViolation) {
        // limits are returned from the store by DESC duration / ASC value
        Optional<CurrentLimits> currentLimits = branch.getCurrentLimits(limitViolation.getSideAsTwoSides());
        if (currentLimits.isEmpty() || limitViolation.getValue() < currentLimits.get().getPermanentLimit()) {
            return null;
        } else {
            Optional<LoadingLimits.TemporaryLimit> nextTemporaryLimit = currentLimits.get().getTemporaryLimits().stream()
                .filter(tl -> limitViolation.getValue() < tl.getValue())
                .findFirst();
            if (nextTemporaryLimit.isPresent()) {
                return nextTemporaryLimit.get();
            }
        }
        return null;
    }

    public static String getViolationLocationId(LimitViolation limitViolation, Network network) {
        // LocationId only for voltage-based limit violations
        if (!Set.of(LOW_VOLTAGE, HIGH_VOLTAGE, LOW_VOLTAGE_ANGLE, HIGH_VOLTAGE_ANGLE).contains(limitViolation.getLimitType())) {
            return null;
        }

        Optional<ViolationLocation> violationLocation = limitViolation.getViolationLocation();
        if (violationLocation.isEmpty()) {
            return limitViolation.getSubjectId();
        }

        ViolationLocation location = violationLocation.get();
        if (location.getType() == NODE_BREAKER) {
            return getNodeBreakerViolationLocationId((NodeBreakerViolationLocation) location, network);
        } else {
            return getBusBreakerViolationLocationId((BusBreakerViolationLocation) location, network, limitViolation.getSubjectId());
        }
    }

    private static String getNodeBreakerViolationLocationId(NodeBreakerViolationLocation nodeBreakerViolationLocation, Network network) {
        VoltageLevel vl = network.getVoltageLevel(nodeBreakerViolationLocation.getVoltageLevelId());

        List<String> busBarIds = nodeBreakerViolationLocation.getNodes().stream()
                .map(node -> vl.getNodeBreakerView().getTerminal(node))
                .filter(Objects::nonNull)
                .map(Terminal::getConnectable)
                .filter(t -> t.getType() == BUSBAR_SECTION)
                .map(Identifiable::getId)
                .distinct()
                .toList();

        String busId = null;
        if (!busBarIds.isEmpty()) {
            busId = getBusId(vl, new HashSet<>(busBarIds));
        }
        return formatViolationLocationId(busId != null ? List.of() : busBarIds, busId != null ? busId : nodeBreakerViolationLocation.getVoltageLevelId());
    }

    private static String getBusId(VoltageLevel voltageLevel, Set<String> sjbIds) {
        Optional<Bus> bus = voltageLevel.getBusView()
            .getBusStream()
            .filter(b -> {
                Set<String> busSjbIds = b.getConnectedTerminalStream().map(Terminal::getConnectable).filter(c -> c.getType() == BUSBAR_SECTION).map(Connectable::getId).collect(Collectors.toSet());
                return busSjbIds.equals(sjbIds);
            })
            .findFirst();
        return bus.map(Identifiable::getId).orElse(null);
    }

    private static String formatViolationLocationId(List<String> elementsIds, String subjectId) {
        return !elementsIds.isEmpty() ?
            subjectId + " (" + String.join(", ", elementsIds) + ")" :
            subjectId;
    }

    private static String getBusBreakerViolationLocationId(BusBreakerViolationLocation busBreakerViolationLocation, Network network, String subjectId) {
        List<String> busBreakerIds = busBreakerViolationLocation
                .getBusView(network)
                .getBusStream()
                .map(Identifiable::getId)
                .distinct()
                .toList();

        return busBreakerIds.size() == 1 ? formatViolationLocationId(List.of(), busBreakerIds.getFirst()) : formatViolationLocationId(busBreakerIds, subjectId);
    }

}
