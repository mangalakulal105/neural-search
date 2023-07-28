/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.neuralsearch.processor.normalization;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.opensearch.neuralsearch.search.CompoundTopDocs;

/**
 * Abstracts normalization of scores based on L2 method
 */
public class L2ScoreNormalizationTechnique implements ScoreNormalizationTechnique {

    public static final String TECHNIQUE_NAME = "l2";
    private static final float MIN_SCORE = 0.001f;

    /**
     * L2 normalization method.
     * n_score_i = score_i/sqrt(score1^2 + score2^2 + ... + scoren^2)
     * Main algorithm steps:
     * - calculate sum of squares of all scores
     * - iterate over each result and update score as per formula above where "score" is raw score returned by Hybrid query
     */
    @Override
    public void normalize(final List<CompoundTopDocs> queryTopDocs) {
        int numOfSubqueries = queryTopDocs.stream()
            .filter(Objects::nonNull)
            .filter(topDocs -> topDocs.getCompoundTopDocs().size() > 0)
            .findAny()
            .get()
            .getCompoundTopDocs()
            .size();
        // get l2 norms for each sub-query
        float[] normsPerSubquery = getL2Norm(queryTopDocs, numOfSubqueries);

        // do normalization using actual score and l2 norm
        for (CompoundTopDocs compoundQueryTopDocs : queryTopDocs) {
            if (Objects.isNull(compoundQueryTopDocs)) {
                continue;
            }
            List<TopDocs> topDocsPerSubQuery = compoundQueryTopDocs.getCompoundTopDocs();
            for (int j = 0; j < topDocsPerSubQuery.size(); j++) {
                TopDocs subQueryTopDoc = topDocsPerSubQuery.get(j);
                for (ScoreDoc scoreDoc : subQueryTopDoc.scoreDocs) {
                    scoreDoc.score = normalizeSingleScore(scoreDoc.score, normsPerSubquery[j]);
                }
            }
        }
    }

    private float[] getL2Norm(final List<CompoundTopDocs> queryTopDocs, final int numOfSubqueries) {
        float[] l2Norms = new float[numOfSubqueries];
        for (CompoundTopDocs compoundQueryTopDocs : queryTopDocs) {
            if (Objects.isNull(compoundQueryTopDocs)) {
                continue;
            }
            List<TopDocs> topDocsPerSubQuery = compoundQueryTopDocs.getCompoundTopDocs();
            IntStream.range(0, topDocsPerSubQuery.size()).forEach(index -> {
                for (ScoreDoc scoreDocs : topDocsPerSubQuery.get(index).scoreDocs) {
                    l2Norms[index] += scoreDocs.score * scoreDocs.score;
                }
            });
        }
        for (int index = 0; index < l2Norms.length; index++) {
            l2Norms[index] = (float) Math.sqrt(l2Norms[index]);
        }
        return l2Norms;
    }

    private float normalizeSingleScore(final float score, final float l2Norm) {
        return l2Norm == 0 ? MIN_SCORE : score / l2Norm;
    }
}
