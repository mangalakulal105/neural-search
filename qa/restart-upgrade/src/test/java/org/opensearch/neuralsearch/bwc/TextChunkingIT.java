/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.neuralsearch.bwc;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.opensearch.index.query.MatchAllQueryBuilder;
import static org.opensearch.neuralsearch.TestUtils.NODES_BWC_CLUSTER;

public class TextChunkingIT extends AbstractRestartUpgradeRestTestCase {

    private static final String PIPELINE_NAME = "pipeline-text-chunking";
    private static final String INPUT_FIELD = "body";
    private static final String OUTPUT_FIELD = "body_chunk";
    private static final String TEST_INDEX_SETTING_PATH = "processor/ChunkingIndexSettings.json";
    private static final String TEST_INGEST_TEXT =
        "This is an example document to be chunked. The document contains a single paragraph, two sentences and 24 tokens by standard tokenizer in OpenSearch.";
    List<String> expectedPassages = List.of(
        "This is an example document to be chunked. The document ",
        "contains a single paragraph, two sentences and 24 tokens by ",
        "standard tokenizer in OpenSearch."
    );

    // Test rolling-upgrade text chunking processor
    // Create Text Chunking Processor, Ingestion Pipeline and add document
    // Validate process, pipeline and document count in restart-upgrade scenario
    public void testTextChunkingProcessor_E2EFlow() throws Exception {
        waitForClusterHealthGreen(NODES_BWC_CLUSTER);
        String indexName = getIndexNameForTest();
        if (isRunningAgainstOldCluster()) {
            createPipelineForTextChunkingProcessor(PIPELINE_NAME);
            createChunkingIndex(indexName);
            waitForClusterHealthGreen(NODES_BWC_CLUSTER); // ensure index has been created
            addDocument(indexName, "0", INPUT_FIELD, TEST_INGEST_TEXT, null, null);
            validateTestIndex(indexName, OUTPUT_FIELD, 1, expectedPassages);
        } else {
            try {
                addDocument(indexName, "1", INPUT_FIELD, TEST_INGEST_TEXT, null, null);
                validateTestIndex(indexName, OUTPUT_FIELD, 2, expectedPassages);
            } finally {
                wipeOfTestResources(indexName, PIPELINE_NAME, null, null);
            }
        }
    }

    private void createChunkingIndex(String indexName) throws Exception {
        URL documentURLPath = classLoader.getResource(TEST_INDEX_SETTING_PATH);
        Objects.requireNonNull(documentURLPath);
        String indexSetting = Files.readString(Path.of(documentURLPath.toURI()));
        createIndexWithConfiguration(indexName, indexSetting, PIPELINE_NAME);
    }

    private void validateTestIndex(String indexName, String fieldName, int documentCount, Object expected) {
        int docCount = getDocCount(indexName);
        assertEquals(documentCount, docCount);
        MatchAllQueryBuilder query = new MatchAllQueryBuilder();
        Map<String, Object> searchResults = search(indexName, query, 10);
        assertNotNull(searchResults);
        Map<String, Object> document = getFirstInnerHit(searchResults);
        assertNotNull(document);
        Object documentSource = document.get("_source");
        assert (documentSource instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> documentSourceMap = (Map<String, Object>) documentSource;
        assert (documentSourceMap).containsKey(fieldName);
        Object ingestOutputs = documentSourceMap.get(fieldName);
        assertEquals(expected, ingestOutputs);
    }
}
