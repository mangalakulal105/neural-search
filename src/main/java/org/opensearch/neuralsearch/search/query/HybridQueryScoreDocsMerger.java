/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.neuralsearch.search.query;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.lucene.search.ScoreDoc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static org.opensearch.neuralsearch.search.util.HybridSearchResultFormatUtil.isHybridQueryScoreDocElement;

/**
 * Merges two ScoreDoc arrays into one
 */
@NoArgsConstructor(access = AccessLevel.PACKAGE)
class HybridQueryScoreDocsMerger<T extends ScoreDoc> {

    private static final int MIN_NUMBER_OF_ELEMENTS_IN_SCORE_DOC = 3;

    /**
     * Merge two score docs objects, result ScoreDocs[] object will have all hits per sub-query from both original objects.
     * Input and output ScoreDocs are in format that is specific to Hybrid Query. This method should not be used for ScoreDocs from
     * other query types.
     * Logic is based on assumption that hits of every sub-query are sorted by score.
     * Method returns new object and doesn't mutate original ScoreDocs arrays.
     * @param sourceScoreDocs original score docs from query result
     * @param newScoreDocs new score docs that we need to merge into existing scores
     * @return merged array of ScoreDocs objects
     */
    public T[] merge(final T[] sourceScoreDocs, final T[] newScoreDocs, final Comparator<T> comparator) {
        if (Objects.requireNonNull(sourceScoreDocs, "score docs cannot be null").length < MIN_NUMBER_OF_ELEMENTS_IN_SCORE_DOC
            || Objects.requireNonNull(newScoreDocs, "score docs cannot be null").length < MIN_NUMBER_OF_ELEMENTS_IN_SCORE_DOC) {
            throw new IllegalArgumentException("cannot merge top docs because it does not have enough elements");
        }
        // we overshoot and preallocate more than we need - length of both top docs combined.
        // we will take only portion of the array at the end
        List<T> mergedScoreDocs = new ArrayList<>(sourceScoreDocs.length + newScoreDocs.length);
        int sourcePointer = 0;
        // mark beginning of hybrid query results by start element
        mergedScoreDocs.add(sourceScoreDocs[sourcePointer]);
        sourcePointer++;
        // new pointer is set to 1 as we don't care about it start-stop element
        int newPointer = 1;

        while (sourcePointer < sourceScoreDocs.length - 1 && newPointer < newScoreDocs.length - 1) {
            // every iteration is for results of one sub-query
            mergedScoreDocs.add(sourceScoreDocs[sourcePointer]);
            sourcePointer++;
            newPointer++;
            // simplest case when both arrays have results for sub-query
            while (sourcePointer < sourceScoreDocs.length
                && isHybridQueryScoreDocElement(sourceScoreDocs[sourcePointer])
                && newPointer < newScoreDocs.length
                && isHybridQueryScoreDocElement(newScoreDocs[newPointer])) {
                if (comparator.compare(sourceScoreDocs[sourcePointer], newScoreDocs[newPointer]) >= 0) {
                    mergedScoreDocs.add(sourceScoreDocs[sourcePointer]);
                    sourcePointer++;
                } else {
                    mergedScoreDocs.add(newScoreDocs[newPointer]);
                    newPointer++;
                }
            }
            // at least one object got exhausted at this point, now merge all elements from object that's left
            while (sourcePointer < sourceScoreDocs.length && isHybridQueryScoreDocElement(sourceScoreDocs[sourcePointer])) {
                mergedScoreDocs.add(sourceScoreDocs[sourcePointer]);
                sourcePointer++;
            }
            while (newPointer < newScoreDocs.length && isHybridQueryScoreDocElement(newScoreDocs[newPointer])) {
                mergedScoreDocs.add(newScoreDocs[newPointer]);
                newPointer++;
            }
        }
        // mark end of hybrid query results by end element
        mergedScoreDocs.add(sourceScoreDocs[sourceScoreDocs.length - 1]);
        return mergedScoreDocs.toArray((T[]) new ScoreDoc[0]);
    }
}
