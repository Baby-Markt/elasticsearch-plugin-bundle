package org.xbib.elasticsearch.index.mapper.langdetect;

import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexOptions;
import org.elasticsearch.common.lucene.Lucene;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.support.XContentMapValues;
import org.elasticsearch.index.analysis.NamedAnalyzer;
import org.elasticsearch.index.mapper.FieldMapper;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.index.mapper.Mapper;
import org.elasticsearch.index.mapper.MapperParsingException;
import org.elasticsearch.index.mapper.ParseContext;
import org.elasticsearch.index.mapper.TextFieldMapper;
import org.xbib.elasticsearch.common.langdetect.LangdetectService;
import org.xbib.elasticsearch.common.langdetect.Language;
import org.xbib.elasticsearch.common.langdetect.LanguageDetectionException;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.index.mapper.TypeParsers.parseStore;

/**
 *
 */
public class LangdetectMapper extends TextFieldMapper {

    public static final String CONTENT_TYPE = "langdetect";

    private final LangdetectService langdetectService;

    private final int positionIncrementGap;

    public LangdetectMapper(String simpleName,
                            TextFieldType fieldType,
                            MappedFieldType defaultFieldType,
                            int positionIncrementGap,
                            Settings indexSettings,
                            MultiFields multiFields,
                            CopyTo copyTo,
                            LangdetectService langdetectService) {
        super(simpleName, fieldType, defaultFieldType,
                positionIncrementGap, false, indexSettings, multiFields, copyTo);
        this.langdetectService = langdetectService;
        this.positionIncrementGap = positionIncrementGap;
    }

    @Override
    protected String contentType() {
        return CONTENT_TYPE;
    }

