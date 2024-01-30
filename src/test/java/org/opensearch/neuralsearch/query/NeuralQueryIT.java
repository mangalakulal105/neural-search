/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.neuralsearch.query;

import static org.opensearch.neuralsearch.TestUtils.DELTA_FOR_SCORE_ASSERTION;
import static org.opensearch.neuralsearch.TestUtils.TEST_DIMENSION;
import static org.opensearch.neuralsearch.TestUtils.TEST_SPACE_TYPE;
import static org.opensearch.neuralsearch.TestUtils.createRandomVector;
import static org.opensearch.neuralsearch.TestUtils.objectToFloat;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.index.query.MatchQueryBuilder;
import org.opensearch.neuralsearch.BaseNeuralSearchIT;

import com.google.common.primitives.Floats;

import lombok.SneakyThrows;

public class NeuralQueryIT extends BaseNeuralSearchIT {
    private static final String TEST_BASIC_INDEX_NAME = "test-neural-basic-index";
    private static final String TEST_MULTI_VECTOR_FIELD_INDEX_NAME = "test-neural-multi-vector-field-index";
    private static final String TEST_TEXT_AND_VECTOR_FIELD_INDEX_NAME = "test-neural-text-and-vector-field-index";
    private static final String TEST_NESTED_INDEX_NAME = "test-neural-nested-index";
    private static final String TEST_MULTI_DOC_INDEX_NAME = "test-neural-multi-doc-index";
    private static final String TEST_QUERY_TEXT = "Hello world";
    private static final String TEST_IMAGE_TEXT = "/9j/4AAQSkZJRgABAQAASABIAAD";
    private static final String TEST_KNN_VECTOR_FIELD_NAME_1 = "test-knn-vector-1";
    private static final String TEST_KNN_VECTOR_FIELD_NAME_2 = "test-knn-vector-2";
    private static final String TEST_TEXT_FIELD_NAME_1 = "test-text-field";
    private static final String TEST_KNN_VECTOR_FIELD_NAME_NESTED = "nested.knn.field";
    private final float[] testVector = createRandomVector(TEST_DIMENSION);

    @Before
    public void setUp() throws Exception {
        super.setUp();
        updateClusterSettings();
    }

    /**
     * Tests basic query:
     * {
     *     "query": {
     *         "neural": {
     *             "text_knn": {
     *                 "query_text": "Hello world",
     *                 "model_id": "dcsdcasd",
     *                 "k": 1
     *             }
     *         }
     *     }
     * }
     */
    @SneakyThrows
    public void testBasicQuery() {
        String modelId = null;
        try {
            initializeIndexIfNotExist(TEST_BASIC_INDEX_NAME);
            modelId = prepareModel();
            NeuralQueryBuilder neuralQueryBuilder = new NeuralQueryBuilder(
                TEST_KNN_VECTOR_FIELD_NAME_1,
                TEST_QUERY_TEXT,
                "",
                modelId,
                1,
                null,
                null
            );
            Map<String, Object> searchResponseAsMap = search(TEST_BASIC_INDEX_NAME, neuralQueryBuilder, 1);
            Map<String, Object> firstInnerHit = getFirstInnerHit(searchResponseAsMap);

            assertEquals("1", firstInnerHit.get("_id"));
            float expectedScore = computeExpectedScore(modelId, testVector, TEST_SPACE_TYPE, TEST_QUERY_TEXT);
            assertEquals(expectedScore, objectToFloat(firstInnerHit.get("_score")), DELTA_FOR_SCORE_ASSERTION);
        } finally {
            wipeOfTestResources(TEST_BASIC_INDEX_NAME, null, modelId, null);
        }
    }

