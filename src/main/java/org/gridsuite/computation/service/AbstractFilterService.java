/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation.service;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Network;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import lombok.NonNull;
import org.apache.commons.collections4.CollectionUtils;
import org.gridsuite.computation.dto.GlobalFilter;
import org.gridsuite.computation.dto.ResourceFilterDTO;
import org.gridsuite.filter.AbstractFilter;
import org.gridsuite.filter.globalfilter.AbstractGlobalFilterService;
import org.gridsuite.filter.utils.EquipmentType;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @author Rehili Ghazwa <ghazwa.rehili at rte-france.com>
 */
public abstract class AbstractFilterService extends AbstractGlobalFilterService {
    protected static final String FILTERS_NOT_FOUND = "Filters not found";
    protected static final String FILTER_API_VERSION = "v1";
    protected static final String DELIMITER = "/";

    protected final RestTemplate restTemplate = new RestTemplate();
    protected final NetworkStoreService networkStoreService;
    protected final String filterServerBaseUri;
    public static final String NETWORK_UUID = "networkUuid";

    public static final String IDS = "ids";

    protected AbstractFilterService(NetworkStoreService networkStoreService, String filterServerBaseUri) {
        this.networkStoreService = networkStoreService;
        this.filterServerBaseUri = filterServerBaseUri;
    }

    @Override
    public List<AbstractFilter> getFilters(List<UUID> filtersUuids) {
        if (CollectionUtils.isEmpty(filtersUuids)) {
            return List.of();
        }

        String ids = filtersUuids.stream()
                .map(UUID::toString)
                .collect(Collectors.joining(","));

        String path = UriComponentsBuilder
                .fromPath(DELIMITER + FILTER_API_VERSION + "/filters/metadata")
                .queryParam(IDS, ids)
                .buildAndExpand()
                .toUriString();

        try {
            return restTemplate.exchange(
                    filterServerBaseUri + path,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<AbstractFilter>>() { }
            ).getBody();
        } catch (HttpStatusCodeException e) {
            throw new PowsyblException(FILTERS_NOT_FOUND + " [" + filtersUuids + "]");
        }
    }

    @Override
    protected Network getNetwork(@NotNull final UUID networkUuid, @NotNull final String variantId) {
        try {
            Network network = networkStoreService.getNetwork(networkUuid, PreloadingStrategy.COLLECTION);
            network.getVariantManager().setWorkingVariant(variantId);
            return network;
        } catch (final PowsyblException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    public Optional<ResourceFilterDTO> getResourceFilter(@NonNull final UUID networkUuid, @NonNull final String variantId,
                                                         @NonNull final GlobalFilter globalFilter,
                                                         @NonNull final List<EquipmentType> equipmentTypes,
                                                         final String columnName) {
        final List<String> subjectIds = this.getIdsFilter(networkUuid, variantId, globalFilter, equipmentTypes);
        return subjectIds.isEmpty()
                ? Optional.empty()
                : Optional.of(new ResourceFilterDTO(ResourceFilterDTO.DataType.TEXT, ResourceFilterDTO.Type.IN, subjectIds, columnName));
    }
}



