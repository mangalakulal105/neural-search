/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.neuralsearch.search.util;

import lombok.SneakyThrows;
import org.apache.lucene.queries.function.FunctionScoreQuery;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.junit.Before;
import org.opensearch.Version;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.common.settings.Settings;
import org.opensearch.index.IndexSettings;
import org.opensearch.index.query.QueryShardContext;
import org.opensearch.neuralsearch.query.HybridQuery;
import org.opensearch.neuralsearch.query.NeuralSparseQuery;
import org.opensearch.search.internal.SearchContext;
import org.opensearch.search.rescore.QueryRescorer;
import org.opensearch.search.rescore.RescoreContext;
import org.opensearch.test.OpenSearchTestCase;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.neuralsearch.search.util.NeuralSparseTwoPhaseUtil.addRescoreContextFromNeuralSparseQuery;

public class NeuralSparseTwoPhaseUtilTests extends OpenSearchTestCase {

    private static final String TWO_PHASE_ENABLED_SETTING_KEY = "index.neural_sparse.two_phase.default_enabled";
    private static final String TWO_PHASE_WINDOW_SIZE_EXPANSION_SETTING_KEY = "index.neural_sparse.two_phase.default_window_size_expansion";
    private static final String TWO_PHASE_PRUNE_RATIO_SETTING_KEY = "index.neural_sparse.two_phase.default_pruning_ratio";
    private static final String TWO_PHASE_MAX_WINDOW_SIZE_KEY = "index.neural_sparse.two_phase.max_window_size";
    private final SearchContext mockSearchContext = mock(SearchContext.class, RETURNS_DEEP_STUBS);

    private final QueryShardContext mockQueryShardContext = mock(QueryShardContext.class);
    private NeuralSparseQuery normalNeuralSparseQuery;
    private final Query currentQuery = mock(Query.class);
    private final Query highScoreTokenQuery = mock(Query.class);
    private final Query lowScoreTokenQuery = mock(Query.class);

    @SneakyThrows
    @Before
    public void testInitialize() {
        normalNeuralSparseQuery = new NeuralSparseQuery(currentQuery, highScoreTokenQuery, lowScoreTokenQuery, 5f);
        when(mockSearchContext.getQueryShardContext()).thenReturn(mockQueryShardContext);
        when(mockSearchContext.size()).thenReturn(10);
        when(mockSearchContext.rescore()).thenReturn(Collections.emptyList());
        when(mockQueryShardContext.getIndexSettings()).thenReturn(getCustomTwoPhaseIndexSettings(true, 5.0f, 0.4f, 10000));
    }

    private IndexSettings getCustomTwoPhaseIndexSettings(boolean enabled, float expansion, float ratio, int windowSize) {
        IndexMetadata indexMetadata = IndexMetadata.builder("_index")
            .settings(Settings.builder().put(IndexMetadata.SETTING_VERSION_CREATED, Version.CURRENT))
            .numberOfShards(1)
            .numberOfReplicas(0)
            .creationDate(System.currentTimeMillis())
            .build();

        return new IndexSettings(
            indexMetadata,
            Settings.builder()
                .put(TWO_PHASE_ENABLED_SETTING_KEY, enabled)
                .put(TWO_PHASE_WINDOW_SIZE_EXPANSION_SETTING_KEY, expansion)
                .put(TWO_PHASE_PRUNE_RATIO_SETTING_KEY, ratio)
                .put(TWO_PHASE_MAX_WINDOW_SIZE_KEY, windowSize)
                .build()
        );
    }

    @SneakyThrows
    public void testAddTwoPhaseNeuralSparseQuery_whenQuery2WeightEmpty_thenNoRescoreAdded() {
        Query query = mock(Query.class);
        addRescoreContextFromNeuralSparseQuery(query, mockSearchContext);
        verify(mockSearchContext, never()).addRescore(any());
    }

    @SneakyThrows
    public void testAddTwoPhaseNeuralSparseQuery_whenUnSupportedQuery_thenNoRescoreAdded() {
        FunctionScoreQuery functionScoreQuery = new FunctionScoreQuery(normalNeuralSparseQuery, mock(DoubleValuesSource.class));
        addRescoreContextFromNeuralSparseQuery(functionScoreQuery, mockSearchContext);
        DisjunctionMaxQuery disjunctionMaxQuery = new DisjunctionMaxQuery(Collections.emptyList(), 1.0f);
        addRescoreContextFromNeuralSparseQuery(disjunctionMaxQuery, mockSearchContext);
        List<Query> subQueries = new ArrayList<>();
        List<Query> filterQueries = new ArrayList<>();
        subQueries.add(normalNeuralSparseQuery);
        filterQueries.add(new MatchAllDocsQuery());
        HybridQuery hybridQuery = new HybridQuery(subQueries, filterQueries);
        addRescoreContextFromNeuralSparseQuery(hybridQuery, mockSearchContext);
        assertEquals(normalNeuralSparseQuery.getCurrentQuery(), currentQuery);
        verify(mockSearchContext, never()).addRescore(any());
    }

