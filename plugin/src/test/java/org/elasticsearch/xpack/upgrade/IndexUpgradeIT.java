/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.upgrade;

import org.elasticsearch.ElasticsearchSecurityException;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.PlainActionFuture;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.transport.TransportResponse;
import org.elasticsearch.xpack.upgrade.actions.IndexUpgradeAction;
import org.elasticsearch.xpack.upgrade.actions.IndexUpgradeInfoAction;
import org.elasticsearch.xpack.upgrade.actions.IndexUpgradeInfoAction.Response;
import org.junit.Before;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.core.IsEqual.equalTo;

public class IndexUpgradeIT extends IndexUpgradeIntegTestCase {

    @Before
    public void resetLicensing() throws Exception {
        enableLicensing();
    }

    public void testIndexUpgradeInfo() {
        // Testing only negative case here, the positive test is done in bwcTests
        assertAcked(client().admin().indices().prepareCreate("test").get());
        ensureYellow("test");
        Response response = client().prepareExecute(IndexUpgradeInfoAction.INSTANCE).setIndices("test").get();
        assertThat(response.getActions().entrySet(), empty());
    }

    public void testIndexUpgradeInfoLicense() throws Exception {
        assertAcked(client().admin().indices().prepareCreate("test").get());
        ensureYellow("test");
        disableLicensing();
        ElasticsearchSecurityException e = expectThrows(ElasticsearchSecurityException.class,
                () -> client().prepareExecute(IndexUpgradeInfoAction.INSTANCE).setIndices("test").get());
        assertThat(e.getMessage(), equalTo("current license is non-compliant for [upgrade]"));
        enableLicensing();
        Response response = client().prepareExecute(IndexUpgradeInfoAction.INSTANCE).setIndices("test").get();
        assertThat(response.getActions().entrySet(), empty());
    }

    public void testUpToDateIndexUpgrade() throws Exception {
        // Testing only negative case here, the positive test is done in bwcTests
        String testIndex = "test";
        String testType = "doc";
        assertAcked(client().admin().indices().prepareCreate(testIndex).get());
        indexRandom(true,
                client().prepareIndex(testIndex, testType, "1").setSource("{\"foo\":\"bar\"}", XContentType.JSON),
                client().prepareIndex(testIndex, testType, "2").setSource("{\"foo\":\"baz\"}", XContentType.JSON)
        );
        ensureYellow(testIndex);

        IllegalStateException ex = expectThrows(IllegalStateException.class,
                () -> client().prepareExecute(IndexUpgradeAction.INSTANCE).setIndex(testIndex).get());
        assertThat(ex.getMessage(), equalTo("Index [" + testIndex + "] cannot be upgraded"));

        SearchResponse searchResponse = client().prepareSearch(testIndex).get();
        assertEquals(2L, searchResponse.getHits().getTotalHits());
    }

    public void testInternalUpgradePrePostChecks() throws Exception {
        String testIndex = "internal_index";
        String testType = "test";
        Long val = randomLong();
        AtomicBoolean preUpgradeIsCalled = new AtomicBoolean();
        AtomicBoolean postUpgradeIsCalled = new AtomicBoolean();

        IndexUpgradeCheck check = new IndexUpgradeCheck<Long>(
                "test", Settings.EMPTY,
                indexMetaData -> {
                    if (indexMetaData.getIndex().getName().equals(testIndex)) {
                        return UpgradeActionRequired.UPGRADE;
                    } else {
                        return UpgradeActionRequired.NOT_APPLICABLE;
                    }
                },
                client(), internalCluster().clusterService(internalCluster().getMasterName()), Strings.EMPTY_ARRAY, null,
                listener -> {
                    assertFalse(preUpgradeIsCalled.getAndSet(true));
                    assertFalse(postUpgradeIsCalled.get());
                    listener.onResponse(val);
                },
                (aLong, listener) -> {
                    assertTrue(preUpgradeIsCalled.get());
                    assertFalse(postUpgradeIsCalled.getAndSet(true));
                    assertEquals(aLong, val);
                    listener.onResponse(TransportResponse.Empty.INSTANCE);
                });

        assertAcked(client().admin().indices().prepareCreate(testIndex).get());
        indexRandom(true,
                client().prepareIndex(testIndex, testType, "1").setSource("{\"foo\":\"bar\"}", XContentType.JSON),
                client().prepareIndex(testIndex, testType, "2").setSource("{\"foo\":\"baz\"}", XContentType.JSON)
        );
        ensureYellow(testIndex);

        IndexUpgradeService service = new IndexUpgradeService(Settings.EMPTY, Collections.singletonList(check));

        PlainActionFuture<BulkByScrollResponse> future = PlainActionFuture.newFuture();
        service.upgrade(testIndex, clusterService().state(), future);
        BulkByScrollResponse response = future.actionGet();
        assertThat(response.getCreated(), equalTo(2L));

        SearchResponse searchResponse = client().prepareSearch(testIndex).get();
        assertEquals(2L, searchResponse.getHits().getTotalHits());

        assertTrue(preUpgradeIsCalled.get());
        assertTrue(postUpgradeIsCalled.get());
    }

}