    @Override
    protected void parseCreateField(ParseContext context, List<Field> fields) throws IOException {
        if (context.externalValueSet()) {
            return;
        }
        XContentParser parser = context.parser();
        if (parser.currentToken() == XContentParser.Token.VALUE_NULL) {
            return;
        }
        String value = fieldType().nullValueAsString();
        if (parser.currentToken() == XContentParser.Token.START_OBJECT) {
            XContentParser.Token token;
            String currentFieldName = null;
            while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
                if (token == XContentParser.Token.FIELD_NAME) {
                    currentFieldName = parser.currentName();
                } else {
                    if ("value".equals(currentFieldName) || "_value".equals(currentFieldName)) {
                        value = parser.textOrNull();
                    }
                }
            }
        } else {
            value = parser.textOrNull();
        }
        if (langdetectService.getSettings().getAsBoolean("binary", false)) {
            try {
                byte[] b = parser.binaryValue();
                if (b != null && b.length > 0) {
                    value = new String(b, Charset.forName("UTF-8"));
                }
            } catch (Exception e) {
                // ignore
            }
        }
        try {
            List<Language> langs = langdetectService.detectAll(value);
            for (Language lang : langs) {
                Field field = new Field(fieldType().name(), lang.getLanguage(), fieldType());
                fields.add(field);
            }
        } catch (LanguageDetectionException e) {
            context.createExternalValueContext("unknown");
        }
    }

    @Override
    protected void doXContentBody(XContentBuilder builder, boolean includeDefaults, Params params) throws IOException {
        super.doXContentBody(builder, includeDefaults, params);
        if (includeDefaults || fieldType().nullValue() != null) {
            builder.field("null_value", fieldType().nullValue());
        }
        if (includeDefaults || positionIncrementGap != -1) {
            builder.field("position_increment_gap", positionIncrementGap);
        }
        NamedAnalyzer searchQuoteAnalyzer = fieldType().searchQuoteAnalyzer();
        if (searchQuoteAnalyzer != null && !searchQuoteAnalyzer.name().equals(fieldType().searchAnalyzer().name())) {
            builder.field("search_quote_analyzer", searchQuoteAnalyzer.name());
        } else if (includeDefaults) {
            if (searchQuoteAnalyzer == null) {
                builder.field("search_quote_analyzer", "default");
            } else {
                builder.field("search_quote_analyzer", searchQuoteAnalyzer.name());
            }
        }
        Map<String, Object> map = langdetectService.getSettings().getAsStructuredMap();
        for (String key : map.keySet()) {
            builder.field(key, map.get(key));
        }
    }

    public static class Defaults {

        public static final MappedFieldType LANG_FIELD_TYPE = new TextFieldType();

        static {
            LANG_FIELD_TYPE.setStored(true);
            LANG_FIELD_TYPE.setOmitNorms(true);
            LANG_FIELD_TYPE.setIndexAnalyzer(Lucene.KEYWORD_ANALYZER);
            LANG_FIELD_TYPE.setSearchAnalyzer(Lucene.KEYWORD_ANALYZER);
            LANG_FIELD_TYPE.setName(CONTENT_TYPE);
            LANG_FIELD_TYPE.freeze();
        }
    }

    public static class Builder extends FieldMapper.Builder<Builder, TextFieldMapper> {

        protected int positionIncrementGap = -1;

        protected Settings.Builder settingsBuilder = Settings.builder();

        public Builder(String name) {
            super(name, Defaults.LANG_FIELD_TYPE, Defaults.LANG_FIELD_TYPE);
            this.builder = this;
        }

        @Override
        public Builder searchAnalyzer(NamedAnalyzer searchAnalyzer) {
            super.searchAnalyzer(searchAnalyzer);
            return this;
        }

        public Builder positionIncrementGap(int positionIncrementGap) {
            this.positionIncrementGap = positionIncrementGap;
            return this;
        }

        public Builder searchQuotedAnalyzer(NamedAnalyzer analyzer) {
            this.fieldType.setSearchQuoteAnalyzer(analyzer);
            return builder;
        }

        public Builder ntrials(int trials) {
            settingsBuilder.put("number_of_trials", trials);
            return this;
        }

        public Builder alpha(double alpha) {
            settingsBuilder.put("alpha", alpha);
            return this;
        }

        public Builder alphaWidth(double alphaWidth) {
            settingsBuilder.put("alpha_width", alphaWidth);
            return this;
        }

        public Builder iterationLimit(int iterationLimit) {
            settingsBuilder.put("iteration_limit", iterationLimit);
            return this;
        }

        public Builder probThreshold(double probThreshold) {
            settingsBuilder.put("prob_threshold", probThreshold);
            return this;
        }

        public Builder convThreshold(double convThreshold) {
            settingsBuilder.put("conv_threshold", convThreshold);
            return this;
        }

        public Builder baseFreq(int baseFreq) {
            settingsBuilder.put("base_freq", baseFreq);
            return this;
        }

        public Builder pattern(String pattern) {
            settingsBuilder.put("pattern", pattern);
            return this;
        }

        public Builder max(int max) {
            settingsBuilder.put("max", max);
            return this;
        }

        public Builder binary(boolean binary) {
            settingsBuilder.put("binary", binary);
            return this;
        }

        public Builder map(Map<String, Object> map) {
            for (String key : map.keySet()) {
                settingsBuilder.put("map." + key, map.get(key));
            }
            return this;
        }

        public Builder languages(String[] languages) {
            settingsBuilder.putArray("languages", languages);
            return this;
        }

        public Builder profile(String profile) {
            settingsBuilder.put("profile", profile);
            return this;
        }

        @Override
        public LangdetectMapper build(BuilderContext context) {
            if (positionIncrementGap != -1) {
                fieldType.setIndexAnalyzer(new NamedAnalyzer(fieldType.indexAnalyzer(), positionIncrementGap));
                fieldType.setSearchAnalyzer(new NamedAnalyzer(fieldType.searchAnalyzer(), positionIncrementGap));
                fieldType.setSearchQuoteAnalyzer(new NamedAnalyzer(fieldType.searchQuoteAnalyzer(), positionIncrementGap));
            }
            if (fieldType.indexOptions() != IndexOptions.NONE && !fieldType.tokenized()) {
                defaultFieldType.setOmitNorms(true);
                defaultFieldType.setIndexOptions(IndexOptions.DOCS);
                if (!omitNormsSet && fieldType.boost() == 1.0f) {
                    fieldType.setOmitNorms(true);
                }
                if (!indexOptionsSet) {
                    fieldType.setIndexOptions(IndexOptions.DOCS);
                }
            }
            setupFieldType(context);
            LangdetectService service = new LangdetectService(settingsBuilder.build());
            return new LangdetectMapper(name,
                    (TextFieldType) fieldType(),
                    defaultFieldType,
                    positionIncrementGap,
                    context.indexSettings(),
                    multiFieldsBuilder.build(this, context),
                    copyTo,
                    service);
        }
    }

    public static class TypeParser implements Mapper.TypeParser {

        @Override
        public Mapper.Builder<?, ?> parse(String name, Map<String, Object> mapping, ParserContext parserContext)
                throws MapperParsingException {
            Builder builder = new Builder(name);
            Iterator<Map.Entry<String, Object>> iterator = mapping.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Object> entry = iterator.next();
                String fieldName = entry.getKey();
                Object fieldNode = entry.getValue();
                switch (fieldName) {
                    case "analyzer": {
                        iterator.remove();
                        break;
                    }
                    case "include_in_all": {
                        iterator.remove();
                        break;
                    }
                    case "search_quote_analyzer": {
                        NamedAnalyzer analyzer = parserContext.getIndexAnalyzers().get(fieldNode.toString());
                        if (analyzer == null) {
                            throw new MapperParsingException("Analyzer [" + fieldNode.toString() + "] not found for field [" + name + "]");
                        }
                        builder.searchQuotedAnalyzer(analyzer);
                        iterator.remove();
                        break;
                    }
                    case "position_increment_gap": {
                        int newPositionIncrementGap = XContentMapValues.nodeIntegerValue(fieldNode, -1);
                        if (newPositionIncrementGap < 0) {
                            throw new MapperParsingException("position_increment_gap less than 0 aren't allowed.");
                        }
                        builder.positionIncrementGap(newPositionIncrementGap);
                        if (builder.fieldType().indexAnalyzer() == null) {
                            builder.fieldType().setIndexAnalyzer(parserContext.getIndexAnalyzers().getDefaultIndexAnalyzer());
                        }
                        if (builder.fieldType().searchAnalyzer() == null) {
                            builder.fieldType().setSearchAnalyzer(parserContext.getIndexAnalyzers().getDefaultSearchAnalyzer());
                        }
                        if (builder.fieldType().searchQuoteAnalyzer() == null) {
                            builder.fieldType().setSearchQuoteAnalyzer(parserContext.getIndexAnalyzers().getDefaultSearchQuoteAnalyzer());
                        }
                        iterator.remove();
                        break;
                    }
                    case "store": {
                        builder.store(parseStore(fieldName, fieldNode.toString(), parserContext));
                        iterator.remove();
                        break;
                    }
                    case "number_of_trials": {
                        builder.ntrials(XContentMapValues.nodeIntegerValue(fieldNode));
                        iterator.remove();
                        break;
                    }
                    case "alpha": {
                        builder.alpha(XContentMapValues.nodeDoubleValue(fieldNode));
                        iterator.remove();
                        break;
                    }
                    case "alpha_width": {
                        builder.alphaWidth(XContentMapValues.nodeDoubleValue(fieldNode));
                        iterator.remove();
                        break;
                    }
                    case "iteration_limit": {
                        builder.iterationLimit(XContentMapValues.nodeIntegerValue(fieldNode));
                        iterator.remove();
                        break;
                    }
                    case "prob_threshold": {
                        builder.probThreshold(XContentMapValues.nodeDoubleValue(fieldNode));
                        iterator.remove();
                        break;
                    }
                    case "conv_threshold": {
                        builder.convThreshold(XContentMapValues.nodeDoubleValue(fieldNode));
                        iterator.remove();
                        break;
                    }
                    case "base_freq": {
                        builder.baseFreq(XContentMapValues.nodeIntegerValue(fieldNode));
                        iterator.remove();
                        break;
                    }
                    case "pattern": {
                        builder.pattern(XContentMapValues.nodeStringValue(fieldNode, null));
                        iterator.remove();
                        break;
                    }
                    case "max": {
                        builder.max(XContentMapValues.nodeIntegerValue(fieldNode));
                        iterator.remove();
                        break;
                    }
                    case "binary": {
                        boolean b = XContentMapValues.nodeBooleanValue(fieldNode);
                        builder.binary(b);
                        iterator.remove();
                        break;
                    }
                    case "map": {
                        builder.map(XContentMapValues.nodeMapValue(fieldNode, "map"));
                        iterator.remove();
                        break;
                    }
                    case "languages": {
                        builder.languages(XContentMapValues.nodeStringArrayValue(fieldNode));
                        iterator.remove();
                        break;
                    }
                    case "profile": {
                        builder.profile(XContentMapValues.nodeStringValue(fieldNode, null));
                        iterator.remove();
                        break;
                    }
                }
            }
            return builder;
        }
    }
}