    @SneakyThrows
    public void testAddTwoPhaseNeuralSparseQuery_whenSingleEntryInQuery2Weight_thenRescoreAdded() {
        NeuralSparseQuery neuralSparseQuery = new NeuralSparseQuery(mock(Query.class), mock(Query.class), mock(Query.class), 5.0f);
        addRescoreContextFromNeuralSparseQuery(neuralSparseQuery, mockSearchContext);
        verify(mockSearchContext).addRescore(any(QueryRescorer.QueryRescoreContext.class));
    }

    @SneakyThrows
    public void testAddTwoPhaseNeuralSparseQuery_whenCompoundBooleanQuery_thenRescoreAdded() {
        NeuralSparseQuery neuralSparseQuery1 = new NeuralSparseQuery(mock(Query.class), mock(Query.class), mock(Query.class), 4f);
        NeuralSparseQuery neuralSparseQuery2 = new NeuralSparseQuery(mock(Query.class), mock(Query.class), mock(Query.class), 4f);
        BoostQuery boostQuery1 = new BoostQuery(neuralSparseQuery1, 2f);
        BoostQuery boostQuery2 = new BoostQuery(neuralSparseQuery2, 3f);
        BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
        queryBuilder.add(boostQuery1, BooleanClause.Occur.SHOULD);
        queryBuilder.add(boostQuery2, BooleanClause.Occur.SHOULD);
        BooleanQuery booleanQuery = queryBuilder.build();
        addRescoreContextFromNeuralSparseQuery(booleanQuery, mockSearchContext);
        verify(mockSearchContext).addRescore(any(QueryRescorer.QueryRescoreContext.class));
    }

    @SneakyThrows
    public void testAddTwoPhaseNeuralSparseQuery_whenBooleanClauseType_thenVerifyBoosts() {
        NeuralSparseQuery neuralSparseQuery1 = new NeuralSparseQuery(mock(Query.class), mock(Query.class), mock(Query.class), 5f);
        NeuralSparseQuery neuralSparseQuery2 = new NeuralSparseQuery(mock(Query.class), mock(Query.class), mock(Query.class), 5f);
        NeuralSparseQuery neuralSparseQuery3 = new NeuralSparseQuery(mock(Query.class), mock(Query.class), mock(Query.class), 5f);
        NeuralSparseQuery neuralSparseQuery4 = new NeuralSparseQuery(mock(Query.class), mock(Query.class), mock(Query.class), 5f);
        BoostQuery boostQuery1 = new BoostQuery(neuralSparseQuery1, 2f);
        BoostQuery boostQuery2 = new BoostQuery(neuralSparseQuery2, 3f);
        BoostQuery boostQuery3 = new BoostQuery(neuralSparseQuery3, 4f);
        BoostQuery boostQuery4 = new BoostQuery(neuralSparseQuery4, 5f);
        BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
        queryBuilder.add(boostQuery1, BooleanClause.Occur.SHOULD);
        queryBuilder.add(boostQuery2, BooleanClause.Occur.MUST);
        queryBuilder.add(boostQuery3, BooleanClause.Occur.FILTER);
        queryBuilder.add(boostQuery4, BooleanClause.Occur.MUST_NOT);
        BooleanQuery booleanQuery = queryBuilder.build();
        addRescoreContextFromNeuralSparseQuery(booleanQuery, mockSearchContext);
        ArgumentCaptor<RescoreContext> rtxCaptor = ArgumentCaptor.forClass(RescoreContext.class);
        verify(mockSearchContext).addRescore(rtxCaptor.capture());
        QueryRescorer.QueryRescoreContext context = (QueryRescorer.QueryRescoreContext) rtxCaptor.getValue();
        BooleanQuery rescoreQuery = (BooleanQuery) context.query();
        List<BooleanClause> clauses = rescoreQuery.clauses();
        List<Float> shouldBoosts = new ArrayList<>();
        for (BooleanClause clause : clauses) {
            if (clause.getOccur() == BooleanClause.Occur.SHOULD) {
                Query query = clause.getQuery();
                if (query instanceof BoostQuery) {
                    BoostQuery boostQuery = (BoostQuery) query;
                    shouldBoosts.add(boostQuery.getBoost());
                }
            }
        }
        assertEquals(shouldBoosts.size(), 2);
        assertTrue("Should contain boost 2.0", shouldBoosts.contains(2.0f));
        assertTrue("Should contain boost 3.0", shouldBoosts.contains(3.0f));
    }

