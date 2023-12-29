/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.neuralsearch.search;

import java.util.stream.IntStream;

import org.apache.lucene.search.ScoreMode;
import org.opensearch.neuralsearch.query.OpenSearchQueryTestCase;

public class HitsThresholdCheckerTests extends OpenSearchQueryTestCase {

    public void testThresholdReached_whenIncrementCount_thenThresholdReached() {
        HitsThresholdChecker hitsThresholdChecker = new HitsThresholdChecker(5);
        assertEquals(5, hitsThresholdChecker.getTotalHitsThreshold());
        assertEquals(ScoreMode.TOP_SCORES, hitsThresholdChecker.scoreMode());
        assertFalse(hitsThresholdChecker.isThresholdReached());
        hitsThresholdChecker.incrementHitCount();
        assertFalse(hitsThresholdChecker.isThresholdReached());
        IntStream.rangeClosed(1, 5).forEach((checker) -> hitsThresholdChecker.incrementHitCount());
        assertTrue(hitsThresholdChecker.isThresholdReached());
    }

    public void testThresholdLimit_whenThresholdNegative_thenFail() {
        expectThrows(IllegalArgumentException.class, () -> new HitsThresholdChecker(-1));
    }

    public void testTrackThreshold_whenTrackThresholdSet_thenSuccessful() {
        HitsThresholdChecker hitsThresholdChecker = new HitsThresholdChecker(Integer.MAX_VALUE);
        assertEquals(ScoreMode.TOP_SCORES, hitsThresholdChecker.scoreMode());
        assertFalse(hitsThresholdChecker.isThresholdReached());
        hitsThresholdChecker.incrementHitCount();
        assertFalse(hitsThresholdChecker.isThresholdReached());
        IntStream.rangeClosed(1, 5).forEach((checker) -> hitsThresholdChecker.incrementHitCount());
        assertFalse(hitsThresholdChecker.isThresholdReached());
    }
}