    /**
     * Tests basic query with boost parameter:
     * {
     *     "query": {
     *         "neural": {
     *             "text_knn": {
     *                 "query_text": "Hello world",
     *                 "model_id": "dcsdcasd",
     *                 "k": 1,
     *                 "boost": 2.0
     *             }
     *         }
     *     }
     * }
     */
    @SneakyThrows
    public void testBoostQuery() {
        String modelId = null;
        try {
            initializeIndexIfNotExist(TEST_BASIC_INDEX_NAME);
            modelId = prepareModel();
            NeuralQueryBuilder neuralQueryBuilder = new NeuralQueryBuilder(
                TEST_KNN_VECTOR_FIELD_NAME_1,
                TEST_QUERY_TEXT,
                "",
                modelId,
                1,
                null,
                null
            );

            final float boost = 2.0f;
            neuralQueryBuilder.boost(boost);
            Map<String, Object> searchResponseAsMap = search(TEST_BASIC_INDEX_NAME, neuralQueryBuilder, 1);
            Map<String, Object> firstInnerHit = getFirstInnerHit(searchResponseAsMap);

            assertEquals("1", firstInnerHit.get("_id"));
            float expectedScore = 2 * computeExpectedScore(modelId, testVector, TEST_SPACE_TYPE, TEST_QUERY_TEXT);
            assertEquals(expectedScore, objectToFloat(firstInnerHit.get("_score")), DELTA_FOR_SCORE_ASSERTION);
        } finally {
            wipeOfTestResources(TEST_BASIC_INDEX_NAME, null, modelId, null);
        }
    }

    /**
     * Tests rescore query:
     * {
     *     "query" : {
     *       "match_all": {}
     *     },
     *     "rescore": {
     *         "query": {
     *              "rescore_query": {
     *                  "neural": {
     *                      "text_knn": {
     *                          "query_text": "Hello world",
     *                          "model_id": "dcsdcasd",
     *                          "k": 1
     *                      }
     *                  }
     *              }
     *          }
     *    }
     */
    @SneakyThrows
    public void testRescoreQuery() {
        String modelId = null;
        try {
            initializeIndexIfNotExist(TEST_BASIC_INDEX_NAME);
            modelId = prepareModel();
            MatchAllQueryBuilder matchAllQueryBuilder = new MatchAllQueryBuilder();
            NeuralQueryBuilder rescoreNeuralQueryBuilder = new NeuralQueryBuilder(
                TEST_KNN_VECTOR_FIELD_NAME_1,
                TEST_QUERY_TEXT,
                "",
                modelId,
                1,
                null,
                null
            );

            Map<String, Object> searchResponseAsMap = search(TEST_BASIC_INDEX_NAME, matchAllQueryBuilder, rescoreNeuralQueryBuilder, 1);
            Map<String, Object> firstInnerHit = getFirstInnerHit(searchResponseAsMap);

            assertEquals("1", firstInnerHit.get("_id"));
            float expectedScore = computeExpectedScore(modelId, testVector, TEST_SPACE_TYPE, TEST_QUERY_TEXT);
            assertEquals(expectedScore, objectToFloat(firstInnerHit.get("_score")), DELTA_FOR_SCORE_ASSERTION);
        } finally {
            wipeOfTestResources(TEST_BASIC_INDEX_NAME, null, modelId, null);
        }
    }

    /**
     * Tests bool should query with vectors:
     * {
     *     "query": {
     *         "bool" : {
     *             "should": [
     *                 "neural": {
     *                     "field_1": {
     *                         "query_text": "Hello world",
     *                         "model_id": "dcsdcasd",
     *                         "k": 1
     *                     },
     *                  },
     *                  "neural": {
     *                     "field_2": {
     *                         "query_text": "Hello world",
     *                         "model_id": "dcsdcasd",
     *                         "k": 1
     *                     }
     *                  }
     *             ]
     *         }
     *     }
     * }
     */
    @SneakyThrows
    public void testBooleanQuery_withMultipleNeuralQueries() {
        String modelId = null;
        try {
            initializeIndexIfNotExist(TEST_MULTI_VECTOR_FIELD_INDEX_NAME);
            modelId = prepareModel();
            BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();

            NeuralQueryBuilder neuralQueryBuilder1 = new NeuralQueryBuilder(
                TEST_KNN_VECTOR_FIELD_NAME_1,
                TEST_QUERY_TEXT,
                "",
                modelId,
                1,
                null,
                null
            );
            NeuralQueryBuilder neuralQueryBuilder2 = new NeuralQueryBuilder(
                TEST_KNN_VECTOR_FIELD_NAME_2,
                TEST_QUERY_TEXT,
                "",
                modelId,
                1,
                null,
                null
            );

            boolQueryBuilder.should(neuralQueryBuilder1).should(neuralQueryBuilder2);

            Map<String, Object> searchResponseAsMap = search(TEST_MULTI_VECTOR_FIELD_INDEX_NAME, boolQueryBuilder, 1);
            Map<String, Object> firstInnerHit = getFirstInnerHit(searchResponseAsMap);

            assertEquals("1", firstInnerHit.get("_id"));
            float expectedScore = 2 * computeExpectedScore(modelId, testVector, TEST_SPACE_TYPE, TEST_QUERY_TEXT);
            assertEquals(expectedScore, objectToFloat(firstInnerHit.get("_score")), DELTA_FOR_SCORE_ASSERTION);
        } finally {
            wipeOfTestResources(TEST_MULTI_VECTOR_FIELD_INDEX_NAME, null, modelId, null);
        }
    }

