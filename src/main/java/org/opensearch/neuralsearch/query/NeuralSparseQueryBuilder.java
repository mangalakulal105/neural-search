/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.neuralsearch.query;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.lucene.document.FeatureField;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.opensearch.Version;
import org.opensearch.client.Client;
import org.opensearch.common.SetOnce;
import org.opensearch.core.ParseField;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.ParsingException;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.mapper.MappedFieldType;
import org.opensearch.index.query.AbstractQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryRewriteContext;
import org.opensearch.index.query.QueryShardContext;
import org.opensearch.neuralsearch.ml.MLCommonsClientAccessor;
import org.opensearch.neuralsearch.util.NeuralSearchClusterUtil;
import org.opensearch.neuralsearch.util.TokenWeightUtil;

import com.google.common.annotations.VisibleForTesting;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import static org.opensearch.neuralsearch.processor.NeuralSparseTwoPhaseProcessor.DOWN_THRESHOLD;
import static org.opensearch.neuralsearch.processor.NeuralSparseTwoPhaseProcessor.UP_THRESHOLD;
import static org.opensearch.neuralsearch.processor.NeuralSparseTwoPhaseProcessor.splitQueryTokensByRatioedMaxScoreAsThreshold;

/**
 * SparseEncodingQueryBuilder is responsible for handling "neural_sparse" query types. It uses an ML NEURAL_SPARSE model
 * or SPARSE_TOKENIZE model to produce a Map with String keys and Float values for input text. Then it will be transformed
 * to Lucene FeatureQuery wrapped by Lucene BooleanQuery.
 */

@Getter
@Setter
@Accessors(chain = true, fluent = true)
@NoArgsConstructor
@AllArgsConstructor
public class NeuralSparseQueryBuilder extends AbstractQueryBuilder<NeuralSparseQueryBuilder> implements ModelInferenceQueryBuilder {
    public static final String NAME = "neural_sparse";
    @VisibleForTesting
    static final ParseField QUERY_TEXT_FIELD = new ParseField("query_text");
    @VisibleForTesting
    static final ParseField QUERY_TOKENS_FIELD = new ParseField("query_tokens");
    @VisibleForTesting
    static final ParseField MODEL_ID_FIELD = new ParseField("model_id");
    // We use max_token_score field to help WAND scorer prune query clause in lucene 9.7. But in lucene 9.8 the inner
    // logics change, this field is not needed any more.
    @VisibleForTesting
    @Deprecated
    static final ParseField MAX_TOKEN_SCORE_FIELD = new ParseField("max_token_score").withAllDeprecated();
    private static MLCommonsClientAccessor ML_CLIENT;
    private String fieldName;
    private String queryText;
    private String modelId;
    private Float maxTokenScore;
    private Map<String, Float> twoPhaseSharedQueryToken;
    private Supplier<Map<String, Float>> queryTokensSupplier;
    // A parameter that can detect if twoPhase are enabled, when twoPhasePruneRatio equals -1f, it means it two-phase rescoreQueryBuilder's
    // subQueryBuilder.
    private float twoPhasePruneRatio = 0F;

    private static final Version MINIMAL_SUPPORTED_VERSION_DEFAULT_MODEL_ID = Version.V_2_13_0;

    public static void initialize(MLCommonsClientAccessor mlClient) {
        NeuralSparseQueryBuilder.ML_CLIENT = mlClient;
    }

    /**
     * Constructor from stream input
     *
     * @param in StreamInput to initialize object from
     * @throws IOException thrown if unable to read from input stream
     */
    public NeuralSparseQueryBuilder(StreamInput in) throws IOException {
        super(in);
        this.fieldName = in.readString();
        this.queryText = in.readString();
        if (isClusterOnOrAfterMinReqVersionForDefaultModelIdSupport()) {
            this.modelId = in.readOptionalString();
        } else {
            this.modelId = in.readString();
        }
        this.maxTokenScore = in.readOptionalFloat();
        if (in.readBoolean()) {
            Map<String, Float> queryTokens = in.readMap(StreamInput::readString, StreamInput::readFloat);
            this.queryTokensSupplier = () -> queryTokens;
        }
        // to be backward compatible with previous version, we need to use writeString/readString API instead of optionalString API
        // after supporting query by tokens, queryText and modelId can be null. here we write an empty String instead
        if (StringUtils.EMPTY.equals(this.queryText)) {
            this.queryText = null;
        }
        if (StringUtils.EMPTY.equals(this.modelId)) {
            this.modelId = null;
        }
    }

