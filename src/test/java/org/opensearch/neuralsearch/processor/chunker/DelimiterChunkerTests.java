/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.neuralsearch.processor.chunker;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.Assert;
import org.opensearch.test.OpenSearchTestCase;

import static org.opensearch.neuralsearch.processor.TextChunkingProcessor.TYPE;
import static org.opensearch.neuralsearch.processor.chunker.Chunker.MAX_CHUNK_LIMIT_FIELD;
import static org.opensearch.neuralsearch.processor.chunker.DelimiterChunker.DELIMITER_FIELD;

public class DelimiterChunkerTests extends OpenSearchTestCase {

    public void testCreate_withDelimiterFieldInvalidType_thenFail() {
        Exception exception = assertThrows(
            IllegalArgumentException.class,
            () -> new DelimiterChunker(Map.of(DELIMITER_FIELD, List.of("")))
        );
        Assert.assertEquals(
            String.format(Locale.ROOT, "Parameter [%s] must be of %s type", DELIMITER_FIELD, String.class.getName()),
            exception.getMessage()
        );
    }

    public void testCreate_withDelimiterFieldEmptyString_thenFail() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> new DelimiterChunker(Map.of(DELIMITER_FIELD, "")));
        Assert.assertEquals(String.format(Locale.ROOT, "Parameter [%s] should not be empty.", DELIMITER_FIELD), exception.getMessage());
    }

    public void testChunk_withNewlineDelimiter_thenSucceed() {
        DelimiterChunker chunker = new DelimiterChunker(Map.of(DELIMITER_FIELD, "\n"));
        String content = "a\nb\nc\nd";
        List<String> chunkResult = chunker.chunk(content, Map.of());
        assertEquals(List.of("a\n", "b\n", "c\n", "d"), chunkResult);
    }

    public void testChunk_withDefaultDelimiter_thenSucceed() {
        // default delimiter is \n\n
        DelimiterChunker chunker = new DelimiterChunker(Map.of());
        String content = "a.b\n\nc.d";
        List<String> chunkResult = chunker.chunk(content, Map.of());
        assertEquals(List.of("a.b\n\n", "c.d"), chunkResult);
    }

    public void testChunk_withOnlyDelimiterContent_thenSucceed() {
        DelimiterChunker chunker = new DelimiterChunker(Map.of(DELIMITER_FIELD, "\n"));
        String content = "\n";
        List<String> chunkResult = chunker.chunk(content, Map.of());
        assertEquals(List.of("\n"), chunkResult);
    }

    public void testChunk_WithAllDelimiterContent_thenSucceed() {
        DelimiterChunker chunker = new DelimiterChunker(Map.of(DELIMITER_FIELD, "\n"));
        String content = "\n\n\n";
        List<String> chunkResult = chunker.chunk(content, Map.of());
        assertEquals(List.of("\n", "\n", "\n"), chunkResult);
    }

    public void testChunk_WithPeriodDelimiters_thenSucceed() {
        DelimiterChunker chunker = new DelimiterChunker(Map.of(DELIMITER_FIELD, "."));
        String content = "a.b.cc.d.";
        List<String> chunkResult = chunker.chunk(content, Map.of());
        assertEquals(List.of("a.", "b.", "cc.", "d."), chunkResult);
    }

    public void testChunk_withDoubleNewlineDelimiter_thenSucceed() {
        DelimiterChunker chunker = new DelimiterChunker(Map.of(DELIMITER_FIELD, "\n\n"));
        String content = "\n\na\n\n\n";
        List<String> chunkResult = chunker.chunk(content, Map.of());
        assertEquals(List.of("\n\n", "a\n\n", "\n"), chunkResult);
    }

    public void testChunk_whenExceedMaxChunkLimit_thenFail() {
        int maxChunkLimit = 2;
        DelimiterChunker chunker = new DelimiterChunker(Map.of(DELIMITER_FIELD, "\n\n", MAX_CHUNK_LIMIT_FIELD, maxChunkLimit));
        String content = "\n\na\n\n\n";
        IllegalArgumentException illegalArgumentException = assertThrows(
            IllegalArgumentException.class,
            () -> chunker.chunk(content, Map.of())
        );
        assert (illegalArgumentException.getMessage()
            .contains(
                String.format(
                    Locale.ROOT,
                    "The number of chunks produced by %s processor has exceeded the allowed maximum of [%s].",
                    TYPE,
                    maxChunkLimit
                )
            ));
    }

    public void testChunk_whenWithinMaxChunkLimit_thenSucceed() {
        int maxChunkLimit = 3;
        DelimiterChunker chunker = new DelimiterChunker(Map.of(DELIMITER_FIELD, "\n\n", MAX_CHUNK_LIMIT_FIELD, maxChunkLimit));
        String content = "\n\na\n\n\n";
        List<String> chunkResult = chunker.chunk(content, Map.of());
        assertEquals(List.of("\n\n", "a\n\n", "\n"), chunkResult);
    }

    public void testChunk_whenExceedRuntimeMaxChunkLimit_thenFail() {
        int maxChunkLimit = 3;
        DelimiterChunker chunker = new DelimiterChunker(Map.of(DELIMITER_FIELD, "\n\n", MAX_CHUNK_LIMIT_FIELD, maxChunkLimit));
        String content = "\n\na\n\n\n";
        int runtimeMaxChunkLimit = 2;
        IllegalArgumentException illegalArgumentException = assertThrows(
            IllegalArgumentException.class,
            () -> chunker.chunk(content, Map.of(MAX_CHUNK_LIMIT_FIELD, runtimeMaxChunkLimit))
        );
        assert (illegalArgumentException.getMessage()
            .contains(
                String.format(
                    Locale.ROOT,
                    "The number of chunks produced by %s processor has exceeded the allowed maximum of [%s].",
                    TYPE,
                    maxChunkLimit
                )
            ));
    }
}
