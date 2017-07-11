/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.upgrade;

import org.elasticsearch.Version;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.UUIDs;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.test.ESTestCase;

import java.io.IOException;

import static org.hamcrest.core.IsEqual.equalTo;

public class IndexUpgradeCheckTests extends ESTestCase {

    public void testWatcherIndexUpgradeCheck() throws Exception {
        IndexUpgradeCheck check = Upgrade.getWatcherUpgradeCheckFactory(Settings.EMPTY).apply(null, null);
        assertThat(check.getName(), equalTo("watcher"));

        IndexMetaData goodKibanaIndex = newTestIndexMeta(".kibana", Settings.EMPTY);
        assertThat(check.actionRequired(goodKibanaIndex), equalTo(UpgradeActionRequired.NOT_APPLICABLE));

        IndexMetaData watcherIndex = newTestIndexMeta(".watches", Settings.EMPTY);
        assertThat(check.actionRequired(watcherIndex), equalTo(UpgradeActionRequired.UPGRADE));

        IndexMetaData watcherIndexWithAlias = newTestIndexMeta("my_watches", ".watches", Settings.EMPTY, "watch");
        assertThat(check.actionRequired(watcherIndexWithAlias), equalTo(UpgradeActionRequired.UPGRADE));

        IndexMetaData watcherIndexWithAliasUpgraded = newTestIndexMeta("my_watches", ".watches", Settings.EMPTY, "doc");
        assertThat(check.actionRequired(watcherIndexWithAliasUpgraded), equalTo(UpgradeActionRequired.UP_TO_DATE));
    }

    public static IndexMetaData newTestIndexMeta(String name, String alias, Settings indexSettings, String type) throws IOException {
        Settings build = Settings.builder().put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT)
                .put(IndexMetaData.SETTING_NUMBER_OF_REPLICAS, 1)
                .put(IndexMetaData.SETTING_NUMBER_OF_SHARDS, 1)
                .put(IndexMetaData.SETTING_CREATION_DATE, 1)
                .put(IndexMetaData.SETTING_INDEX_UUID, UUIDs.randomBase64UUID())
                .put(IndexMetaData.SETTING_VERSION_UPGRADED, Version.V_5_0_0_beta1)
                .put(indexSettings)
                .build();
        IndexMetaData.Builder builder = IndexMetaData.builder(name).settings(build);
        if (alias != null) {
            // Create alias
            builder.putAlias(AliasMetaData.newAliasMetaDataBuilder(alias).build());
        }
        if (type != null) {
            // Create fake type
            builder.putMapping(type, "{}");
        }
        return builder.build();
    }

    public static IndexMetaData newTestIndexMeta(String name, Settings indexSettings) throws IOException {
        return newTestIndexMeta(name, null, indexSettings, "foo");
    }

}
