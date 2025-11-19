/**
  Copyright (c) 2025, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation.utils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.iidm.network.Country;
import com.powsybl.security.LimitViolationType;
import org.gridsuite.computation.ComputationException;
import org.gridsuite.computation.dto.GlobalFilter;
import org.gridsuite.computation.dto.ResourceFilterDTO;
import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
class FilterUtilsTest {
    private final ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void testFromStringFiltersToDTO() throws Exception {
        List<ResourceFilterDTO> resourceFilterDTOList = FilterUtils.fromStringFiltersToDTO("", objectMapper);
        assertTrue(resourceFilterDTOList.isEmpty());

        resourceFilterDTOList = List.of(new ResourceFilterDTO(ResourceFilterDTO.DataType.TEXT, ResourceFilterDTO.Type.CONTAINS, "abc", "ID"),
                                        new ResourceFilterDTO(ResourceFilterDTO.DataType.NUMBER, ResourceFilterDTO.Type.EQUALS, 100., "activePower", 0.1));
        List<ResourceFilterDTO> actualResourceFilterDTOList = FilterUtils.fromStringFiltersToDTO(objectMapper.writeValueAsString(resourceFilterDTOList), objectMapper);

        assertEquals(2, actualResourceFilterDTOList.size());
        assertEquals(ResourceFilterDTO.DataType.TEXT, actualResourceFilterDTOList.getFirst().dataType());
        assertEquals(ResourceFilterDTO.Type.CONTAINS, actualResourceFilterDTOList.getFirst().type());
        assertEquals("abc", actualResourceFilterDTOList.getFirst().value());
        assertEquals("ID", actualResourceFilterDTOList.getFirst().column());
        assertNull(actualResourceFilterDTOList.get(0).tolerance());

        assertEquals(ResourceFilterDTO.DataType.NUMBER, actualResourceFilterDTOList.get(1).dataType());
        assertEquals(ResourceFilterDTO.Type.EQUALS, actualResourceFilterDTOList.get(1).type());
        assertEquals(100., actualResourceFilterDTOList.get(1).value());
        assertEquals("activePower", actualResourceFilterDTOList.get(1).column());
        assertEquals(0.1, actualResourceFilterDTOList.get(1).tolerance());
    }

    @Test
    void testFromStringGlobalFiltersToDTO() throws Exception {
        GlobalFilter globalFilter = FilterUtils.fromStringGlobalFiltersToDTO("", objectMapper);
        assertNull(globalFilter);

        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        globalFilter = new GlobalFilter();
        globalFilter.setNominalV(List.of("380", "225"));
        globalFilter.setCountryCode(List.of(Country.FR, Country.BE));
        globalFilter.setGenericFilter(List.of(uuid1, uuid2));
        globalFilter.setSubstationProperty(Map.of());
        globalFilter.setLimitViolationsTypes(List.of(LimitViolationType.CURRENT, LimitViolationType.LOW_VOLTAGE));
        GlobalFilter actualGlobalFilter = FilterUtils.fromStringGlobalFiltersToDTO(objectMapper.writeValueAsString(globalFilter), objectMapper);
        assertNotNull(actualGlobalFilter);
    }

    @Test
    void testInvalidFilterFormat() {
        assertThrows(UncheckedIOException.class, () -> FilterUtils.fromStringGlobalFiltersToDTO("titi", objectMapper), "The filter format is invalid.");
    }

    @Test
    void testFailCreateUtilityClassInstance() {
        assertThrows(IllegalCallerException.class, FilterUtils::new);
    }
}