    @SneakyThrows
    public void testWindowSize_whenNormalConditions_thenWindowSizeIsAsSet() {
        NeuralSparseQuery query = normalNeuralSparseQuery;
        addRescoreContextFromNeuralSparseQuery(query, mockSearchContext);
        ArgumentCaptor<QueryRescorer.QueryRescoreContext> rescoreContextArgumentCaptor = ArgumentCaptor.forClass(
            QueryRescorer.QueryRescoreContext.class
        );
        verify(mockSearchContext).addRescore(rescoreContextArgumentCaptor.capture());
        assertEquals(50, rescoreContextArgumentCaptor.getValue().getWindowSize());
    }

    @SneakyThrows
    public void testWindowSize_whenBoundaryConditions_thenThrowException() {
        NeuralSparseQuery query = new NeuralSparseQuery(new MatchAllDocsQuery(), new MatchAllDocsQuery(), new MatchAllDocsQuery(), 5000f);
        NeuralSparseQuery finalQuery1 = query;
        expectThrows(IllegalArgumentException.class, () -> { addRescoreContextFromNeuralSparseQuery(finalQuery1, mockSearchContext); });
        query = new NeuralSparseQuery(new MatchAllDocsQuery(), new MatchAllDocsQuery(), new MatchAllDocsQuery(), Float.MAX_VALUE);
        NeuralSparseQuery finalQuery = query;
        expectThrows(IllegalArgumentException.class, () -> { addRescoreContextFromNeuralSparseQuery(finalQuery, mockSearchContext); });
    }

    @SneakyThrows
    public void testRescoreListWeightCalculation_whenMultipleRescoreContexts_thenCalculateCorrectWeight() {
        QueryRescorer.QueryRescoreContext mockContext1 = mock(QueryRescorer.QueryRescoreContext.class);
        QueryRescorer.QueryRescoreContext mockContext2 = mock(QueryRescorer.QueryRescoreContext.class);
        when(mockContext1.queryWeight()).thenReturn(2.0f);
        when(mockContext2.queryWeight()).thenReturn(3.0f);
        List<RescoreContext> rescoreContextList = Arrays.asList(mockContext1, mockContext2);
        when(mockSearchContext.rescore()).thenReturn(rescoreContextList);
        NeuralSparseQuery query = normalNeuralSparseQuery;
        addRescoreContextFromNeuralSparseQuery(query, mockSearchContext);
        ArgumentCaptor<RescoreContext> rtxCaptor = ArgumentCaptor.forClass(RescoreContext.class);
        verify(mockSearchContext).addRescore(rtxCaptor.capture());
        QueryRescorer.QueryRescoreContext context = (QueryRescorer.QueryRescoreContext) rtxCaptor.getValue();
        assertEquals(context.rescoreQueryWeight(), 6.0f, 0.01f);
    }

    @SneakyThrows
    public void testEmptyRescoreListWeight_whenRescoreListEmpty_thenDefaultWeightUsed() {
        when(mockSearchContext.rescore()).thenReturn(Collections.emptyList());
        NeuralSparseQuery query = normalNeuralSparseQuery;
        addRescoreContextFromNeuralSparseQuery(query, mockSearchContext);
        ArgumentCaptor<RescoreContext> rtxCaptor = ArgumentCaptor.forClass(RescoreContext.class);
        verify(mockSearchContext).addRescore(rtxCaptor.capture());
        QueryRescorer.QueryRescoreContext context = (QueryRescorer.QueryRescoreContext) rtxCaptor.getValue();
        assertEquals(context.rescoreQueryWeight(), 1.0f, 0.01f);
    }

    @SneakyThrows
    public void testAddRescoreContext_whenOverEdgeWindowSize_thenThrowException() {
        when(mockSearchContext.size()).thenReturn(100);
        when(mockQueryShardContext.getIndexSettings()).thenReturn(getCustomTwoPhaseIndexSettings(true,10.0f,0.4f,50));
        expectThrows(
            IllegalArgumentException.class,
            ()->addRescoreContextFromNeuralSparseQuery(normalNeuralSparseQuery,mockSearchContext)
        );
        when(mockSearchContext.size()).thenReturn(10);
        when(mockSearchContext.rescore()).thenReturn(Collections.emptyList());
        addRescoreContextFromNeuralSparseQuery(normalNeuralSparseQuery,mockSearchContext);
        ArgumentCaptor<QueryRescorer.QueryRescoreContext> rescoreContextArgumentCaptor = ArgumentCaptor.forClass(
            QueryRescorer.QueryRescoreContext.class
        );
        verify(mockSearchContext).addRescore(rescoreContextArgumentCaptor.capture());
        assertEquals(50, rescoreContextArgumentCaptor.getValue().getWindowSize());
    }

}