    /**
     * Tests bool should with BM25 and neural query:
     * {
     *     "query": {
     *         "bool" : {
     *             "should": [
     *                 "neural": {
     *                     "field_1": {
     *                         "query_text": "Hello world",
     *                         "model_id": "dcsdcasd",
     *                         "k": 1
     *                     },
     *                  },
     *                  "match": {
     *                     "field_2": {
     *                          "query": "Hello world"
     *                     }
     *                  }
     *             ]
     *         }
     *     }
     * }
     */
    @SneakyThrows
    public void testBooleanQuery_withNeuralAndBM25Queries() {
        String modelId = null;
        try {
            initializeIndexIfNotExist(TEST_TEXT_AND_VECTOR_FIELD_INDEX_NAME);
            modelId = prepareModel();
            BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();

            NeuralQueryBuilder neuralQueryBuilder = new NeuralQueryBuilder(
                TEST_KNN_VECTOR_FIELD_NAME_1,
                TEST_QUERY_TEXT,
                "",
                modelId,
                1,
                null,
                null
            );

            MatchQueryBuilder matchQueryBuilder = new MatchQueryBuilder(TEST_TEXT_FIELD_NAME_1, TEST_QUERY_TEXT);

            boolQueryBuilder.should(neuralQueryBuilder).should(matchQueryBuilder);

            Map<String, Object> searchResponseAsMap = search(TEST_TEXT_AND_VECTOR_FIELD_INDEX_NAME, boolQueryBuilder, 1);
            Map<String, Object> firstInnerHit = getFirstInnerHit(searchResponseAsMap);

            assertEquals("1", firstInnerHit.get("_id"));
            float minExpectedScore = computeExpectedScore(modelId, testVector, TEST_SPACE_TYPE, TEST_QUERY_TEXT);
            assertTrue(minExpectedScore < objectToFloat(firstInnerHit.get("_score")));
        } finally {
            wipeOfTestResources(TEST_TEXT_AND_VECTOR_FIELD_INDEX_NAME, null, modelId, null);
        }
    }

    /**
     * Tests nested query:
     * {
     *     "query": {
     *         "nested" : {
     *             "query": {
     *                 "neural": {
     *                     "field_1": {
     *                         "query_text": "Hello world",
     *                         "model_id": "dcsdcasd",
     *                         "k": 1
     *                     },
     *                  }
     *              }
     *          }
     *      }
     * }
     */
    @SneakyThrows
    public void testNestedQuery() {
        String modelId = null;
        try {
            initializeIndexIfNotExist(TEST_NESTED_INDEX_NAME);
            modelId = prepareModel();
            NeuralQueryBuilder neuralQueryBuilder = new NeuralQueryBuilder(
                TEST_KNN_VECTOR_FIELD_NAME_NESTED,
                TEST_QUERY_TEXT,
                "",
                modelId,
                1,
                null,
                null
            );

            Map<String, Object> searchResponseAsMap = search(TEST_NESTED_INDEX_NAME, neuralQueryBuilder, 1);
            Map<String, Object> firstInnerHit = getFirstInnerHit(searchResponseAsMap);

            assertEquals("1", firstInnerHit.get("_id"));
            float expectedScore = computeExpectedScore(modelId, testVector, TEST_SPACE_TYPE, TEST_QUERY_TEXT);
            assertEquals(expectedScore, objectToFloat(firstInnerHit.get("_score")), DELTA_FOR_SCORE_ASSERTION);
        } finally {
            wipeOfTestResources(TEST_NESTED_INDEX_NAME, null, modelId, null);
        }
    }

