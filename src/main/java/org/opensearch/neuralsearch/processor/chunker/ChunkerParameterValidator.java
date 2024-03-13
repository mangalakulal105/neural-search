/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.neuralsearch.processor.chunker;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.Map;
import java.util.Locale;

/**
 * Validate and parse the parameter for text chunking processor and algorithms
 */
public class ChunkerParameterValidator {

    /**
     * Validate and parse the parameter for string parameters
     */
    public static String validateStringParameters(
        Map<String, Object> parameters,
        String fieldName,
        String defaultValue,
        boolean allowEmpty
    ) {
        if (!parameters.containsKey(fieldName)) {
            // all chunking algorithm parameters are optional
            return defaultValue;
        }
        Object fieldValue = parameters.get(fieldName);
        if (!(fieldValue instanceof String)) {
            throw new IllegalArgumentException(
                String.format(Locale.ROOT, "Parameter [%s] cannot be cast to [%s]", fieldName, String.class.getName())
            );
        } else if (!allowEmpty && StringUtils.isEmpty(fieldValue.toString())) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "Parameter [%s] should not be empty.", fieldName));
        }
        return (String) fieldValue;
    }

    /**
     * Validate and parse the parameter for numeric parameters
     */
    public static Number validateNumberParameter(Map<String, Object> parameters, String fieldName, Number defaultValue) {
        if (!parameters.containsKey(fieldName)) {
            // all chunking algorithm parameters are optional
            return defaultValue;
        }
        String fieldValue = parameters.get(fieldName).toString();
        if (!(NumberUtils.isParsable(fieldValue))) {
            throw new IllegalArgumentException(
                String.format(Locale.ROOT, "Parameter [%s] cannot be cast to [%s]", fieldName, Number.class.getName())
            );
        }
        return NumberUtils.createNumber(fieldValue);
    }

    /**
     * Validate and parse the parameter for positive integer parameters
     */
    public static int validatePositiveIntegerParameter(Map<String, Object> parameters, String fieldName, int defaultValue) {
        Number fieldValueNumber = validateNumberParameter(parameters, fieldName, defaultValue);
        int fieldValueInt = fieldValueNumber.intValue();
        // sometimes parameter has negative default value, indicating that this parameter is not effective
        if (fieldValueInt != defaultValue && fieldValueInt <= 0) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "Parameter [%s] must be positive.", fieldName));
        }
        return fieldValueInt;
    }

    /**
     * Validate and parse the parameter for double parameters within [lowerBound, upperBound]
     */
    public static double validateRangeDoubleParameter(
        Map<String, Object> parameters,
        String fieldName,
        double lowerBound,
        double upperBound,
        double defaultValue
    ) {
        Number fieldValueNumber = validateNumberParameter(parameters, fieldName, defaultValue);
        double fieldValueDouble = fieldValueNumber.doubleValue();
        if (fieldValueDouble < lowerBound || fieldValueDouble > upperBound) {
            throw new IllegalArgumentException(
                String.format(Locale.ROOT, "Parameter [%s] must be between %s and %s", fieldName, lowerBound, upperBound)
            );
        }
        return fieldValueDouble;
    }
}