    /**
     * Copy this QueryBuilder for two phase rescorer, set the copy one's twoPhasePruneRatio to -1.
     * @param ratio the parameter of the NeuralSparseTwoPhaseProcessor, control how to split the queryTokens to two phase.
     * @return A copy NeuralSparseQueryBuilder for twoPhase, it will be added to the rescorer.
     */
    public NeuralSparseQueryBuilder getCopyNeuralSparseQueryBuilderForTwoPhase(float ratio) {
        this.twoPhasePruneRatio(ratio);
        NeuralSparseQueryBuilder copy = new NeuralSparseQueryBuilder().fieldName(this.fieldName)
            .queryText(this.queryText)
            .modelId(this.modelId)
            .maxTokenScore(this.maxTokenScore)
            .twoPhasePruneRatio(-1f);
        if (this.queryTokensSupplier != null) {
            Map<String, Float> tokens = queryTokensSupplier.get();
            Map<String, SetOnce<Map<String, Float>>> splitTokens = splitQueryTokensByRatioedMaxScoreAsThreshold(tokens, ratio);
            this.queryTokensSupplier(splitTokens.get(UP_THRESHOLD)::get);
            copy.queryTokensSupplier(splitTokens.get(DOWN_THRESHOLD)::get);
        } else {
            this.twoPhaseSharedQueryToken = new HashMap<>();
            copy.queryTokensSupplier(() -> this.twoPhaseSharedQueryToken);
        }
        return copy;
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeString(this.fieldName);
        // to be backward compatible with previous version, we need to use writeString/readString API instead of optionalString API
        // after supporting query by tokens, queryText and modelId can be null. here we write an empty String instead
        out.writeString(StringUtils.defaultString(this.queryText, StringUtils.EMPTY));
        if (isClusterOnOrAfterMinReqVersionForDefaultModelIdSupport()) {
            out.writeOptionalString(this.modelId);
        } else {
            out.writeString(StringUtils.defaultString(this.modelId, StringUtils.EMPTY));
        }
        out.writeOptionalFloat(maxTokenScore);
        if (!Objects.isNull(this.queryTokensSupplier) && !Objects.isNull(this.queryTokensSupplier.get())) {
            out.writeBoolean(true);
            out.writeMap(this.queryTokensSupplier.get(), StreamOutput::writeString, StreamOutput::writeFloat);
        } else {
            out.writeBoolean(false);
        }
    }

    @Override
    protected void doXContent(XContentBuilder xContentBuilder, Params params) throws IOException {
        xContentBuilder.startObject(NAME);
        xContentBuilder.startObject(fieldName);
        if (Objects.nonNull(queryText)) {
            xContentBuilder.field(QUERY_TEXT_FIELD.getPreferredName(), queryText);
        }
        if (Objects.nonNull(modelId)) {
            xContentBuilder.field(MODEL_ID_FIELD.getPreferredName(), modelId);
        }
        if (Objects.nonNull(maxTokenScore)) xContentBuilder.field(MAX_TOKEN_SCORE_FIELD.getPreferredName(), maxTokenScore);
        if (Objects.nonNull(queryTokensSupplier) && Objects.nonNull(queryTokensSupplier.get())) {
            xContentBuilder.field(QUERY_TOKENS_FIELD.getPreferredName(), queryTokensSupplier.get());
        }
        printBoostAndQueryName(xContentBuilder);
        xContentBuilder.endObject();
        xContentBuilder.endObject();
    }

