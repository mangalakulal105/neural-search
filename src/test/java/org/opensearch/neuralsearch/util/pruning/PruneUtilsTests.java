/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.neuralsearch.util.pruning;

import org.opensearch.test.OpenSearchTestCase;

import java.util.HashMap;
import java.util.Map;

public class PruneUtilsTests extends OpenSearchTestCase {

    public void testPruningByTopK() {
        Map<String, Float> input = new HashMap<>();
        input.put("a", 5.0f);
        input.put("b", 3.0f);
        input.put("c", 4.0f);
        input.put("d", 1.0f);

        Map<String, Float> result = PruneUtils.pruningSparseVector(PruneType.TOP_K, 2, input);

        assertEquals(2, result.size());
        assertTrue(result.containsKey("a"));
        assertTrue(result.containsKey("c"));
        assertEquals(5.0f, result.get("a"), 0.001);
        assertEquals(4.0f, result.get("c"), 0.001);
    }

    public void testPruningByMaxRatio() {
        Map<String, Float> input = new HashMap<>();
        input.put("a", 10.0f);
        input.put("b", 8.0f);
        input.put("c", 5.0f);
        input.put("d", 2.0f);

        Map<String, Float> result = PruneUtils.pruningSparseVector(PruneType.MAX_RATIO, 0.7f, input);

        assertEquals(2, result.size());
        assertTrue(result.containsKey("a")); // 10.0/10.0 = 1.0 >= 0.7
        assertTrue(result.containsKey("b")); // 8.0/10.0 = 0.8 >= 0.7
    }

    public void testPruningByValue() {
        Map<String, Float> input = new HashMap<>();
        input.put("a", 5.0f);
        input.put("b", 3.0f);
        input.put("c", 2.0f);
        input.put("d", 1.0f);

        Map<String, Float> result = PruneUtils.pruningSparseVector(PruneType.ABS_VALUE, 3.0f, input);

        assertEquals(2, result.size());
        assertTrue(result.containsKey("a"));
        assertTrue(result.containsKey("b"));
    }

    public void testPruningByAlphaMass() {
        Map<String, Float> input = new HashMap<>();
        input.put("a", 10.0f);
        input.put("b", 6.0f);
        input.put("c", 3.0f);
        input.put("d", 1.0f);
        // Total sum = 20.0

        Map<String, Float> result = PruneUtils.pruningSparseVector(PruneType.ALPHA_MASS, 0.8f, input);

        assertEquals(2, result.size());
        assertTrue(result.containsKey("a"));
        assertTrue(result.containsKey("b"));
    }

    public void testEmptyInput() {
        Map<String, Float> input = new HashMap<>();

        Map<String, Float> result = PruneUtils.pruningSparseVector(PruneType.TOP_K, 5, input);
        assertTrue(result.isEmpty());
    }

    public void testNegativeValues() {
        Map<String, Float> input = new HashMap<>();
        input.put("a", -5.0f);
        input.put("b", 3.0f);
        input.put("c", 4.0f);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> PruneUtils.pruningSparseVector(PruneType.TOP_K, 2, input)
        );
        assertEquals("Pruned values must be positive", exception.getMessage());
    }

    public void testInvalidPruningType() {
        Map<String, Float> input = new HashMap<>();
        input.put("a", 1.0f);
        input.put("b", 2.0f);

        IllegalArgumentException exception1 = assertThrows(
            IllegalArgumentException.class,
            () -> PruneUtils.pruningSparseVector(null, 2, input)
        );
        assertEquals(exception1.getMessage(), "Prune type and prune ratio must be provided");

        IllegalArgumentException exception2 = assertThrows(
            IllegalArgumentException.class,
            () -> PruneUtils.pruningSparseVector(null, 2, input)
        );
        assertEquals(exception2.getMessage(), "Prune type and prune ratio must be provided");
    }

    public void testIsValidPruneRatio() {
        // Test TOP_K validation
        assertTrue(PruneUtils.isValidPruneRatio(PruneType.TOP_K, 1));
        assertTrue(PruneUtils.isValidPruneRatio(PruneType.TOP_K, 100));
        assertFalse(PruneUtils.isValidPruneRatio(PruneType.TOP_K, 0));
        assertFalse(PruneUtils.isValidPruneRatio(PruneType.TOP_K, -1));
        assertFalse(PruneUtils.isValidPruneRatio(PruneType.TOP_K, 1.5f));

        // Test ALPHA_MASS validation
        assertTrue(PruneUtils.isValidPruneRatio(PruneType.ALPHA_MASS, 0.5f));
        assertFalse(PruneUtils.isValidPruneRatio(PruneType.ALPHA_MASS, 1.0f));
        assertFalse(PruneUtils.isValidPruneRatio(PruneType.ALPHA_MASS, 0));
        assertFalse(PruneUtils.isValidPruneRatio(PruneType.ALPHA_MASS, -0.1f));
        assertFalse(PruneUtils.isValidPruneRatio(PruneType.ALPHA_MASS, 1.1f));

        // Test MAX_RATIO validation
        assertFalse(PruneUtils.isValidPruneRatio(PruneType.MAX_RATIO, 0.0f));
        assertTrue(PruneUtils.isValidPruneRatio(PruneType.MAX_RATIO, 0.5f));
        assertFalse(PruneUtils.isValidPruneRatio(PruneType.MAX_RATIO, 1.0f));
        assertFalse(PruneUtils.isValidPruneRatio(PruneType.MAX_RATIO, -0.1f));
        assertFalse(PruneUtils.isValidPruneRatio(PruneType.MAX_RATIO, 1.1f));

        // Test ABS_VALUE validation
        assertFalse(PruneUtils.isValidPruneRatio(PruneType.ABS_VALUE, 0.0f));
        assertTrue(PruneUtils.isValidPruneRatio(PruneType.ABS_VALUE, 1.0f));
        assertTrue(PruneUtils.isValidPruneRatio(PruneType.ABS_VALUE, 100.0f));
        assertFalse(PruneUtils.isValidPruneRatio(PruneType.ABS_VALUE, -0.1f));

        // Test with extreme cases
        assertTrue(PruneUtils.isValidPruneRatio(PruneType.TOP_K, Float.MAX_VALUE));
        assertTrue(PruneUtils.isValidPruneRatio(PruneType.ABS_VALUE, Float.MAX_VALUE));
        assertTrue(PruneUtils.isValidPruneRatio(PruneType.ALPHA_MASS, Float.MIN_VALUE));
        assertTrue(PruneUtils.isValidPruneRatio(PruneType.MAX_RATIO, Float.MIN_VALUE));
    }

    public void testIsValidPruneRatioWithNullType() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> PruneUtils.isValidPruneRatio(null, 1.0f));
        assertEquals("Pruning type cannot be null", exception.getMessage());
    }
}
