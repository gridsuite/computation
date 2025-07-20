/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.computation.service;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.iidm.network.VariantManager;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import org.gridsuite.computation.dto.GlobalFilter;
import org.gridsuite.computation.dto.ResourceFilterDTO;
import org.gridsuite.filter.AbstractFilter;
import org.gridsuite.filter.expertfilter.ExpertFilter;
import org.gridsuite.filter.expertfilter.expertrule.*;
import org.gridsuite.filter.identifierlistfilter.IdentifiableAttributes;
import org.gridsuite.filter.utils.EquipmentType;
import org.gridsuite.filter.utils.FilterServiceUtils;
import org.gridsuite.filter.utils.expertfilter.CombinatorType;
import org.gridsuite.filter.utils.expertfilter.FieldType;
import org.gridsuite.filter.utils.expertfilter.OperatorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * @author Rehili Ghazwa <ghazwa.rehili at rte-france.com>
 */
@ExtendWith(MockitoExtension.class)
class FilterServiceTest {

    @Mock
    private NetworkStoreService networkStoreService;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private Network network;

    @Mock
    private VariantManager variantManager;

    @Mock
    private AbstractFilterService filterService;

    private static final String FILTER_SERVER_BASE_URI = "http://localhost:8080";
    private static final String VARIANT_ID = "testVariant";
    private static final UUID NETWORK_UUID = UUID.randomUUID();
    private static final UUID FILTER_UUID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(filterService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(filterService, "filterServerBaseUri", FILTER_SERVER_BASE_URI);
        ReflectionTestUtils.setField(filterService, "networkStoreService", networkStoreService);
    }