    /**
     * Tests filter query:
     * {
     *     "query": {
     *         "neural": {
     *             "text_knn": {
     *                 "query_text": "Hello world",
     *                 "model_id": "dcsdcasd",
     *                 "k": 1,
     *                 "filter": {
     *                     "match": {
     *                         "_id": {
     *                             "query": "3"
     *                         }
     *                     }
     *                 }
     *             }
     *         }
     *     }
     * }
     */
    @SneakyThrows
    public void testFilterQuery() {
        String modelId = null;
        try {
            initializeIndexIfNotExist(TEST_MULTI_DOC_INDEX_NAME);
            modelId = prepareModel();
            NeuralQueryBuilder neuralQueryBuilder = new NeuralQueryBuilder(
                TEST_KNN_VECTOR_FIELD_NAME_1,
                TEST_QUERY_TEXT,
                "",
                modelId,
                1,
                null,
                new MatchQueryBuilder("_id", "3")
            );
            Map<String, Object> searchResponseAsMap = search(TEST_MULTI_DOC_INDEX_NAME, neuralQueryBuilder, 3);
            assertEquals(1, getHitCount(searchResponseAsMap));
            Map<String, Object> firstInnerHit = getFirstInnerHit(searchResponseAsMap);
            assertEquals("3", firstInnerHit.get("_id"));
            float expectedScore = computeExpectedScore(modelId, testVector, TEST_SPACE_TYPE, TEST_QUERY_TEXT);
            assertEquals(expectedScore, objectToFloat(firstInnerHit.get("_score")), DELTA_FOR_SCORE_ASSERTION);
        } finally {
            wipeOfTestResources(TEST_MULTI_DOC_INDEX_NAME, null, modelId, null);
        }
    }

    /**
     * Tests basic query for multimodal:
     * {
     *     "query": {
     *         "neural": {
     *             "text_knn": {
     *                 "query_text": "Hello world",
     *                 "query_image": "base64_1234567890",
     *                 "model_id": "dcsdcasd",
     *                 "k": 1
     *             }
     *         }
     *     }
     * }
     */
    @SneakyThrows
    public void testMultimodalQuery() {
        String modelId = null;
        try {
            initializeIndexIfNotExist(TEST_BASIC_INDEX_NAME);
            modelId = prepareModel();
            NeuralQueryBuilder neuralQueryBuilder = new NeuralQueryBuilder(
                TEST_KNN_VECTOR_FIELD_NAME_1,
                TEST_QUERY_TEXT,
                TEST_IMAGE_TEXT,
                modelId,
                1,
                null,
                null
            );
            Map<String, Object> searchResponseAsMap = search(TEST_BASIC_INDEX_NAME, neuralQueryBuilder, 1);
            Map<String, Object> firstInnerHit = getFirstInnerHit(searchResponseAsMap);

            assertEquals("1", firstInnerHit.get("_id"));
            float expectedScore = computeExpectedScore(modelId, testVector, TEST_SPACE_TYPE, TEST_QUERY_TEXT);
            assertEquals(expectedScore, objectToFloat(firstInnerHit.get("_score")), DELTA_FOR_SCORE_ASSERTION);
        } finally {
            wipeOfTestResources(TEST_BASIC_INDEX_NAME, null, modelId, null);
        }
    }

