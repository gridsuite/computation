/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.computation.service;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManager;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import org.gridsuite.computation.dto.GlobalFilter;
import org.gridsuite.computation.dto.ResourceFilterDTO;
import org.gridsuite.filter.AbstractFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    void shouldReturnResourceFilterWhenSuccessful() {
        when(filterService.getResourceFilter(any(), any(), any(), any(), any())).thenCallRealMethod();
        when(networkStoreService.getNetwork(any(), any())).thenReturn(network);
        Optional<ResourceFilterDTO> resourceFilter = filterService.getResourceFilter(NETWORK_UUID, VARIANT_ID, new GlobalFilter(), List.of(), "testColumn");
        assertFalse(resourceFilter.isPresent());
    }
}
