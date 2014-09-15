
package org.xbib.elasticsearch.index.analysis.langdetect;

import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.inject.Injector;
import org.elasticsearch.common.inject.ModulesBuilder;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsModule;
import org.elasticsearch.env.Environment;
import org.elasticsearch.env.EnvironmentModule;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.IndexNameModule;
import org.elasticsearch.index.analysis.AnalysisModule;
import org.elasticsearch.index.analysis.AnalysisService;
import org.elasticsearch.index.codec.docvaluesformat.DocValuesFormatService;
import org.elasticsearch.index.codec.postingsformat.PostingsFormatService;
import org.elasticsearch.index.mapper.DocumentMapper;
import org.elasticsearch.index.mapper.DocumentMapperParser;
import org.elasticsearch.index.mapper.ParseContext;
import org.elasticsearch.index.settings.IndexSettingsModule;
import org.elasticsearch.index.similarity.SimilarityLookupService;
import org.elasticsearch.indices.analysis.IndicesAnalysisModule;
import org.elasticsearch.indices.analysis.IndicesAnalysisService;
import org.junit.Assert;
import org.junit.Test;
import org.xbib.elasticsearch.plugin.analysis.german.AnalysisGermanPlugin;

import java.io.IOException;

import static org.elasticsearch.common.io.Streams.copyToStringFromClasspath;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

public class LangdetectMappingTests extends Assert {

    @Test
    public void testSimpleMappings() throws Exception {
        String mapping = copyToStringFromClasspath("/org/xbib/elasticsearch/index/analysis/langdetect/simple-mapping.json");
        DocumentMapper docMapper = createMapperParser().parse(mapping);
        String sampleText = copyToStringFromClasspath("/org/xbib/elasticsearch/index/analysis/langdetect/english.txt");
        BytesReference json = jsonBuilder().startObject().field("_id", 1).field("someField", sampleText).endObject().bytes();
        ParseContext.Document doc = docMapper.parse(json).rootDoc();
        assertEquals(doc.get(docMapper.mappers().smartName("someField").mapper().names().indexName()), sampleText);
        assertEquals(doc.getFields("someField.lang").length, 1);
        assertEquals(doc.getFields("someField.lang")[0].stringValue(), "eng");

        // re-parse it
        String builtMapping = docMapper.mappingSource().string();
        docMapper = createMapperParser().parse(builtMapping);
        json = jsonBuilder().startObject().field("_id", 1).field("someField", sampleText).endObject().bytes();
        doc = docMapper.parse(json).rootDoc();
        assertEquals(doc.get(docMapper.mappers().smartName("someField").mapper().names().indexName()), sampleText);
        assertEquals(doc.getFields("someField.lang").length, 1);
        assertEquals(doc.getFields("someField.lang")[0].stringValue(), "eng");
    }

    @Test
    public void testBinary() throws Exception {
        Settings settings = ImmutableSettings.EMPTY;
        String mapping = copyToStringFromClasspath("/org/xbib/elasticsearch/index/analysis/langdetect/base64-mapping.json");
        DocumentMapper docMapper = createMapperParser(settings).parse(mapping);
        String sampleBinary = copyToStringFromClasspath("/org/xbib/elasticsearch/index/analysis/langdetect/base64.txt");
        String sampleText = copyToStringFromClasspath("/org/xbib/elasticsearch/index/analysis/langdetect/base64-decoded.txt");
        BytesReference json = jsonBuilder().startObject().field("_id", 1).field("someField", sampleBinary).endObject().bytes();
        ParseContext.Document doc = docMapper.parse(json).rootDoc();
        assertEquals(doc.get(docMapper.mappers().smartName("someField").mapper().names().indexName()), sampleText);
        assertEquals(doc.getFields("someField.lang").length, 1);
        assertEquals(doc.getFields("someField.lang")[0].stringValue(), "eng");

        // re-parse it
        String builtMapping = docMapper.mappingSource().string();
        docMapper = createMapperParser(settings).parse(builtMapping);
        json = jsonBuilder().startObject().field("_id", 1).field("someField", sampleText).endObject().bytes();
        doc = docMapper.parse(json).rootDoc();
        assertEquals(doc.get(docMapper.mappers().smartName("someField").mapper().names().indexName()), sampleText);
        assertEquals(doc.getFields("someField.lang").length, 1);
        assertEquals(doc.getFields("someField.lang")[0].stringValue(), "eng");
    }

    @Test
    public void testMappings() throws Exception {
        Settings settings = ImmutableSettings.settingsBuilder()
                .loadFromClasspath("org/xbib/elasticsearch/index/analysis/langdetect/settings.json").build();
        String mapping = copyToStringFromClasspath("/org/xbib/elasticsearch/index/analysis/langdetect/mapping.json");
        DocumentMapper docMapper = createMapperParser(settings).parse(mapping);
        String sampleText = copyToStringFromClasspath("/org/xbib/elasticsearch/index/analysis/langdetect/german.txt");
        BytesReference json = jsonBuilder().startObject().field("_id", 1).field("someField", sampleText).endObject().bytes();
        ParseContext.Document doc = docMapper.parse(json).rootDoc();
        assertEquals(doc.get(docMapper.mappers().smartName("someField").mapper().names().indexName()), sampleText);
        assertEquals(doc.getFields("someField.lang").length, 1);
        assertEquals(doc.getFields("someField.lang")[0].stringValue(), "Deutsch");
    }

    private DocumentMapperParser createMapperParser() throws IOException {
        return createMapperParser(ImmutableSettings.EMPTY);
    }

    private DocumentMapperParser createMapperParser(Settings settings) throws IOException {
        Index index = new Index("test");
        Injector parentInjector = new ModulesBuilder().add(new SettingsModule(settings),
                new EnvironmentModule(new Environment(settings)),
                new IndicesAnalysisModule())
                .createInjector();
        AnalysisModule analysisModule = new AnalysisModule(settings, parentInjector.getInstance(IndicesAnalysisService.class));
        new AnalysisGermanPlugin().onModule(analysisModule);
        Injector injector = new ModulesBuilder().add(
                new IndexSettingsModule(index, settings),
                new IndexNameModule(index),
                analysisModule)
                .createChildInjector(parentInjector);
        AnalysisService service =injector.getInstance(AnalysisService.class);
        DocumentMapperParser mapperParser = new DocumentMapperParser(index,
                settings,
                service,
                new PostingsFormatService(index),
                new DocValuesFormatService(index),
                new SimilarityLookupService(index, settings),
                null
        );
        mapperParser.putTypeParser(LangdetectMapper.CONTENT_TYPE, new LangdetectMapper.TypeParser());
        return mapperParser;
    }



}
