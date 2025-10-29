/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.computation.utils;

import com.google.common.collect.Lists;
import jakarta.persistence.criteria.*;
import lombok.NonNull;
import org.gridsuite.computation.dto.ResourceFilterDTO;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.EscapeCharacter;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static org.springframework.data.jpa.domain.Specification.anyOf;
import static org.springframework.data.jpa.domain.Specification.not;

/**
 * Utility class to create Spring Data JPA Specification (Spring interface for JPA Criteria API).
 *
 * @author Kevin Le Saulnier <kevin.lesaulnier@rte-france.com>
 */
public final class SpecificationUtils {

    public static final String FIELD_SEPARATOR = ".";

    /**
    Prefer less than 1000 for Oracle DB / good compromise for Postgres
     */
    public static final int MAX_IN_CLAUSE_SIZE = 10000;

    // Utility class, so no constructor
    private SpecificationUtils() { }

    // we use .as(String.class) to be able to works on enum fields
    public static <X> Specification<X> equals(String field, String value) {
        return (root, cq, cb) -> cb.equal(
                cb
                    .upper(getColumnPath(root, field).as(String.class))
                    .as(String.class),
                value.toUpperCase()
        );
    }

    public static <X> Specification<X> notEqual(String field, String value) {
        return (root, cq, cb) -> cb.notEqual(getColumnPath(root, field), value);
    }

    public static <X> Specification<X> in(String field, List<String> values) {
        return (root, cq, cb) ->
                cb.upper(getColumnPath(root, field).as(String.class)).in(values);
    }

    public static <X> Specification<X> contains(String field, String value) {
        return (root, cq, cb) -> cb.like(cb.upper(getColumnPath(root, field).as(String.class)), "%" + EscapeCharacter.DEFAULT.escape(value).toUpperCase() + "%", EscapeCharacter.DEFAULT.getEscapeCharacter());
    }

    public static <X> Specification<X> startsWith(String field, String value) {
        return (root, cq, cb) -> cb.like(cb.upper(getColumnPath(root, field).as(String.class)), EscapeCharacter.DEFAULT.escape(value).toUpperCase() + "%", EscapeCharacter.DEFAULT.getEscapeCharacter());
    }

    /**
     * Returns a specification where the field value is not equal within the given tolerance.
     */
    public static <X> Specification<X> notEqual(String field, Double value, Double tolerance) {
        return (root, cq, cb) -> {
            Expression<Double> doubleExpression = getColumnPath(root, field).as(Double.class);
            /*
             * in order to be equal to doubleExpression, value has to fit :
             * value - tolerance <= doubleExpression <= value + tolerance
             * therefore in order to be different at least one of the opposite comparison needs to be true :
             */
            return cb.or(
                    cb.greaterThan(doubleExpression, value + tolerance),
                    cb.lessThan(doubleExpression, value - tolerance)
            );
        };
    }

    public static <X> Specification<X> lessThanOrEqual(String field, Double value, Double tolerance) {
        return (root, cq, cb) -> {
            Expression<Double> doubleExpression = getColumnPath(root, field).as(Double.class);
            return cb.lessThanOrEqualTo(doubleExpression, value + tolerance);
        };
    }

    public static <X> Specification<X> greaterThanOrEqual(String field, Double value, Double tolerance) {
        return (root, cq, cb) -> {
            Expression<Double> doubleExpression = getColumnPath(root, field).as(Double.class);
            return cb.greaterThanOrEqualTo(doubleExpression, value - tolerance);
        };
    }

    public static <X> Specification<X> isNotEmpty(String field) {
        return (root, cq, cb) -> cb.isNotEmpty(getColumnPath(root, field));
    }

    public static <X> Specification<X> distinct() {
        return (root, cq, cb) -> {
            // to select distinct result, we need to set a "criteria query" param
            // we don't need to return any predicate here
            cq.distinct(true);
            return null;
        };
    }

    public static <X> Specification<X> appendFiltersToSpecification(Specification<X> specification, List<ResourceFilterDTO> resourceFilters) {
        Objects.requireNonNull(specification);

        if (resourceFilters == null || resourceFilters.isEmpty()) {
            return specification;
        }

        Specification<X> completedSpecification = specification;

        for (ResourceFilterDTO resourceFilter : resourceFilters) {
            if (resourceFilter.dataType() == ResourceFilterDTO.DataType.TEXT) {
                completedSpecification = appendTextFilterToSpecification(completedSpecification, resourceFilter);
            } else if (resourceFilter.dataType() == ResourceFilterDTO.DataType.NUMBER) {
                completedSpecification = appendNumberFilterToSpecification(completedSpecification, resourceFilter);
            }
        }

        return completedSpecification;
    }

