/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation.specification;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import lombok.NoArgsConstructor;
import org.gridsuite.computation.dto.ResourceFilterDTO;
import org.gridsuite.computation.utils.SpecificationUtils;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.UUID;

/**
 * @author Kevin LE SAULNIER <kevin.lesaulnier@rte-france.com>
 */
@NoArgsConstructor
public abstract class AbstractCommonSpecificationBuilder<T> {

    public Specification<T> resultUuidEquals(UUID value) {
        return (root, cq, cb) -> cb.equal(getResultIdPath(root), value);
    }

    public Specification<T> uuidIn(List<UUID> uuids) {
        return (root, cq, cb) -> root.get(getIdFieldName()).in(uuids);
    }

    /**
     * @param distinct : true if you want to force the results to be distinct.
     *                 Since sql joins generates duplicate results, we may need to use distinct here
     *                 But can't use both distinct and sort on nested field (sql limitation)
     */
    public Specification<T> buildSpecification(UUID resultUuid, List<ResourceFilterDTO> resourceFilters, boolean distinct) {
        List<ResourceFilterDTO> childrenFilters = resourceFilters != null ? resourceFilters.stream().filter(this::isNotParentFilter).toList() : List.of();
        // filter by resultUuid
        Specification<T> specification = Specification.where(resultUuidEquals(resultUuid));
        if (distinct) {
            specification = specification.and(SpecificationUtils.distinct());
        }
        if (childrenFilters.isEmpty()) {
            Specification<T> spec = addSpecificFilterWhenNoChildrenFilter();
            if (spec != null) {
                specification = specification.and(spec);
            }
        } else {
            // needed here to filter main entities that would have empty collection when filters are applied
            Specification<T> spec = addSpecificFilterWhenChildrenFilters();
            if (spec != null) {
                specification = specification.and(spec);
            }
        }

        return SpecificationUtils.appendFiltersToSpecification(specification, resourceFilters);
    }

    public Specification<T> buildSpecification(UUID resultUuid, List<ResourceFilterDTO> resourceFilters) {
        return buildSpecification(resultUuid, resourceFilters, true);
    }

    public Specification<T> buildLimitViolationsSpecification(List<UUID> uuids, List<ResourceFilterDTO> resourceFilters) {
        List<ResourceFilterDTO> childrenFilters = resourceFilters.stream().filter(this::isNotParentFilter).toList();
        Specification<T> specification = Specification.where(uuidIn(uuids));

        return SpecificationUtils.appendFiltersToSpecification(specification, childrenFilters);
    }

    public Specification<T> addSpecificFilterWhenChildrenFilters() {
        return null;
    }

    public Specification<T> addSpecificFilterWhenNoChildrenFilter() {
        return null;
    }

    public abstract boolean isNotParentFilter(ResourceFilterDTO filter);

    public abstract String getIdFieldName();

    public abstract Path<UUID> getResultIdPath(Root<T> root);
}