    @SneakyThrows
    private void initializeIndexIfNotExist(String indexName) {
        if (TEST_BASIC_INDEX_NAME.equals(indexName) && !indexExists(TEST_BASIC_INDEX_NAME)) {
            prepareKnnIndex(
                TEST_BASIC_INDEX_NAME,
                Collections.singletonList(new KNNFieldConfig(TEST_KNN_VECTOR_FIELD_NAME_1, TEST_DIMENSION, TEST_SPACE_TYPE))
            );
            addKnnDoc(
                TEST_BASIC_INDEX_NAME,
                "1",
                Collections.singletonList(TEST_KNN_VECTOR_FIELD_NAME_1),
                Collections.singletonList(Floats.asList(testVector).toArray())
            );
            assertEquals(1, getDocCount(TEST_BASIC_INDEX_NAME));
        }

        if (TEST_MULTI_VECTOR_FIELD_INDEX_NAME.equals(indexName) && !indexExists(TEST_MULTI_VECTOR_FIELD_INDEX_NAME)) {
            prepareKnnIndex(
                TEST_MULTI_VECTOR_FIELD_INDEX_NAME,
                List.of(
                    new KNNFieldConfig(TEST_KNN_VECTOR_FIELD_NAME_1, TEST_DIMENSION, TEST_SPACE_TYPE),
                    new KNNFieldConfig(TEST_KNN_VECTOR_FIELD_NAME_2, TEST_DIMENSION, TEST_SPACE_TYPE)
                )
            );
            addKnnDoc(
                TEST_MULTI_VECTOR_FIELD_INDEX_NAME,
                "1",
                List.of(TEST_KNN_VECTOR_FIELD_NAME_1, TEST_KNN_VECTOR_FIELD_NAME_2),
                List.of(Floats.asList(testVector).toArray(), Floats.asList(testVector).toArray())
            );
            assertEquals(1, getDocCount(TEST_MULTI_VECTOR_FIELD_INDEX_NAME));
        }

        if (TEST_NESTED_INDEX_NAME.equals(indexName) && !indexExists(TEST_NESTED_INDEX_NAME)) {
            prepareKnnIndex(
                TEST_NESTED_INDEX_NAME,
                Collections.singletonList(new KNNFieldConfig(TEST_KNN_VECTOR_FIELD_NAME_NESTED, TEST_DIMENSION, TEST_SPACE_TYPE))
            );
            addKnnDoc(
                TEST_NESTED_INDEX_NAME,
                "1",
                Collections.singletonList(TEST_KNN_VECTOR_FIELD_NAME_NESTED),
                Collections.singletonList(Floats.asList(testVector).toArray())
            );
            assertEquals(1, getDocCount(TEST_NESTED_INDEX_NAME));
        }

        if (TEST_TEXT_AND_VECTOR_FIELD_INDEX_NAME.equals(indexName) && !indexExists(TEST_TEXT_AND_VECTOR_FIELD_INDEX_NAME)) {
            prepareKnnIndex(
                TEST_TEXT_AND_VECTOR_FIELD_INDEX_NAME,
                Collections.singletonList(new KNNFieldConfig(TEST_KNN_VECTOR_FIELD_NAME_1, TEST_DIMENSION, TEST_SPACE_TYPE))
            );
            addKnnDoc(
                TEST_TEXT_AND_VECTOR_FIELD_INDEX_NAME,
                "1",
                Collections.singletonList(TEST_KNN_VECTOR_FIELD_NAME_1),
                Collections.singletonList(Floats.asList(testVector).toArray()),
                Collections.singletonList(TEST_TEXT_FIELD_NAME_1),
                Collections.singletonList(TEST_QUERY_TEXT)
            );
            assertEquals(1, getDocCount(TEST_TEXT_AND_VECTOR_FIELD_INDEX_NAME));
        }

        if (TEST_MULTI_DOC_INDEX_NAME.equals(indexName) && !indexExists(TEST_MULTI_DOC_INDEX_NAME)) {
            prepareKnnIndex(
                TEST_MULTI_DOC_INDEX_NAME,
                Collections.singletonList(new KNNFieldConfig(TEST_KNN_VECTOR_FIELD_NAME_1, TEST_DIMENSION, TEST_SPACE_TYPE))
            );
            addKnnDoc(
                TEST_MULTI_DOC_INDEX_NAME,
                "1",
                Collections.singletonList(TEST_KNN_VECTOR_FIELD_NAME_1),
                Collections.singletonList(Floats.asList(testVector).toArray())
            );
            addKnnDoc(
                TEST_MULTI_DOC_INDEX_NAME,
                "2",
                Collections.singletonList(TEST_KNN_VECTOR_FIELD_NAME_1),
                Collections.singletonList(Floats.asList(testVector).toArray())
            );
            addKnnDoc(
                TEST_MULTI_DOC_INDEX_NAME,
                "3",
                Collections.singletonList(TEST_KNN_VECTOR_FIELD_NAME_1),
                Collections.singletonList(Floats.asList(testVector).toArray())
            );
            assertEquals(3, getDocCount(TEST_MULTI_DOC_INDEX_NAME));
        }
    }
}
