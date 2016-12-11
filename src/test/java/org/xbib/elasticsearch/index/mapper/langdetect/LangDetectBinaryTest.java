package org.xbib.elasticsearch.index.mapper.langdetect;

import org.elasticsearch.action.admin.indices.create.CreateIndexAction;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.index.IndexAction;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchAction;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.index.query.QueryBuilders;
import org.junit.Test;
import org.xbib.elasticsearch.NodeTestUtils;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class LangDetectBinaryTest extends NodeTestUtils {

    @Test
    public void testLangDetectBinary() throws Exception {
        CreateIndexRequestBuilder createIndexRequestBuilder =
                new CreateIndexRequestBuilder(client(), CreateIndexAction.INSTANCE).setIndex("test");
        createIndexRequestBuilder.addMapping("someType", "{\n" +
                "    \"properties\": {\n" +
                "      \"content\": {\n" +
                "        \"type\": \"multi_field\",\n" +
                "        \"fields\": {\n" +
                "          \"content\": {\n" +
                "            \"type\": \"string\"\n" +
                "          },\n" +
                "          \"language\": {\n" +
                "            \"type\": \"langdetect\",\n" +
                "            \"binary\": true\n" +
                "          }\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "}");
        createIndexRequestBuilder.execute().actionGet();
        IndexRequestBuilder indexRequestBuilder =
                new IndexRequestBuilder(client(), IndexAction.INSTANCE)
                        .setIndex("test").setType("someType").setId("1")
                        //\"God Save the Queen\" (alternatively \"God Save the King\"
                        .setSource("content", "IkdvZCBTYXZlIHRoZSBRdWVlbiIgKGFsdGVybmF0aXZlbHkgIkdvZCBTYXZlIHRoZSBLaW5nIg==");
        indexRequestBuilder.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE).execute().actionGet();
        SearchRequestBuilder searchRequestBuilder =
                new SearchRequestBuilder(client(), SearchAction.INSTANCE)
                        .setIndices("test")
                        .setQuery(QueryBuilders.matchAllQuery())
                        .addStoredField("content.language");
        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        assertEquals(1L, searchResponse.getHits().getTotalHits());
        assertEquals("en", searchResponse.getHits().getAt(0).field("content.language").getValue());
    }
}
