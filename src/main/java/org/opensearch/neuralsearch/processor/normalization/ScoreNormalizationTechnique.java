/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.neuralsearch.processor.normalization;

import java.util.List;

import org.opensearch.neuralsearch.search.CompoundTopDocs;

/**
 * Abstracts normalization of scores in query search results.
 */
public interface ScoreNormalizationTechnique {

    /**
     * Performs score normalization based on input normalization technique. Mutates input object by updating normalized scores.
     * @param queryTopDocs original query results from multiple shards and multiple sub-queries
     */
    void normalize(final List<CompoundTopDocs> queryTopDocs);
}
