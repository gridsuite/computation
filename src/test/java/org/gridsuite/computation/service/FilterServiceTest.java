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
import org.gridsuite.filter.identifierlistfilter.IdentifierListFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author Rehili Ghazwa <ghazwa.rehili at rte-france.com>
 */
@ExtendWith(MockitoExtension.class)
class FilterServiceTest {

    @Mock
    private NetworkStoreService networkStoreService;

    @Mock
    private Network network;

    @Mock
    private VariantManager variantManager;

    private MockRestServiceServer server;
    private TestFilterService filterService;

    private static final String FILTER_SERVER_BASE_URI = "http://localhost:8080";
    private static final String VARIANT_ID = "testVariant";
    private static final UUID NETWORK_UUID = UUID.randomUUID();
    private static final UUID FILTER_UUID = UUID.randomUUID();
    private static final List<String> FILTERED_SUBJECT_ID = List.of("FILTERED_ID_1", "FILTERED_ID_2", "FILTERED_ID_3");

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        filterService = new TestFilterService(FILTERED_SUBJECT_ID, restClientBuilder, networkStoreService, FILTER_SERVER_BASE_URI);
    }

    @AfterEach
    void tearDown() {
        server.verify();
    }

    @Test
    void shouldReturnEmptyListWhenFiltersUuidsIsEmpty() {
        List<AbstractFilter> result = filterService.getFilters(List.of());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldCallRestClientAndReturnFilters() {
        List<UUID> filterUuids = List.of(FILTER_UUID);

        server.expect(MockRestRequestMatchers.requestTo(FILTER_SERVER_BASE_URI + "/v1/filters/metadata?ids=" + FILTER_UUID))
                .andRespond(MockRestResponseCreators.withSuccess("""
                    [{
                        "type": "IDENTIFIER_LIST",
                        "id": "%s",
                        "equipmentType": "LINE",
                        "filterEquipmentsAttributes": []
                    }]
                    """.formatted(FILTER_UUID), MediaType.APPLICATION_JSON));

        List<AbstractFilter> result = filterService.getFilters(filterUuids);

        assertEquals(1, result.size());
        assertInstanceOf(IdentifierListFilter.class, result.getFirst());
        assertEquals(FILTER_UUID, result.getFirst().getId());
    }

    @Test
    void shouldThrowPowsyblExceptionWhenHttpError() {
        List<UUID> filterUuids = List.of(FILTER_UUID);

        server.expect(MockRestRequestMatchers.requestTo(FILTER_SERVER_BASE_URI + "/v1/filters/metadata?ids=" + FILTER_UUID))
                .andRespond(MockRestResponseCreators.withServerError());

        PowsyblException exception = assertThrows(PowsyblException.class, () -> filterService.getFilters(filterUuids));
        assertTrue(exception.getMessage().contains("Filters not found"));
        assertTrue(exception.getMessage().contains(FILTER_UUID.toString()));
    }

    @Test
    void shouldReturnNetworkWhenSuccessful() {
        when(networkStoreService.getNetwork(NETWORK_UUID, PreloadingStrategy.COLLECTION)).thenReturn(network);
        when(network.getVariantManager()).thenReturn(variantManager);
        Network result = filterService.getNetwork(NETWORK_UUID, VARIANT_ID);
        assertEquals(network, result);
        verify(variantManager).setWorkingVariant(VARIANT_ID);
    }

    @Test
    void shouldThrowResponseStatusExceptionWhenPowsyblException() {
        PowsyblException powsyblException = new PowsyblException("Network not found");
        when(networkStoreService.getNetwork(NETWORK_UUID, PreloadingStrategy.COLLECTION)).thenThrow(powsyblException);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> filterService.getNetwork(NETWORK_UUID, VARIANT_ID));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Network not found", exception.getReason());
    }

    @Test
    void shouldReturnResourceFilterFromFiltersIds() {
        Optional<ResourceFilterDTO> resourceFilter = filterService.getResourceFilter(NETWORK_UUID, VARIANT_ID, new GlobalFilter(), List.of(), "testColumn");
        assertTrue(resourceFilter.isPresent());
        assertEquals(FILTERED_SUBJECT_ID, resourceFilter.get().value());
    }

    @Test
    void shouldReturnEmptyResourceFilterWhenNoFilteredIds() {
        AbstractFilterService returningEmptyFilterService = new TestFilterService(RestClient.builder(), networkStoreService, FILTER_SERVER_BASE_URI);
        Optional<ResourceFilterDTO> resourceFilter = returningEmptyFilterService.getResourceFilter(NETWORK_UUID, VARIANT_ID, new GlobalFilter(), List.of(), "testColumn");
        assertFalse(resourceFilter.isPresent());
    }

    private static final class TestFilterService extends AbstractFilterService {

        private final List<String> filteredSubjectIds;

        private TestFilterService(List<String> filteredSubjectIds, RestClient.Builder restClientBuilder, NetworkStoreService networkStoreService, String filterServerBaseUri) {
            this.filteredSubjectIds = filteredSubjectIds;
            super(restClientBuilder, networkStoreService, filterServerBaseUri);
        }

        private TestFilterService(RestClient.Builder restClientBuilder, NetworkStoreService networkStoreService, String filterServerBaseUri) {
            this.filteredSubjectIds = List.of();
            super(restClientBuilder, networkStoreService, filterServerBaseUri);
        }

        @Override
        protected List<String> getFilteredIds(UUID networkUuid, String variantId, org.gridsuite.filter.globalfilter.GlobalFilter globalFilter,
                                              List<org.gridsuite.filter.utils.EquipmentType> equipmentTypes) {
            return filteredSubjectIds;
        }
    }
}