    @NonNull
    private static <X> Specification<X> appendTextFilterToSpecification(Specification<X> specification, ResourceFilterDTO resourceFilter) {
        Specification<X> completedSpecification = specification;

        switch (resourceFilter.type()) {
            case NOT_EQUAL, EQUALS, IN -> {
                // this type can manage one value or a list of values (with OR)
                if (resourceFilter.value() instanceof Collection<?> valueList) {
                    // implicitly an IN resourceFilter type because only IN may have value lists as filter value
                    List<String> inValues = valueList.stream()
                            .map(Object::toString)
                            .map(String::toUpperCase)
                            .toList();
                    completedSpecification = completedSpecification.and(
                            resourceFilter.type() == ResourceFilterDTO.Type.NOT_EQUAL ?
                            not(generateInSpecification(resourceFilter.column(), inValues)) :
                            generateInSpecification(resourceFilter.column(), inValues)
                    );
                } else if (resourceFilter.value() == null) {
                    // if the value is null, we build an impossible specification (trick to remove later on ?)
                    completedSpecification = completedSpecification.and(not(completedSpecification));
                } else {
                    completedSpecification = completedSpecification.and(
                            resourceFilter.type() == ResourceFilterDTO.Type.NOT_EQUAL ?
                            notEqual(resourceFilter.column(), resourceFilter.value().toString()) :
                            equals(resourceFilter.column(), resourceFilter.value().toString())
                    );
                }
            }
            case CONTAINS -> {
                if (resourceFilter.value() instanceof Collection<?> valueList) {
                    completedSpecification = completedSpecification.and(
                            anyOf(
                                    valueList
                                            .stream()
                                            .map(value -> SpecificationUtils.<X>contains(resourceFilter.column(), value.toString()))
                                            .toList()
                            ));
                } else {
                    completedSpecification = completedSpecification.and(contains(resourceFilter.column(), resourceFilter.value().toString()));
                }
            }
            case STARTS_WITH ->
                    completedSpecification = completedSpecification.and(startsWith(resourceFilter.column(), resourceFilter.value().toString()));
            default -> throw new IllegalArgumentException("The filter type " + resourceFilter.type() + " is not supported with the data type " + resourceFilter.dataType());
        }

        return completedSpecification;
    }

    /**
    * Generates a specification for IN clause with the given column and values.
    * Handles large value lists by chunking them to avoid StackOverflow.
    *
    * @param column the column name to filter on
    * @param inPossibleValues the list of values for the IN clause
    * @return a specification for the IN clause
    */
    private static <X> Specification<X> generateInSpecification(String column, List<String> inPossibleValues) {
        List<List<String>> chunksOfInValues = Lists.partition(inPossibleValues, MAX_IN_CLAUSE_SIZE);
        Specification<X> containerSpec = null;
        for (List<String> chunk : chunksOfInValues) {
            Specification<X> multiOrEqualSpec = Specification.anyOf(in(column, chunk));
            if (containerSpec == null) {
                containerSpec = multiOrEqualSpec;
            } else {
                containerSpec = containerSpec.or(multiOrEqualSpec);
            }
        }
        return containerSpec;
    }

    @NonNull
    private static <X> Specification<X> appendNumberFilterToSpecification(Specification<X> specification, ResourceFilterDTO resourceFilter) {
        String filterValue = resourceFilter.value().toString();
        double tolerance;
        if (resourceFilter.tolerance() != null) {
            tolerance = resourceFilter.tolerance();
        } else {
            // the reference for the comparison is the number of digits after the decimal point in filterValue
            // extra digits are ignored, but the user may add '0's after the decimal point in order to get a better precision
            String[] splitValue = filterValue.split("\\.");
            int numberOfDecimalAfterDot = 0;
            if (splitValue.length > 1) {
                numberOfDecimalAfterDot = splitValue[1].length();
            }
            // tolerance is multiplied by 0.5 to simulate the fact that the database value is rounded (in the front, from the user viewpoint)
            // more than 13 decimal after dot will likely cause rounding errors due to double precision
            tolerance = Math.pow(10, -numberOfDecimalAfterDot) * 0.5;
        }
        Double valueDouble = Double.valueOf(filterValue);
        return switch (resourceFilter.type()) {
            case NOT_EQUAL -> specification.and(notEqual(resourceFilter.column(), valueDouble, tolerance));
            case LESS_THAN_OR_EQUAL ->
                    specification.and(lessThanOrEqual(resourceFilter.column(), valueDouble, tolerance));
            case GREATER_THAN_OR_EQUAL ->
                    specification.and(greaterThanOrEqual(resourceFilter.column(), valueDouble, tolerance));
            case EQUALS -> specification.and(greaterThanOrEqual(resourceFilter.column(), valueDouble, tolerance))
                    .and(lessThanOrEqual(resourceFilter.column(), valueDouble, tolerance));
            default ->
                    throw new IllegalArgumentException("The filter type " + resourceFilter.type() + " is not supported with the data type " + resourceFilter.dataType());
        };
    }

    /**
     * This method allow to query eventually dot separated fields with the Criteria API
     * Ex : from 'fortescueCurrent.positiveMagnitude' we create the query path
     * root.get("fortescueCurrent").get("positiveMagnitude") to access to the correct nested field
     *
     * @param root               the root entity
     * @param dotSeparatedFields dot separated fields (can be only one field without any dot)
     * @param <X>                the entity type referenced by the root
     * @param <Y>                the type referenced by the path
     * @return path for the query
     */
    private static <X, Y> Path<Y> getColumnPath(Root<X> root, String dotSeparatedFields) {
        if (dotSeparatedFields.contains(SpecificationUtils.FIELD_SEPARATOR)) {
            String[] fields = dotSeparatedFields.split("\\.");
            Path<Y> path = root.get(fields[0]);
            for (int i = 1; i < fields.length; i++) {
                path = path.get(fields[i]);
            }
            return path;
        } else {
            return root.get(dotSeparatedFields);
        }
    }
}
