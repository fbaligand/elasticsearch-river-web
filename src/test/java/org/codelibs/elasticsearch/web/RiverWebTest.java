package org.codelibs.elasticsearch.web;

import static org.codelibs.elasticsearch.runner.ElasticsearchClusterRunner.newConfigs;

import java.util.UUID;
import java.util.function.IntConsumer;

import junit.framework.TestCase;

import org.codelibs.elasticsearch.runner.ElasticsearchClusterRunner;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.common.settings.ImmutableSettings.Builder;

public class RiverWebTest extends TestCase {

    private ElasticsearchClusterRunner runner;

    private String clusterName;

    @Override
    protected void setUp() throws Exception {
        // create runner instance
        runner = new ElasticsearchClusterRunner();
        clusterName = "es-river-web-" + UUID.randomUUID().toString();
        runner.onBuild(new ElasticsearchClusterRunner.Builder() {
            @Override
            public void build(final int number, final Builder settingsBuilder) {
                settingsBuilder.put("http.cors.enabled", true);
            }
        }).build(newConfigs().clusterName(clusterName).ramIndexStore().numOfNode(3));

        // wait for yellow status
        runner.ensureYellow();
    }

    @Override
    protected void tearDown() throws Exception {
        // close runner
        runner.close();
        // delete all files
        runner.clean();
    }

    public void test_runCluster() throws Exception {

        RiverWeb.exitMethod = new IntConsumer() {
            @Override
            public void accept(final int value) {
                if (value != 0) {
                    fail();
                }
            }
        };

        final String index = "webindex";
        final String type = "my_web";
        final String riverWebIndex = ".river_web";
        final String riverWebType = "config";

        // create an index
        runner.createIndex(index, null);
        runner.ensureYellow(index);

        // create a mapping
        final String mappingSource =
                "{\"my_web\":{\"dynamic_templates\":[{\"url\":{\"match\":\"url\",\"mapping\":{\"type\":\"string\",\"store\":\"yes\",\"index\":\"not_analyzed\"}}},{\"method\":{\"match\":\"method\",\"mapping\":{\"type\":\"string\",\"store\":\"yes\",\"index\":\"not_analyzed\"}}},{\"charSet\":{\"match\":\"charSet\",\"mapping\":{\"type\":\"string\",\"store\":\"yes\",\"index\":\"not_analyzed\"}}},{\"mimeType\":{\"match\":\"mimeType\",\"mapping\":{\"type\":\"string\",\"store\":\"yes\",\"index\":\"not_analyzed\"}}}]}}";
        runner.createMapping(index, type, mappingSource);

        if (!runner.indexExists(index)) {
            fail();
        }

        {
            final String riverWebSource =
                    "{\"index\":\"webindex\",\"url\":[\"http://www.codelibs.org/\",\"http://fess.codelibs.org/\"],\"includeFilter\":[\"http://www.codelibs.org/.*\",\"http://fess.codelibs.org/.*\"],\"maxDepth\":3,\"maxAccessCount\":100,\"numOfThread\":5,\"interval\":1000,\"target\":[{\"pattern\":{\"url\":\"http://www.codelibs.org/.*\",\"mimeType\":\"text/html\"},\"properties\":{\"title\":{\"text\":\"title\"},\"body\":{\"text\":\"body\"},\"bodyAsHtml\":{\"html\":\"body\"},\"projects\":{\"text\":\"ul.nav-listlia\",\"isArray\":true}}},{\"pattern\":{\"url\":\"http://fess.codelibs.org/.*\",\"mimeType\":\"text/html\"},\"properties\":{\"title\":{\"text\":\"title\"},\"body\":{\"text\":\"body\",\"trimSpaces\":true},\"menus\":{\"text\":\"ul.nav-listlia\",\"isArray\":true}}}]}";
            final IndexResponse response = runner.insert(riverWebIndex, riverWebType, "1", riverWebSource);
            if (!response.isCreated()) {
                fail();
            }
        }

        RiverWeb.main(new String[] { "--config-id", "1", "--es-port", runner.node().settings().get("transport.tcp.port"), "--cluster-name",
                clusterName, "--cleanup" });

        assertEquals(0, runner.count(index, type).getCount());

        runner.ensureYellow();
    }
}
