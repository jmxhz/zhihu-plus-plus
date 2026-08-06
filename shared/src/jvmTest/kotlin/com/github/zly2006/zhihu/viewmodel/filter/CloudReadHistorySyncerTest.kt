/*
 * Zhihu++ - Free & Ad-Free Zhihu client for Android.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.zly2006.zhihu.viewmodel.filter

import com.github.zly2006.zhihu.data.OnlineHistoryAction
import com.github.zly2006.zhihu.data.OnlineHistoryData
import com.github.zly2006.zhihu.data.OnlineHistoryExtra
import com.github.zly2006.zhihu.data.OnlineHistoryHeader
import com.github.zly2006.zhihu.data.OnlineHistoryItem
import com.github.zly2006.zhihu.data.OnlineHistoryPage
import com.github.zly2006.zhihu.data.ZhihuPaging
import com.github.zly2006.zhihu.filter.ContentOpenEventSupport
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CloudReadHistorySyncerTest {
    @Test
    fun syncCachesAllPages() = runTest {
        val database = database()
        val secondUrl = "https://api.zhihu.com/unify-consumption/read_history?offset=50&limit=50"
        val syncer = CloudReadHistorySyncer(
            database = database,
            fetchPage = { url ->
                when (url) {
                    zhihuOnlineHistoryUrl(limit = 50) -> page(
                        items = listOf(historyItem(contentToken = "1")),
                        next = secondUrl,
                    )
                    secondUrl -> page(
                        items = listOf(historyItem(contentToken = "2")),
                        isEnd = true,
                    )
                    else -> error("Unexpected url: $url")
                }
            },
        )

        assertTrue(syncer.syncIfNeeded(force = true))

        val keys = database
            .cloudReadHistoryDao()
            .getReadContentKeysByKeys(
                listOf(
                    ContentOpenEventSupport.buildContentKey(ContentType.ANSWER, "1"),
                    ContentOpenEventSupport.buildContentKey(ContentType.ANSWER, "2"),
                ),
            ).toSet()
        assertEquals(
            setOf(
                ContentOpenEventSupport.buildContentKey(ContentType.ANSWER, "1"),
                ContentOpenEventSupport.buildContentKey(ContentType.ANSWER, "2"),
            ),
            keys,
        )
        database.close()
    }

    @Test
    fun syncUpsertsExistingRecord() = runTest {
        val database = database()
        var readTime = 1L
        val syncer = CloudReadHistorySyncer(
            database = database,
            fetchPage = {
                page(
                    items = listOf(historyItem(contentToken = "1", readTime = readTime)),
                    isEnd = true,
                )
            },
        )

        syncer.syncIfNeeded(force = true)
        readTime = 2L
        syncer.syncIfNeeded(force = true)

        assertEquals(
            2L,
            database
                .cloudReadHistoryDao()
                .getRecord(ContentType.ANSWER, "1")
                ?.readTime,
        )
        database.close()
    }

    @Test
    fun syncUsesActionUrlWhenContentTokenIsMissing() = runTest {
        val database = database()
        val syncer = CloudReadHistorySyncer(
            database = database,
            fetchPage = {
                page(
                    items = listOf(
                        historyItem(
                            contentToken = "",
                            contentType = "",
                            actionUrl = "https://www.zhihu.com/question/123/answer/456",
                        ),
                    ),
                    isEnd = true,
                )
            },
        )

        syncer.syncIfNeeded(force = true)

        assertEquals(
            setOf(ContentOpenEventSupport.buildContentKey(ContentType.ANSWER, "456")),
            database
                .cloudReadHistoryDao()
                .getReadContentKeysByKeys(
                    listOf(ContentOpenEventSupport.buildContentKey(ContentType.ANSWER, "456")),
                ).toSet(),
        )
        database.close()
    }

    @Test
    fun syncFailureKeepsCachedRecords() = runTest {
        val database = database()
        database.cloudReadHistoryDao().upsertRecords(
            listOf(
                CloudReadHistoryRecord(
                    contentType = ContentType.ANSWER,
                    contentId = "1",
                    readTime = 1L,
                    syncedAt = 1L,
                ),
            ),
        )
        val syncer = CloudReadHistorySyncer(
            database = database,
            fetchPage = { error("network failed") },
        )

        assertFalse(syncer.syncIfNeeded(force = true))

        assertEquals(
            ContentOpenEventSupport.buildContentKey(ContentType.ANSWER, "1"),
            database
                .cloudReadHistoryDao()
                .getReadContentKeysByKeys(
                    listOf(ContentOpenEventSupport.buildContentKey(ContentType.ANSWER, "1")),
                ).single(),
        )
        database.close()
    }

    @Test
    fun syncFallsBackToSmallPageWhenLargePageFails() = runTest {
        val database = database()
        val requestedUrls = mutableListOf<String>()
        val syncer = CloudReadHistorySyncer(
            database = database,
            fetchPage = { url ->
                requestedUrls += url
                if (url == zhihuOnlineHistoryUrl(limit = 50)) {
                    error("page too large")
                }
                page(
                    items = listOf(historyItem(contentToken = "1")),
                    isEnd = true,
                )
            },
        )

        assertTrue(syncer.syncIfNeeded(force = true))

        assertEquals(
            listOf(
                zhihuOnlineHistoryUrl(limit = 50),
                zhihuOnlineHistoryUrl(limit = 10),
            ),
            requestedUrls,
        )
        assertEquals(1, database.cloudReadHistoryDao().getRecordCount())
        database.close()
    }

    private fun database(): ContentFilterDatabase =
        getContentFilterDatabase(
            createTempDirectory("cloud-read-history-syncer").resolve("content-filter.db").toFile(),
        )

    private fun page(
        items: List<OnlineHistoryItem>,
        isEnd: Boolean = false,
        next: String = "",
    ): OnlineHistoryPage = OnlineHistoryPage(
        data = items,
        paging = ZhihuPaging(isEnd = isEnd, next = next),
    )

    private fun historyItem(
        contentToken: String,
        contentType: String = ContentType.ANSWER,
        readTime: Long = 1L,
        actionUrl: String = "https://www.zhihu.com/question/10/answer/$contentToken",
    ): OnlineHistoryItem = OnlineHistoryItem(
        cardType = "history",
        data = OnlineHistoryData(
            header = OnlineHistoryHeader(icon = "", title = "title"),
            action = OnlineHistoryAction(type = "open_url", url = actionUrl),
            extra = OnlineHistoryExtra(
                contentToken = contentToken,
                contentType = contentType,
                readTime = readTime,
                questionToken = "10",
            ),
        ),
    )
}