    /**
     * The expected parsing form looks like:
     *  "SAMPLE_FIELD": {
     *    "query_text": "string",
     *    "model_id": "string",
     *    "max_token_score": float (optional)
     *  }
     *
     *  or
     *  "SAMPLE_FIELD": {
     *      "query_tokens": {
     *          "token_a": float,
     *          "token_b": float,
     *          ...
     *       }
     *  }
     *
     *
     * @param parser XContentParser
     * @return NeuralQueryBuilder
     * @throws IOException can be thrown by parser
     */
    public static NeuralSparseQueryBuilder fromXContent(XContentParser parser) throws IOException {
        NeuralSparseQueryBuilder sparseEncodingQueryBuilder = new NeuralSparseQueryBuilder();
        if (parser.currentToken() != XContentParser.Token.START_OBJECT) {
            throw new ParsingException(parser.getTokenLocation(), "First token of " + NAME + "query must be START_OBJECT");
        }
        parser.nextToken();
        sparseEncodingQueryBuilder.fieldName(parser.currentName());
        parser.nextToken();
        parseQueryParams(parser, sparseEncodingQueryBuilder);
        if (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            throw new ParsingException(
                parser.getTokenLocation(),
                String.format(
                    Locale.ROOT,
                    "[%s] query doesn't support multiple fields, found [%s] and [%s]",
                    NAME,
                    sparseEncodingQueryBuilder.fieldName(),
                    parser.currentName()
                )
            );
        }

        requireValue(sparseEncodingQueryBuilder.fieldName(), "Field name must be provided for " + NAME + " query");
        if (Objects.isNull(sparseEncodingQueryBuilder.queryTokensSupplier())) {
            requireValue(
                sparseEncodingQueryBuilder.queryText(),
                String.format(
                    Locale.ROOT,
                    "either %s field or %s field must be provided for [%s] query",
                    QUERY_TEXT_FIELD.getPreferredName(),
                    QUERY_TOKENS_FIELD.getPreferredName(),
                    NAME
                )
            );
            if (!isClusterOnOrAfterMinReqVersionForDefaultModelIdSupport()) {
                requireValue(
                    sparseEncodingQueryBuilder.modelId(),
                    String.format(
                        Locale.ROOT,
                        "using %s, %s field must be provided for [%s] query",
                        QUERY_TEXT_FIELD.getPreferredName(),
                        MODEL_ID_FIELD.getPreferredName(),
                        NAME
                    )
                );
            }
        }

        if (StringUtils.EMPTY.equals(sparseEncodingQueryBuilder.queryText())) {
            throw new IllegalArgumentException(
                String.format(Locale.ROOT, "%s field can not be empty", QUERY_TEXT_FIELD.getPreferredName())
            );
        }
        if (StringUtils.EMPTY.equals(sparseEncodingQueryBuilder.modelId())) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "%s field can not be empty", MODEL_ID_FIELD.getPreferredName()));
        }

        return sparseEncodingQueryBuilder;
    }

    private static void parseQueryParams(XContentParser parser, NeuralSparseQueryBuilder sparseEncodingQueryBuilder) throws IOException {
        XContentParser.Token token;
        String currentFieldName = "";
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (token.isValue()) {
                if (NAME_FIELD.match(currentFieldName, parser.getDeprecationHandler())) {
                    sparseEncodingQueryBuilder.queryName(parser.text());
                } else if (BOOST_FIELD.match(currentFieldName, parser.getDeprecationHandler())) {
                    sparseEncodingQueryBuilder.boost(parser.floatValue());
                } else if (QUERY_TEXT_FIELD.match(currentFieldName, parser.getDeprecationHandler())) {
                    sparseEncodingQueryBuilder.queryText(parser.text());
                } else if (MODEL_ID_FIELD.match(currentFieldName, parser.getDeprecationHandler())) {
                    sparseEncodingQueryBuilder.modelId(parser.text());
                } else if (MAX_TOKEN_SCORE_FIELD.match(currentFieldName, parser.getDeprecationHandler())) {
                    sparseEncodingQueryBuilder.maxTokenScore(parser.floatValue());
                } else {
                    throw new ParsingException(
                        parser.getTokenLocation(),
                        String.format(Locale.ROOT, "[%s] query does not support [%s] field", NAME, currentFieldName)
                    );
                }
            } else if (QUERY_TOKENS_FIELD.match(currentFieldName, parser.getDeprecationHandler())) {
                Map<String, Float> queryTokens = parser.map(HashMap::new, XContentParser::floatValue);
                sparseEncodingQueryBuilder.queryTokensSupplier(() -> queryTokens);
            } else {
                throw new ParsingException(
                    parser.getTokenLocation(),
                    String.format(Locale.ROOT, "[%s] unknown token [%s] after [%s]", NAME, token, currentFieldName)
                );
            }
        }
    }

    @Override
    protected QueryBuilder doRewrite(QueryRewriteContext queryRewriteContext) {
        if (Objects.nonNull(queryTokensSupplier)) {
            return this;
        }
        validateForRewrite(queryText, modelId);
        SetOnce<Map<String, Float>> queryTokensSetOnce = new SetOnce<>();
        queryRewriteContext.registerAsyncAction(getModelInferenceAsync(queryTokensSetOnce));
        return new NeuralSparseQueryBuilder().fieldName(fieldName)
            .queryText(queryText)
            .queryName(queryName)
            .boost(boost)
            .modelId(modelId)
            .maxTokenScore(maxTokenScore)
            .queryTokensSupplier(queryTokensSetOnce::get)
            .twoPhaseSharedQueryToken(twoPhaseSharedQueryToken)
            .twoPhasePruneRatio(twoPhasePruneRatio);
    }

    private BiConsumer<Client, ActionListener<?>> getModelInferenceAsync(SetOnce<Map<String, Float>> setOnce) {
        return ((client, actionListener) -> ML_CLIENT.inferenceSentencesWithMapResult(
            modelId(),
            List.of(queryText),
            ActionListener.wrap(mapResultList -> {
                Map<String, Float> queryTokens = TokenWeightUtil.fetchListOfTokenWeightMap(mapResultList).get(0);
                if (twoPhaseSharedQueryToken != null) {
                    Map<String, SetOnce<Map<String, Float>>> splitSetOnce = splitQueryTokensByRatioedMaxScoreAsThreshold(
                        queryTokens,
                        twoPhasePruneRatio
                    );
                    setOnce.set(splitSetOnce.get(UP_THRESHOLD).get());
                    twoPhaseSharedQueryToken.putAll(splitSetOnce.get(DOWN_THRESHOLD).get());
                } else {
                    setOnce.set(queryTokens);
                }
                actionListener.onResponse(null);
            }, actionListener::onFailure)
        ));
    }

    @Override
    protected Query doToQuery(QueryShardContext context) throws IOException {
        final MappedFieldType ft = context.fieldMapper(fieldName);
        validateFieldType(ft);
        Map<String, Float> queryTokens = queryTokensSupplier.get();
        if (Objects.isNull(queryTokens)) {
            if (twoPhasePruneRatio == -1f) {
                return new MatchNoDocsQuery();
            }
            throw new IllegalArgumentException("Query tokens cannot be null.");
        }
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        for (Map.Entry<String, Float> entry : queryTokens.entrySet()) {
            builder.add(FeatureField.newLinearQuery(fieldName, entry.getKey(), entry.getValue()), BooleanClause.Occur.SHOULD);
        }
        return builder.build();
    }

    private static void validateForRewrite(String queryText, String modelId) {
        if (StringUtils.isBlank(queryText) || StringUtils.isBlank(modelId)) {
            throw new IllegalArgumentException(
                String.format(
                    Locale.ROOT,
                    "%s and %s cannot be null",
                    QUERY_TEXT_FIELD.getPreferredName(),
                    MODEL_ID_FIELD.getPreferredName()
                )
            );
        }
    }

    private static void validateFieldType(MappedFieldType fieldType) {
        if (null == fieldType || !fieldType.typeName().equals("rank_features")) {
            throw new IllegalArgumentException("[" + NAME + "] query only works on [rank_features] fields");
        }
    }

    @Override
    protected boolean doEquals(NeuralSparseQueryBuilder obj) {
        if (this == obj) return true;
        if (Objects.isNull(obj) || getClass() != obj.getClass()) return false;
        if (Objects.isNull(queryTokensSupplier) && Objects.nonNull(obj.queryTokensSupplier)) return false;
        if (Objects.nonNull(queryTokensSupplier) && Objects.isNull(obj.queryTokensSupplier)) return false;
        if (Objects.nonNull(twoPhaseSharedQueryToken) && Objects.isNull(obj.twoPhaseSharedQueryToken)) return false;
        if (Objects.isNull(twoPhaseSharedQueryToken) && Objects.nonNull(obj.twoPhaseSharedQueryToken)) return false;

        EqualsBuilder equalsBuilder = new EqualsBuilder().append(fieldName, obj.fieldName)
            .append(queryText, obj.queryText)
            .append(modelId, obj.modelId)
            .append(maxTokenScore, obj.maxTokenScore)
            .append(twoPhasePruneRatio, obj.twoPhasePruneRatio);
        if (Objects.nonNull(queryTokensSupplier)) {
            equalsBuilder.append(queryTokensSupplier.get(), obj.queryTokensSupplier.get());
        }
        if (Objects.nonNull(twoPhaseSharedQueryToken)) {
            equalsBuilder.append(twoPhaseSharedQueryToken, obj.twoPhaseSharedQueryToken);
        }
        return equalsBuilder.isEquals();
    }

    @Override
    protected int doHashCode() {
        HashCodeBuilder builder = new HashCodeBuilder().append(fieldName)
            .append(queryText)
            .append(modelId)
            .append(maxTokenScore)
            .append(twoPhasePruneRatio);
        if (queryTokensSupplier != null) {
            builder.append(queryTokensSupplier.get());
        }
        if (twoPhaseSharedQueryToken != null) {
            builder.append(twoPhaseSharedQueryToken);
        }
        return builder.toHashCode();
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    private static boolean isClusterOnOrAfterMinReqVersionForDefaultModelIdSupport() {
        return NeuralSearchClusterUtil.instance().getClusterMinVersion().onOrAfter(MINIMAL_SUPPORTED_VERSION_DEFAULT_MODEL_ID);
    }

}
