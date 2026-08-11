/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
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

package com.github.zly2006.zhihu.filter

import com.github.zly2006.zhihu.data.OnlineHistoryAction
import com.github.zly2006.zhihu.data.OnlineHistoryData
import com.github.zly2006.zhihu.data.OnlineHistoryExtra
import com.github.zly2006.zhihu.data.OnlineHistoryHeader
import com.github.zly2006.zhihu.data.OnlineHistoryItem
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.CollectionContent
import com.github.zly2006.zhihu.navigation.History
import com.github.zly2006.zhihu.navigation.Notification
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.viewmodel.filter.CloudReadHistoryDao
import com.github.zly2006.zhihu.viewmodel.filter.CloudReadHistoryRecord
import com.github.zly2006.zhihu.viewmodel.filter.CloudReadHistorySyncState
import com.github.zly2006.zhihu.viewmodel.filter.ContentOpenEvent
import com.github.zly2006.zhihu.viewmodel.filter.ContentOpenEventDao
import com.github.zly2006.zhihu.viewmodel.filter.ContentType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContentOpenEventSupportTest {
    @Test
    fun inferOpenFromMapsKnownSourceAndTargetPairs() {
        assertEquals(
            ContentOpenFrom.QUESTION_FEED,
            ContentOpenEventSupport.inferOpenFrom(
                source = Question(questionId = 10L),
                target = Article(type = ArticleType.Answer, id = 2L),
            ),
        )
        assertEquals(
            ContentOpenFrom.ANSWER_SWITCH,
            ContentOpenEventSupport.inferOpenFrom(
                source = Article(type = ArticleType.Answer, id = 2L),
                target = Article(type = ArticleType.Answer, id = 3L),
            ),
        )
        assertEquals(
            ContentOpenFrom.COLLECTION,
            ContentOpenEventSupport.inferOpenFrom(
                source = CollectionContent(collectionId = "fav"),
                target = Article(type = ArticleType.Answer, id = 4L),
            ),
        )
        assertEquals(
            ContentOpenFrom.HISTORY,
            ContentOpenEventSupport.inferOpenFrom(
                source = History,
                target = Pin(id = 5L),
            ),
        )
        assertEquals(
            ContentOpenFrom.NOTIFICATION,
            ContentOpenEventSupport.inferOpenFrom(
                source = Notification,
                target = Question(questionId = 6L),
            ),
        )
    }

    @Test
    fun toTrackedContentIdentityReturnsSupportedContentTypes() {
        assertEquals(
            TrackedContentIdentity(ContentType.ANSWER, "11"),
            ContentOpenEventSupport.toTrackedContentIdentity(Article(type = ArticleType.Answer, id = 11L)),
        )
        assertEquals(
            TrackedContentIdentity(ContentType.ARTICLE, "12"),
            ContentOpenEventSupport.toTrackedContentIdentity(Article(type = ArticleType.Article, id = 12L)),
        )
        assertEquals(
            TrackedContentIdentity(ContentType.QUESTION, "13"),
            ContentOpenEventSupport.toTrackedContentIdentity(Question(questionId = 13L)),
        )
        assertEquals(
            TrackedContentIdentity(ContentType.PIN, "14"),
            ContentOpenEventSupport.toTrackedContentIdentity(Pin(id = 14L)),
        )
        assertNull(
            ContentOpenEventSupport.toTrackedContentIdentity(
                Person(id = "u1", urlToken = "user-1"),
            ),
        )
    }

    @Test
    fun answerContentKeysFromDestinationsKeepsOnlyAnswers() {
        val keys = ContentOpenEventSupport.answerContentKeysFromDestinations(
            listOf(
                Article(type = ArticleType.Answer, id = 11L),
                Article(type = ArticleType.Article, id = 12L),
                Question(questionId = 13L),
                Pin(id = 14L),
            ),
        )

        assertEquals(setOf(ContentOpenEventSupport.buildContentKey(ContentType.ANSWER, "11")), keys)
    }

    @Test
    fun trackedContentKeysFromDestinationsKeepsAllTrackedContentTypes() {
        val keys = ContentOpenEventSupport.trackedContentKeysFromDestinations(
            listOf(
                Article(type = ArticleType.Answer, id = 11L),
                Article(type = ArticleType.Article, id = 12L),
                Question(questionId = 13L),
                Pin(id = 14L),
                Person(id = "u1", urlToken = "user-1"),
            ),
        )

        assertEquals(
            setOf(
                ContentOpenEventSupport.buildContentKey(ContentType.ANSWER, "11"),
                ContentOpenEventSupport.buildContentKey(ContentType.ARTICLE, "12"),
                ContentOpenEventSupport.buildContentKey(ContentType.QUESTION, "13"),
                ContentOpenEventSupport.buildContentKey(ContentType.PIN, "14"),
            ),
            keys,
        )
    }

    @Test
    fun answerContentKeysFromOnlineHistoryUsesExtraTokenAndUrlFallback() {
        val keys = ContentOpenEventSupport.answerContentKeysFromOnlineHistory(
            listOf(
                onlineHistoryItem(
                    contentType = ContentType.ANSWER,
                    contentToken = "21",
                    actionUrl = "zhihu://questions/10",
                ),
                onlineHistoryItem(
                    contentType = ContentType.ARTICLE,
                    contentToken = "22",
                    actionUrl = "https://www.zhihu.com/question/10/answer/23",
                ),
            ),
        )

        assertEquals(
            setOf(
                ContentOpenEventSupport.buildContentKey(ContentType.ANSWER, "21"),
                ContentOpenEventSupport.buildContentKey(ContentType.ANSWER, "23"),
            ),
            keys,
        )
    }

    @Test
    fun answerIdsFromContentKeysKeepsOnlyAnswerIds() {
        val ids = ContentOpenEventSupport.answerIdsFromContentKeys(
            setOf(
                "answer:11",
                "article:12",
                "question:13",
                "answer:bad",
            ),
        )

        assertEquals(setOf(11L), ids)
    }

    @Test
    fun getAlreadyOpenedAnswerIdsMergesContentOpenCloudAndLocalReadKeys() = runTest {
        val contentOpenEventDao = object : ContentOpenEventDao {
            override suspend fun insert(event: ContentOpenEvent): Long = 0L

            override suspend fun getOpenedContentKeysByKeys(keys: List<String>): List<String> =
                keys.filter { it == "answer:31" }
        }
        val cloudReadHistoryDao = object : CloudReadHistoryDao {
            override suspend fun upsertRecords(records: List<CloudReadHistoryRecord>) = Unit

            override suspend fun getReadContentKeysByKeys(keys: List<String>): List<String> =
                keys.filter { it == "answer:32" || it == "answer:33" }

            override suspend fun getRecord(contentType: String, contentId: String): CloudReadHistoryRecord? = null

            override suspend fun upsertSyncState(state: CloudReadHistorySyncState) = Unit

            override suspend fun getSyncState(syncKey: String): CloudReadHistorySyncState? = null

            override suspend fun getRecordCount(): Int = 0
        }
        val ids = ContentOpenEventSupport.getAlreadyOpenedAnswerIds(
            contentOpenEventDao = contentOpenEventDao,
            cloudReadHistoryDao = cloudReadHistoryDao,
            answerIds = listOf(31L, 32L, 33L, 34L),
            extraReadContentKeys = setOf("answer:34", "question:99"),
        )

        assertEquals(setOf(31L, 32L, 33L, 34L), ids)
    }

    @Test
    fun partitionQuestionAnswerCandidatesExcludesOpenedAnswersFromPreviousAndNext() {
        val partition = ContentOpenEventSupport.partitionQuestionAnswerCandidates(
            candidates = listOf(
                Article(type = ArticleType.Answer, id = 10L),
                Article(type = ArticleType.Answer, id = 11L),
                Article(type = ArticleType.Answer, id = 12L),
                Article(type = ArticleType.Answer, id = 13L),
                Article(type = ArticleType.Article, id = 14L),
            ),
            openedAnswerIds = setOf(11L, 12L),
            currentArticleId = 10L,
        )

        assertEquals(emptyList(), partition.previousCandidates)
        assertEquals(
            listOf(Article(type = ArticleType.Answer, id = 13L)),
            partition.nextCandidates,
        )
    }

    private fun onlineHistoryItem(
        contentType: String,
        contentToken: String,
        actionUrl: String,
    ): OnlineHistoryItem = OnlineHistoryItem(
        cardType = "read_history",
        data = OnlineHistoryData(
            header = OnlineHistoryHeader(icon = "", title = "title"),
            action = OnlineHistoryAction(type = "open_url", url = actionUrl),
            extra = OnlineHistoryExtra(
                contentToken = contentToken,
                contentType = contentType,
                readTime = 1L,
                questionToken = "10",
            ),
        ),
    )
}