    @Test
    void shouldReturnEmptyListWhenFiltersUuidsIsEmpty() {
        when(filterService.getFilters(anyList())).thenCallRealMethod();
        List<UUID> emptyList = Collections.emptyList();
        List<AbstractFilter> result = filterService.getFilters(emptyList);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldCallRestTemplateAndReturnFilters() {
        when(filterService.getFilters(anyList())).thenCallRealMethod();
        List<UUID> filterUuids = List.of(FILTER_UUID);
        List<AbstractFilter> expectedFilters = Collections.singletonList(mock(AbstractFilter.class));
        ResponseEntity<List<AbstractFilter>> responseEntity = new ResponseEntity<>(expectedFilters, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class))).thenReturn(responseEntity);
        List<AbstractFilter> result = filterService.getFilters(filterUuids);
        assertEquals(expectedFilters, result);
        verify(restTemplate).exchange(contains("v1/filters/metadata"), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class)
        );
    }

    @Test
    void shouldThrowPowsyblExceptionWhenHttpError() {
        when(filterService.getFilters(anyList())).thenCallRealMethod();
        List<UUID> filterUuids = List.of(FILTER_UUID);
        HttpStatusCodeException httpException = mock(HttpStatusCodeException.class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class)
        )).thenThrow(httpException);
        PowsyblException exception = assertThrows(PowsyblException.class, () -> filterService.getFilters(filterUuids));
        assertTrue(exception.getMessage().contains("Filters not found"));
        assertTrue(exception.getMessage().contains(FILTER_UUID.toString()));
    }

    @Test
    void shouldReturnNetworkWhenSuccessful() {
        when(filterService.getNetwork(any(), any())).thenCallRealMethod();
        when(networkStoreService.getNetwork(NETWORK_UUID, PreloadingStrategy.COLLECTION)).thenReturn(network);
        when(network.getVariantManager()).thenReturn(variantManager);
        Network result = filterService.getNetwork(NETWORK_UUID, VARIANT_ID);
        assertEquals(network, result);
        verify(variantManager).setWorkingVariant(VARIANT_ID);
    }

    @Test
    void shouldThrowResponseStatusExceptionWhenPowsyblException() {
        when(filterService.getNetwork(any(), any())).thenCallRealMethod();
        PowsyblException powsyblException = new PowsyblException("Network not found");
        when(networkStoreService.getNetwork(NETWORK_UUID, PreloadingStrategy.COLLECTION)).thenThrow(powsyblException);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> filterService.getNetwork(NETWORK_UUID, VARIANT_ID));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Network not found", exception.getReason());
    }

    @ParameterizedTest
    @MethodSource("expertRulesData")
    void shouldCreateExpertRules(List<String> values, FieldType fieldType, Class<?> expectedRuleType, boolean shouldBeEmpty) {
        when(filterService.createNumberExpertRules(any(), any())).thenCallRealMethod();
        List<AbstractExpertRule> result = filterService.createNumberExpertRules(values, fieldType);
        if (shouldBeEmpty) {
            assertNotNull(result);
            assertTrue(result.isEmpty());
        } else {
            assertNotNull(result);
            assertEquals(values.size(), result.size());
            for (AbstractExpertRule rule : result) {
                assertInstanceOf(expectedRuleType, rule);
                NumberExpertRule numberRule = (NumberExpertRule) rule;
                assertEquals(fieldType, numberRule.getField());
                assertEquals(OperatorType.EQUALS, numberRule.getOperator());
            }
        }
    }

    private static Stream<Arguments> expertRulesData() {
        return Stream.of(
                Arguments.of(null, FieldType.NOMINAL_VOLTAGE, NumberExpertRule.class, true),
                Arguments.of(Arrays.asList("400.0", "225.0"), FieldType.NOMINAL_VOLTAGE, NumberExpertRule.class, false)
        );
    }

    @ParameterizedTest
    @MethodSource("enumRulesData")
    void shouldCreateEnumRules(List<Country> values, FieldType fieldType, Class<?> expectedRuleType, boolean shouldBeEmpty) {
        when(filterService.createEnumExpertRules(any(), any())).thenCallRealMethod();

        List<AbstractExpertRule> result = filterService.createEnumExpertRules(values, fieldType);

        if (shouldBeEmpty) {
            assertNotNull(result);
            assertTrue(result.isEmpty());
        } else {
            assertNotNull(result);
            assertEquals(values.size(), result.size());
            for (AbstractExpertRule rule : result) {
                assertInstanceOf(expectedRuleType, rule);
                EnumExpertRule enumRule = (EnumExpertRule) rule;
                assertEquals(fieldType, enumRule.getField());
                assertEquals(OperatorType.EQUALS, enumRule.getOperator());
            }
        }
    }

    private static Stream<Arguments> enumRulesData() {
        return Stream.of(
                Arguments.of(null, FieldType.COUNTRY, EnumExpertRule.class, true),
                Arguments.of(Arrays.asList(Country.FR, Country.DE), FieldType.COUNTRY, EnumExpertRule.class, false)
        );
    }

    @Test
    void shouldCreateCorrectPropertiesRule() {
        when(filterService.createPropertiesRule(any(), any(), any())).thenCallRealMethod();
        String property = "testProperty";
        List<String> values = Arrays.asList("value1", "value2");
        AbstractExpertRule result = filterService.createPropertiesRule(property, values, FieldType.SUBSTATION_PROPERTIES);
        assertNotNull(result);
        assertInstanceOf(PropertiesExpertRule.class, result);
        PropertiesExpertRule propertiesRule = (PropertiesExpertRule) result;
        assertEquals(CombinatorType.OR, propertiesRule.getCombinator());
        assertEquals(OperatorType.IN, propertiesRule.getOperator());
        assertEquals(FieldType.SUBSTATION_PROPERTIES, propertiesRule.getField());
        assertEquals(property, propertiesRule.getPropertyName());
        assertEquals(values, propertiesRule.getPropertyValues());
    }

    @Test
    void shouldCreateCombinatorRule() {
        when(filterService.createCombination(any(), any())).thenCallRealMethod();
        List<AbstractExpertRule> rules = Collections.singletonList(mock(AbstractExpertRule.class));
        AbstractExpertRule result = filterService.createCombination(CombinatorType.AND, rules);
        assertNotNull(result);
        assertInstanceOf(CombinatorExpertRule.class, result);
        CombinatorExpertRule combinatorRule = (CombinatorExpertRule) result;
        assertEquals(CombinatorType.AND, combinatorRule.getCombinator());
        assertEquals(rules, combinatorRule.getRules());
    }

    @ParameterizedTest
    @MethodSource("orCombinationData")
    void shouldCreateOrCombination(List<AbstractExpertRule> rules, boolean expectEmpty, boolean expectSingle) {
        when(filterService.createOrCombination(any())).thenCallRealMethod();
        if (!expectEmpty && !expectSingle) {
            when(filterService.createCombination(any(), any())).thenCallRealMethod();
        }
        Optional<AbstractExpertRule> result = filterService.createOrCombination(rules);
        if (expectEmpty) {
            assertFalse(result.isPresent());
        } else if (expectSingle) {
            assertTrue(result.isPresent());
            assertEquals(rules.getFirst(), result.get());
        } else {
            assertTrue(result.isPresent());
            assertInstanceOf(CombinatorExpertRule.class, result.get());
            CombinatorExpertRule combinatorRule = (CombinatorExpertRule) result.get();
            assertEquals(CombinatorType.OR, combinatorRule.getCombinator());
        }
    }

    private static Stream<Arguments> orCombinationData() {
        return Stream.of(
                Arguments.of(Collections.emptyList(), true, false),
                Arguments.of(Collections.singletonList(mock(AbstractExpertRule.class)), false, true),
                Arguments.of(Arrays.asList(mock(AbstractExpertRule.class), mock(AbstractExpertRule.class)), false, false)
        );
    }

    @Test
    void shouldReturnEmptyWhenCombineFilterResultsInputIsEmpty() {
        when(filterService.combineFilterResults(any(), anyBoolean())).thenCallRealMethod();
        List<List<String>> filterResults = Collections.emptyList();
        List<String> result = filterService.combineFilterResults(filterResults, true);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnIntersectionWhenUsingAndLogic() {
        when(filterService.combineFilterResults(any(), anyBoolean())).thenCallRealMethod();
        List<List<String>> filterResults = Arrays.asList(
                Arrays.asList("item1", "item2", "item3"),
                Arrays.asList("item2", "item3", "item4"),
                Arrays.asList("item2", "item5")
        );
        List<String> result = filterService.combineFilterResults(filterResults, true);
        assertEquals(1, result.size());
        assertTrue(result.contains("item2"));
    }

    @Test
    void shouldReturnUnionWhenUsingOrLogic() {
        when(filterService.combineFilterResults(any(), anyBoolean())).thenCallRealMethod();
        List<List<String>> filterResults = Arrays.asList(
                Arrays.asList("item1", "item2"),
                Arrays.asList("item3", "item4"),
                List.of("item5")
        );
        List<String> result = filterService.combineFilterResults(filterResults, false);
        assertEquals(5, result.size());
        assertTrue(result.containsAll(Arrays.asList("item1", "item2", "item3", "item4", "item5")));
    }

    @Test
    void shouldReturnIdsFromFilteredNetwork() {
        when(filterService.filterNetwork(any(), any())).thenCallRealMethod();
        AbstractFilter filter = mock(AbstractFilter.class);
        try (MockedStatic<FilterServiceUtils> mockStatic = mockStatic(FilterServiceUtils.class)) {
            List<IdentifiableAttributes> attributes = Arrays.asList(
                    createIdentifiableAttributes("id1"),
                    createIdentifiableAttributes("id2")
            );
            mockStatic.when(() -> FilterServiceUtils.getIdentifiableAttributes(filter, network, filterService)).thenReturn(attributes);
            List<String> result = filterService.filterNetwork(filter, network);
            assertEquals(Arrays.asList("id1", "id2"), result);
        }
    }

    @ParameterizedTest
    @MethodSource("voltageLevelRuleData")
    void shouldCreateVoltageLevelIdRule(TwoSides side, FieldType expectedFieldType) {
        when(filterService.createVoltageLevelIdRule(any(), any())).thenCallRealMethod();
        UUID filterUuid = UUID.randomUUID();
        AbstractExpertRule result = filterService.createVoltageLevelIdRule(filterUuid, side);
        assertNotNull(result);
        assertInstanceOf(FilterUuidExpertRule.class, result);
        FilterUuidExpertRule rule = (FilterUuidExpertRule) result;
        assertEquals(OperatorType.IS_PART_OF, rule.getOperator());
        assertEquals(expectedFieldType, rule.getField());
        assertTrue(rule.getValues().contains(filterUuid.toString()));
    }

    private static Stream<Arguments> voltageLevelRuleData() {
        return Stream.of(
                Arguments.of(TwoSides.ONE, FieldType.VOLTAGE_LEVEL_ID_1),
                Arguments.of(TwoSides.TWO, FieldType.VOLTAGE_LEVEL_ID_2)
        );
    }

    @ParameterizedTest
    @MethodSource("fieldTypeData")
    void shouldReturnCorrectFieldTypes(String methodType, EquipmentType equipmentType, List<FieldType> expectedFields) {
        switch (methodType) {
            case "nominal" -> when(filterService.getNominalVoltageFieldType(any())).thenCallRealMethod();
            case "country" -> when(filterService.getCountryCodeFieldType(any())).thenCallRealMethod();
            case "substation" -> when(filterService.getSubstationPropertiesFieldTypes(any())).thenCallRealMethod();
        }
        List<FieldType> result = switch (methodType) {
            case "nominal" -> filterService.getNominalVoltageFieldType(equipmentType);
            case "country" -> filterService.getCountryCodeFieldType(equipmentType);
            case "substation" -> filterService.getSubstationPropertiesFieldTypes(equipmentType);
            default -> Collections.emptyList();
        };
        assertEquals(expectedFields, result);
    }

    private static Stream<Arguments> fieldTypeData() {
        return Stream.of(
                // Nominal voltage
                Arguments.of("nominal", EquipmentType.LINE, List.of(FieldType.NOMINAL_VOLTAGE_1, FieldType.NOMINAL_VOLTAGE_2)),
                Arguments.of("nominal", EquipmentType.TWO_WINDINGS_TRANSFORMER, List.of(FieldType.NOMINAL_VOLTAGE_1, FieldType.NOMINAL_VOLTAGE_2)),
                Arguments.of("nominal", EquipmentType.VOLTAGE_LEVEL, List.of(FieldType.NOMINAL_VOLTAGE)),
                Arguments.of("nominal", EquipmentType.GENERATOR, Collections.emptyList()),

                // Country code
                Arguments.of("country", EquipmentType.VOLTAGE_LEVEL, List.of(FieldType.COUNTRY)),
                Arguments.of("country", EquipmentType.TWO_WINDINGS_TRANSFORMER, List.of(FieldType.COUNTRY)),
                Arguments.of("country", EquipmentType.LINE, List.of(FieldType.COUNTRY_1, FieldType.COUNTRY_2)),
                Arguments.of("country", EquipmentType.GENERATOR, Collections.emptyList()),

                // Substation properties
                Arguments.of("substation", EquipmentType.LINE, List.of(FieldType.SUBSTATION_PROPERTIES_1, FieldType.SUBSTATION_PROPERTIES_2)),
                Arguments.of("substation", EquipmentType.GENERATOR, List.of(FieldType.SUBSTATION_PROPERTIES))
        );
    }

    @Test
    void shouldReturnEmptyWhenNoExpertFiltersProvided() {
        when(filterService.buildAllExpertRules(any(), any())).thenCallRealMethod();
        when(filterService.buildNominalVoltageRules(any(), any())).thenCallRealMethod();
        GlobalFilter globalFilter = mock(GlobalFilter.class);
        when(globalFilter.getNominalV()).thenReturn(null);
        when(globalFilter.getCountryCode()).thenReturn(null);
        when(globalFilter.getSubstationProperty()).thenReturn(null);
        List<AbstractExpertRule> result = filterService.buildAllExpertRules(globalFilter, EquipmentType.GENERATOR);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnRulesWhenFilterExpertRulesProvided() {
        when(filterService.buildAllExpertRules(any(), any())).thenCallRealMethod();
        when(filterService.buildNominalVoltageRules(any(), any())).thenCallRealMethod();
        when(filterService.buildCountryCodeRules(any(), any())).thenCallRealMethod();
        when(filterService.buildSubstationPropertyRules(any(), any())).thenCallRealMethod();
        when(filterService.getNominalVoltageFieldType(any())).thenReturn(List.of(FieldType.NOMINAL_VOLTAGE));
        when(filterService.getCountryCodeFieldType(any())).thenReturn(List.of(FieldType.COUNTRY));
        when(filterService.getSubstationPropertiesFieldTypes(any())).thenReturn(List.of(FieldType.SUBSTATION_PROPERTIES));
        when(filterService.createNumberExpertRules(any(), any())).thenCallRealMethod();
        when(filterService.createEnumExpertRules(any(), any())).thenCallRealMethod();
        when(filterService.createPropertiesRule(any(), any(), any())).thenCallRealMethod();
        when(filterService.createOrCombination(any())).thenCallRealMethod();
        GlobalFilter globalFilter = mock(GlobalFilter.class);
        when(globalFilter.getNominalV()).thenReturn(List.of("400.0"));
        when(globalFilter.getCountryCode()).thenReturn(List.of(Country.FR));
        when(globalFilter.getSubstationProperty()).thenReturn(Map.of("prop1", List.of("value1")));
        List<AbstractExpertRule> result = filterService.buildAllExpertRules(globalFilter, EquipmentType.GENERATOR);
        assertNotNull(result);
        assertEquals(3, result.size());
    }

    @Test
    void shouldReturnNullWhenNoRules() {
        when(filterService.buildExpertFilter(any(), any())).thenCallRealMethod();
        when(filterService.buildAllExpertRules(any(), any())).thenCallRealMethod();
        when(filterService.buildNominalVoltageRules(any(), any())).thenCallRealMethod();
        GlobalFilter globalFilter = mock(GlobalFilter.class);
        when(globalFilter.getNominalV()).thenReturn(null);
        when(globalFilter.getCountryCode()).thenReturn(null);
        when(globalFilter.getSubstationProperty()).thenReturn(null);
        ExpertFilter result = filterService.buildExpertFilter(globalFilter, EquipmentType.GENERATOR);
        assertNull(result);
    }

    @Test
    void shouldCreateFilterWhenRulesExist() {
        when(filterService.buildExpertFilter(any(), any())).thenCallRealMethod();
        when(filterService.buildAllExpertRules(any(), any())).thenCallRealMethod();
        when(filterService.buildNominalVoltageRules(any(), any())).thenCallRealMethod();
        when(filterService.createCombination(any(), any())).thenCallRealMethod();
        when(filterService.getNominalVoltageFieldType(any())).thenReturn(List.of(FieldType.NOMINAL_VOLTAGE));
        when(filterService.createNumberExpertRules(any(), any())).thenCallRealMethod();
        when(filterService.createOrCombination(any())).thenCallRealMethod();
        GlobalFilter globalFilter = mock(GlobalFilter.class);
        when(globalFilter.getNominalV()).thenReturn(List.of("400.0"));
        when(globalFilter.getCountryCode()).thenReturn(null);
        when(globalFilter.getSubstationProperty()).thenReturn(null);
        ExpertFilter result = filterService.buildExpertFilter(globalFilter, EquipmentType.GENERATOR);
        assertNotNull(result);
        assertEquals(EquipmentType.GENERATOR, result.getEquipmentType());
        assertNotNull(result.getRules());
        assertInstanceOf(CombinatorExpertRule.class, result.getRules());
    }

    @Test
    void shouldReturnFilteredNetworkWhenSameEquipmentType() {
        when(filterService.extractEquipmentIdsFromGenericFilter(any(), any(), any())).thenCallRealMethod();
        when(filterService.filterNetwork(any(), any())).thenCallRealMethod();
        AbstractFilter filter = mock(AbstractFilter.class);
        when(filter.getEquipmentType()).thenReturn(EquipmentType.GENERATOR);
        try (MockedStatic<FilterServiceUtils> mockStatic = mockStatic(FilterServiceUtils.class)) {
            List<IdentifiableAttributes> attributes = Arrays.asList(
                    createIdentifiableAttributes("gen1"),
                    createIdentifiableAttributes("gen2")
            );
            mockStatic.when(() -> FilterServiceUtils.getIdentifiableAttributes(filter, network, filterService)).thenReturn(attributes);
            List<String> result = filterService.extractEquipmentIdsFromGenericFilter(filter, EquipmentType.GENERATOR, network);
            assertEquals(Arrays.asList("gen1", "gen2"), result);
        }
    }

    @Test
    void shouldBuildVoltageLevelFilterWhenVoltageLevelType() {
        when(filterService.extractEquipmentIdsFromGenericFilter(any(), any(), any())).thenCallRealMethod();
        when(filterService.buildExpertFilterWithVoltageLevelIdsCriteria(any(), any())).thenCallRealMethod();
        when(filterService.createVoltageLevelIdRule(any(), any())).thenCallRealMethod();
        when(filterService.createCombination(any(), any())).thenCallRealMethod();
        when(filterService.filterNetwork(any(), any())).thenCallRealMethod();
        AbstractFilter filter = mock(AbstractFilter.class);
        when(filter.getEquipmentType()).thenReturn(EquipmentType.VOLTAGE_LEVEL);
        when(filter.getId()).thenReturn(FILTER_UUID);
        try (MockedStatic<FilterServiceUtils> mockStatic = mockStatic(FilterServiceUtils.class)) {
            List<IdentifiableAttributes> attributes = Arrays.asList(
                    createIdentifiableAttributes("line1"),
                    createIdentifiableAttributes("line2")
            );
            mockStatic.when(() -> FilterServiceUtils.getIdentifiableAttributes(any(ExpertFilter.class), eq(network), eq(filterService))).thenReturn(attributes);
            List<String> result = filterService.extractEquipmentIdsFromGenericFilter(filter, EquipmentType.LINE, network);
            assertEquals(Arrays.asList("line1", "line2"), result);
        }
    }

    @Test
    void shouldReturnEmptyWhenDifferentEquipmentType() {
        when(filterService.extractEquipmentIdsFromGenericFilter(any(), any(), any())).thenCallRealMethod();
        AbstractFilter filter = mock(AbstractFilter.class);
        when(filter.getEquipmentType()).thenReturn(EquipmentType.LOAD);
        List<String> result = filterService.extractEquipmentIdsFromGenericFilter(filter, EquipmentType.GENERATOR, network);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldCreateExpertFilterWithVoltageLevelIdsCriteria() {
        when(filterService.buildExpertFilterWithVoltageLevelIdsCriteria(any(), any())).thenCallRealMethod();
        when(filterService.createVoltageLevelIdRule(any(), any())).thenCallRealMethod();
        when(filterService.createCombination(any(), any())).thenCallRealMethod();
        UUID filterUuid = UUID.randomUUID();
        EquipmentType equipmentType = EquipmentType.LINE;
        ExpertFilter result = filterService.buildExpertFilterWithVoltageLevelIdsCriteria(filterUuid, equipmentType);
        assertNotNull(result);
        assertEquals(equipmentType, result.getEquipmentType());
        assertNotNull(result.getRules());
        assertInstanceOf(CombinatorExpertRule.class, result.getRules());
        CombinatorExpertRule combinatorRule = (CombinatorExpertRule) result.getRules();
        assertEquals(CombinatorType.OR, combinatorRule.getCombinator());
        assertEquals(2, combinatorRule.getRules().size());
    }

    @Test
    void shouldReturnEmptyWhenNoFilterResults() {
        when(filterService.getResourceFilter(any(), any(), any(), any(), any())).thenCallRealMethod();
        when(filterService.getNetwork(any(), any())).thenReturn(network);
        when(filterService.getFilters(any())).thenReturn(Collections.emptyList());
        when(filterService.filterEquipmentsByType(any(), any(), any(), any())).thenReturn(Collections.emptyMap());
        GlobalFilter globalFilter = mock(GlobalFilter.class);
        when(globalFilter.getGenericFilter()).thenReturn(Collections.emptyList());
        List<EquipmentType> equipmentTypes = List.of(EquipmentType.LINE);
        String columnName = "functionId";
        Optional<ResourceFilterDTO> result = filterService.getResourceFilter(NETWORK_UUID, VARIANT_ID, globalFilter, equipmentTypes, columnName);
        assertNotNull(result);
        assertFalse(result.isPresent());
    }

    @Test
    void shouldReturnResourceFilterWithResults() {
        when(filterService.getResourceFilter(any(), any(), any(), any(), any())).thenCallRealMethod();
        when(filterService.getNetwork(any(), any())).thenReturn(network);
        when(filterService.getFilters(any())).thenReturn(Collections.emptyList());
        Map<EquipmentType, List<String>> equipmentResults = new EnumMap<>(EquipmentType.class);
        equipmentResults.put(EquipmentType.LINE, Arrays.asList("line1", "line2"));
        equipmentResults.put(EquipmentType.GENERATOR, List.of("gen1"));
        when(filterService.filterEquipmentsByType(any(), any(), any(), any())).thenReturn(equipmentResults);
        GlobalFilter globalFilter = mock(GlobalFilter.class);
        when(globalFilter.getGenericFilter()).thenReturn(Collections.emptyList());
        List<EquipmentType> equipmentTypes = Arrays.asList(EquipmentType.LINE, EquipmentType.GENERATOR);
        String columnName = "functionId";
        Optional<ResourceFilterDTO> result = filterService.getResourceFilter(NETWORK_UUID, VARIANT_ID, globalFilter, equipmentTypes, columnName);
        assertNotNull(result);
        assertTrue(result.isPresent());
        ResourceFilterDTO dto = result.get();
        assertEquals(ResourceFilterDTO.DataType.TEXT, dto.dataType());
        assertEquals(ResourceFilterDTO.Type.IN, dto.type());
        assertEquals(columnName, dto.column());
        assertEquals(3, ((List<?>) dto.value()).size());
    }

    private IdentifiableAttributes createIdentifiableAttributes(String id) {
        IdentifiableAttributes attributes = mock(IdentifiableAttributes.class);
        when(attributes.getId()).thenReturn(id);
        return attributes;
    }
}
