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

package com.github.zly2006.zhihu.viewmodel.filter

import com.github.zly2006.zhihu.data.CommonFeed
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.Person
import com.github.zly2006.zhihu.data.toFeedDisplayItemNavDestinationJson
import com.github.zly2006.zhihu.filter.ContentOpenEventSupport
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class ForegroundReadFilterPipelineTest {
    @Test
    fun disabledOrReverseBlockReturnsItemsWithoutRecording() = runTest {
        listOf(
            FeedFilterSettings(enableContentFilter = false),
            FeedFilterSettings(reverseBlock = true),
        ).forEach { settings ->
            val fixture = fixture(settings)
            val item = item("item", 1)

            assertEquals(listOf(item), fixture.pipeline().filter(listOf(item)))
            assertEquals(0, fixture.database.contentFilterDao().getRecordCount())
            fixture.database.close()
        }
    }

    @Test
    fun blocksReadAnswerWhenContentFilterDisabled() = runTest {
        val fixture = fixture(settings = FeedFilterSettings(enableContentFilter = false))
        val openedAnswer = answerItem("opened", answerId = 1, questionId = 10)
        val otherAnswer = answerItem("other", answerId = 2, questionId = 10)
        fixture.database.contentOpenEventDao().insert(
            ContentOpenEvent(
                contentType = ContentType.ANSWER,
                contentId = "1",
                questionId = 10,
                openFrom = "test",
            ),
        )

        val result = fixture.pipeline().filter(listOf(openedAnswer, otherAnswer))

        assertEquals(listOf("other"), result.map { it.title })
        assertEquals(0, fixture.database.contentFilterDao().getRecordCount())
        fixture.database.close()
    }

    @Test
    fun blocksReadAnswerWhenReverseBlockEnabled() = runTest {
        val fixture = fixture(settings = FeedFilterSettings(reverseBlock = true))
        val item = answerItem("opened", answerId = 1, questionId = 10, isFollowing = true)
        fixture.database.contentOpenEventDao().insert(
            ContentOpenEvent(
                contentType = ContentType.ANSWER,
                contentId = "1",
                questionId = 10,
                openFrom = "test",
            ),
        )

        val result = fixture.pipeline().filter(listOf(item))

        assertEquals(emptyList(), result)
        assertEquals(0, fixture.database.contentFilterDao().getRecordCount())
        fixture.database.close()
    }

    @Test
    fun keepsUnviewedNormalItemAndRecordsView() = runTest {
        val fixture = fixture()
        val item = item("item", 1, details = "文章 · 100 赞")

        val result = fixture.pipeline().filter(listOf(item))

        assertEquals(listOf("item"), result.map { it.title })
        assertEquals(1, fixture.database.contentFilterDao().getRecordCount())
        assertEquals(
            emptyList(),
            fixture.database
                .blockedFeedRecordDao()
                .observeAll()
                .first(),
        )
        fixture.database.close()
    }

    @Test
    fun blocksAlreadyViewedUnfollowedItemAndStoresHistory() = runTest {
        val fixture = fixture()
        val item = item("item", 1)
        fixture.manager.recordContentView("article", "1")

        val result = fixture.pipeline().filter(listOf(item))

        assertEquals(emptyList(), result)
        assertEquals(
            listOf("已读过且未关注作者"),
            fixture.database
                .blockedFeedRecordDao()
                .observeAll()
                .first()
                .map { it.blockedReason },
        )
        fixture.database.close()
    }

    @Test
    fun blocksOpenedAnswerByAnswerIdAndKeepsOtherAnswersFromSameQuestion() = runTest {
        val fixture = fixture()
        val openedAnswer = answerItem("opened", answerId = 1, questionId = 10)
        val otherAnswer = answerItem("other", answerId = 2, questionId = 10)
        fixture.database.contentOpenEventDao().insert(
            ContentOpenEvent(
                contentType = ContentType.ANSWER,
                contentId = "1",
                questionId = 10,
                openFrom = "test",
            ),
        )

        val result = fixture.pipeline().filter(listOf(openedAnswer, otherAnswer))

        assertEquals(listOf("other"), result.map { it.title })
        assertEquals(
            listOf("已阅读过回答"),
            fixture.database
                .blockedFeedRecordDao()
                .observeAll()
                .first()
                .map { it.blockedReason },
        )
        fixture.database.close()
    }

    @Test
    fun blocksOpenedArticlePinAndQuestionItems() = runTest {
        val fixture = fixture()
        val article = item("opened article", id = 1, details = "文章")
        val pin = pinItem("opened pin", id = 2)
        val question = questionItem("opened question", id = 3)
        listOf(
            ContentType.ARTICLE to "1",
            ContentType.PIN to "2",
            ContentType.QUESTION to "3",
        ).forEach { (type, id) ->
            fixture.database.contentOpenEventDao().insert(
                ContentOpenEvent(
                    contentType = type,
                    contentId = id,
                    openFrom = "test",
                ),
            )
        }

        val result = fixture.pipeline().filter(listOf(article, pin, question))

        assertEquals(emptyList(), result)
        assertEquals(
            setOf("已阅读过文章", "已阅读过想法", "已阅读过问题"),
            fixture.database
                .blockedFeedRecordDao()
                .observeAll()
                .first()
                .map { it.blockedReason }
                .toSet(),
        )
        fixture.database.close()
    }

    @Test
    fun blocksOpenedArticleEvenWhenAuthorIsFollowed() = runTest {
        val fixture = fixture()
        val item = item("followed opened article", id = 1, isFollowing = true)
        fixture.database.contentOpenEventDao().insert(
            ContentOpenEvent(
                contentType = ContentType.ARTICLE,
                contentId = "1",
                openFrom = "test",
            ),
        )

        val result = fixture.pipeline().filter(listOf(item))

        assertEquals(emptyList(), result)
        fixture.database.close()
    }

    @Test
    fun blocksExtraReadAnswerKeyEvenWhenAuthorIsFollowed() = runTest {
        val fixture = fixture()
        val item = answerItem("followed opened", answerId = 1, questionId = 10, isFollowing = true)

        val result = fixture.pipeline().filter(
            items = listOf(item),
            extraReadContentKeys = setOf(ContentOpenEventSupport.buildContentKey(ContentType.ANSWER, "1")),
        )

        assertEquals(emptyList(), result)
        fixture.database.close()
    }

    @Test
    fun blocksCloudReadAnswerKeyEvenWhenAuthorIsFollowed() = runTest {
        val fixture = fixture()
        val item = answerItem("followed cloud read", answerId = 1, questionId = 10, isFollowing = true)
        fixture.database.cloudReadHistoryDao().upsertRecords(
            listOf(
                CloudReadHistoryRecord(
                    contentType = ContentType.ANSWER,
                    contentId = "1",
                    questionId = "10",
                    readTime = 1L,
                    syncedAt = 1L,
                ),
            ),
        )

        val result = fixture.pipeline().filter(listOf(item))

        assertEquals(emptyList(), result)
        fixture.database.close()
    }

    @Test
    fun keepsFollowedItemEvenWhenAlreadyViewedOrLowQuality() = runTest {
        val fixture = fixture()
        val item = item("followed", 1, details = "1 分钟前", isFollowing = true)
        fixture.manager.recordContentView("article", "1")

        val result = fixture.pipeline().filter(listOf(item))

        assertEquals(listOf("followed"), result.map { it.title })
        assertEquals(
            emptyList(),
            fixture.database
                .blockedFeedRecordDao()
                .observeAll()
                .first(),
        )
        fixture.database.close()
    }

    @Test
    fun contentExposureRecorderMarksInteractionAndCleanup() = runTest {
        val fixture = fixture()

        fixture.pipeline().filter(listOf(item("item", 1, details = "文章 · 100 赞")))
        fixture.manager.recordContentInteraction("article", "1")

        val record = fixture.database.contentFilterDao().getViewRecord("article:1")
        assertEquals(true, record?.hasInteraction)
        fixture.database.contentFilterDao().insertOrUpdateViewRecord(
            ContentViewRecord(
                id = "article:old",
                targetType = "article",
                targetId = "old",
                firstViewTime = 0L,
                lastViewTime = 0L,
            ),
        )
        fixture.manager.cleanupOldData()
        assertEquals(null, fixture.database.contentFilterDao().getViewRecord("article:old"))
        fixture.database.close()
    }

    private fun fixture(settings: FeedFilterSettings = FeedFilterSettings()): Fixture {
        val database = getContentFilterDatabase(
            createTempDirectory("foreground-read-filter-pipeline").resolve("content-filter.db").toFile(),
        )
        return Fixture(database, settings)
    }

    private class Fixture(
        val database: ContentFilterDatabase,
        val settings: FeedFilterSettings,
    ) {
        val manager = ContentFilterManager(database.contentFilterDao())

        fun pipeline(): ForegroundReadFilterPipeline = ForegroundReadFilterPipeline(
            settings = settings,
            contentFilterManager = manager,
            blockedFeedRecordDao = database.blockedFeedRecordDao(),
            contentOpenEventDao = database.contentOpenEventDao(),
            cloudReadHistoryDao = database.cloudReadHistoryDao(),
        )
    }

    private fun item(
        title: String,
        id: Long,
        details: String = "",
        isFollowing: Boolean = false,
    ): FeedDisplayItem = FeedDisplayItem(
        title = title,
        summary = null,
        details = details,
        feed = CommonFeed(
            target = Feed.ArticleTarget(
                id = id,
                url = "",
                author = person(isFollowing),
                title = title,
            ),
        ),
        navDestinationJson = Article(type = ArticleType.Article, id = id).toFeedDisplayItemNavDestinationJson(),
    )

    private fun answerItem(
        title: String,
        answerId: Long,
        questionId: Long,
        isFollowing: Boolean = false,
    ): FeedDisplayItem = FeedDisplayItem(
        title = title,
        summary = null,
        details = "回答",
        feed = CommonFeed(
            target = Feed.AnswerTarget(
                id = answerId,
                url = "",
                author = person(isFollowing),
                question = Feed.QuestionTarget(
                    id = questionId,
                    url = "",
                    type = "question",
                    _title = "question",
                ),
            ),
        ),
        navDestinationJson = Article(type = ArticleType.Answer, id = answerId).toFeedDisplayItemNavDestinationJson(),
    )

    private fun pinItem(
        title: String,
        id: Long,
        isFollowing: Boolean = false,
    ): FeedDisplayItem = FeedDisplayItem(
        title = title,
        summary = null,
        details = "想法",
        feed = CommonFeed(
            target = Feed.PinTarget(
                id = id,
                url = "",
                author = person(isFollowing),
                commentCount = 0,
            ),
        ),
        navDestinationJson = Pin(id).toFeedDisplayItemNavDestinationJson(),
    )

    private fun questionItem(
        title: String,
        id: Long,
    ): FeedDisplayItem = FeedDisplayItem(
        title = title,
        summary = null,
        details = "问题",
        feed = CommonFeed(
            target = Feed.QuestionTarget(
                id = id,
                url = "",
                type = "question",
                _title = title,
            ),
        ),
        navDestinationJson = Question(questionId = id, title = title).toFeedDisplayItemNavDestinationJson(),
    )

    private fun person(isFollowing: Boolean): Person = Person(
        id = "author-id",
        url = "",
        userType = "people",
        name = "author",
        headline = "",
        avatarUrl = "",
        isFollowing = isFollowing,
    )
}
