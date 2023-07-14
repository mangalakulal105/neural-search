/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.neuralsearch.plugin;

import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.opensearch.ingest.Processor;
import org.opensearch.neuralsearch.processor.TextEmbeddingProcessor;
import org.opensearch.neuralsearch.query.HybridQueryBuilder;
import org.opensearch.neuralsearch.query.NeuralQueryBuilder;
import org.opensearch.neuralsearch.query.OpenSearchQueryTestCase;
import org.opensearch.neuralsearch.search.query.HybridQueryPhaseSearcher;
import org.opensearch.plugins.SearchPlugin;
import org.opensearch.search.query.QueryPhaseSearcher;

public class NeuralSearchTests extends OpenSearchQueryTestCase {

    public void testQuerySpecs() {
        NeuralSearch plugin = new NeuralSearch();
        List<SearchPlugin.QuerySpec<?>> querySpecs = plugin.getQueries();

        assertNotNull(querySpecs);
        assertFalse(querySpecs.isEmpty());
        assertTrue(querySpecs.stream().anyMatch(spec -> NeuralQueryBuilder.NAME.equals(spec.getName().getPreferredName())));
        assertTrue(querySpecs.stream().anyMatch(spec -> HybridQueryBuilder.NAME.equals(spec.getName().getPreferredName())));
    }

    public void testQueryPhaseSearcher() {
        NeuralSearch plugin = new NeuralSearch();
        Optional<QueryPhaseSearcher> queryPhaseSearcher = plugin.getQueryPhaseSearcher();

        assertNotNull(queryPhaseSearcher);
        assertTrue(queryPhaseSearcher.isEmpty());

        initFeatureFlags();

        Optional<QueryPhaseSearcher> queryPhaseSearcherWithFeatureFlagDisabled = plugin.getQueryPhaseSearcher();

        assertNotNull(queryPhaseSearcherWithFeatureFlagDisabled);
        assertFalse(queryPhaseSearcherWithFeatureFlagDisabled.isEmpty());
        assertTrue(queryPhaseSearcherWithFeatureFlagDisabled.get() instanceof HybridQueryPhaseSearcher);
    }

    public void testProcessors() {
        NeuralSearch plugin = new NeuralSearch();
        Processor.Parameters processorParams = mock(Processor.Parameters.class);
        Map<String, Processor.Factory> processors = plugin.getProcessors(processorParams);
        assertNotNull(processors);
        assertNotNull(processors.get(TextEmbeddingProcessor.TYPE));
    }
}
