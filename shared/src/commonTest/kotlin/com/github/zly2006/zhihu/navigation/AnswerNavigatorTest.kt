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

package com.github.zly2006.zhihu.navigation

import com.github.zly2006.zhihu.shared.data.CommonFeed
import com.github.zly2006.zhihu.shared.data.DataHolder
import com.github.zly2006.zhihu.shared.data.Feed
import com.github.zly2006.zhihu.shared.data.Person
import com.github.zly2006.zhihu.viewmodel.ArticleViewModel.CachedAnswerContent
import com.github.zly2006.zhihu.viewmodel.CollectionItem
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnswerNavigatorTest {
    @Test
    fun questionNavigatorSkipsAnswersVisitedInCurrentSession() = runTest {
        val navigator = QuestionAnswerNavigator(
            questionId = 10,
            repository = object : AnswerNavigatorRepository {
                override suspend fun fetchAnswerContent(article: Article): DataHolder.Answer? = null

                override suspend fun fetchQuestionFeeds(
                    questionId: Long,
                    pageUrl: String?,
                ): AnswerNavigatorPage<Feed> = AnswerNavigatorPage(
                    items = listOf(
                        answerFeed(answerId = 1, questionId = questionId),
                        answerFeed(answerId = 2, questionId = questionId),
                        answerFeed(answerId = 3, questionId = questionId),
                    ),
                    nextUrl = "",
                )

                override suspend fun fetchCollectionItems(pageUrl: String): AnswerNavigatorPage<CollectionItem> =
                    AnswerNavigatorPage(emptyList(), "")

                override suspend fun getAlreadyOpenedAnswerIds(answerIds: List<Long>): Set<Long> = emptySet()
            },
        )

        navigator.pushAnswer(cachedAnswer(answerId = 1))

        assertEquals(2L, navigator.loadNext()?.id)
    }

    @Test
    fun questionNavigatorUsesSelectedSortOrderForInitialQuestionFeedsPage() = runTest {
        var requestedPageUrl = ""
        val navigator = QuestionAnswerNavigator(
            questionId = 10,
            sortOrder = "updated",
            repository = object : AnswerNavigatorRepository {
                override suspend fun fetchAnswerContent(article: Article): DataHolder.Answer? = null

                override suspend fun fetchQuestionFeeds(
                    questionId: Long,
                    pageUrl: String?,
                ): AnswerNavigatorPage<Feed> {
                    requestedPageUrl = pageUrl.orEmpty()
                    return AnswerNavigatorPage(
                        items = listOf(answerFeed(answerId = 2, questionId = questionId)),
                        nextUrl = "",
                    )
                }

                override suspend fun fetchCollectionItems(pageUrl: String): AnswerNavigatorPage<CollectionItem> =
                    AnswerNavigatorPage(emptyList(), "")

                override suspend fun getAlreadyOpenedAnswerIds(answerIds: List<Long>): Set<Long> = emptySet()
            },
        )

        navigator.pushAnswer(cachedAnswer(answerId = 1))
        assertEquals(2L, navigator.loadNext()?.id)

        assertTrue(requestedPageUrl.contains("order=updated"))
    }

    @Test
    fun questionNavigatorSkipsHistoryReadAnswersOnlyForNextAndKeepsSessionPreviousChain() = runTest {
        val navigator = QuestionAnswerNavigator(
            questionId = 10,
            repository = repositoryWithQuestionAnswers(
                answers = listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L),
                openedAnswerIds = setOf(7L),
            ),
        )

        navigator.pushAnswer(cachedAnswer(answerId = 7))

        val firstNext = navigator.loadNext()
        assertEquals(1L, firstNext?.id)
        navigator.pushAnswer(cachedAnswer(answerId = firstNext!!.id))

        val secondNext = navigator.loadNext()
        assertEquals(2L, secondNext?.id)
        navigator.pushAnswer(cachedAnswer(answerId = secondNext!!.id))

        assertEquals(1L, navigator.goToPrevious()?.article?.id)
        assertEquals(7L, navigator.goToPrevious()?.article?.id)
    }

    @Test
    fun questionNavigatorSkipsPreviouslyReadAnswersWhenEnteringAnotherAnswer() = runTest {
        val navigator = QuestionAnswerNavigator(
            questionId = 10,
            repository = repositoryWithQuestionAnswers(
                answers = listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L),
                openedAnswerIds = setOf(1L, 2L, 7L),
            ),
        )

        navigator.pushAnswer(cachedAnswer(answerId = 5))

        listOf(3L, 4L, 6L, 8L).forEach { expectedId ->
            val next = navigator.loadNext()
            assertEquals(expectedId, next?.id)
            navigator.pushAnswer(cachedAnswer(answerId = next!!.id))
        }

        assertEquals(6L, navigator.goToPrevious()?.article?.id)
        assertEquals(4L, navigator.goToPrevious()?.article?.id)
    }

    @Test
    fun paginationNavigatorSkipsQueuedAnswersVisitedInCurrentSession() = runTest {
        val navigator = PaginationInfoNavigator(
            questionId = 10,
            initialPaginationInfo = DataHolder.Answer.PaginationInfo(
                index = 0,
                nextAnswerIds = listOf(1, 2, 3),
            ),
            repository = emptyRepository,
        )

        navigator.pushAnswer(cachedAnswer(answerId = 1))

        assertEquals(2L, navigator.loadNext()?.id)
    }

    @Test
    fun paginationNavigatorDeduplicatesInitialQueues() = runTest {
        val navigator = PaginationInfoNavigator(
            questionId = 10,
            initialPaginationInfo = DataHolder.Answer.PaginationInfo(
                index = 0,
                nextAnswerIds = listOf(1, 2, 2, 3),
            ),
            repository = emptyRepository,
        )

        navigator.pushAnswer(cachedAnswer(answerId = 1))
        assertEquals(2L, navigator.loadNext()?.id)
        navigator.pushAnswer(cachedAnswer(answerId = 2))
        assertEquals(3L, navigator.loadNext()?.id)
    }

    private val emptyRepository = object : AnswerNavigatorRepository {
        override suspend fun fetchAnswerContent(article: Article): DataHolder.Answer? = null

        override suspend fun fetchQuestionFeeds(
            questionId: Long,
            pageUrl: String?,
        ): AnswerNavigatorPage<Feed> = AnswerNavigatorPage(emptyList(), "")

        override suspend fun fetchCollectionItems(pageUrl: String): AnswerNavigatorPage<CollectionItem> =
            AnswerNavigatorPage(emptyList(), "")

        override suspend fun getAlreadyOpenedAnswerIds(answerIds: List<Long>): Set<Long> = emptySet()
    }

    private fun repositoryWithQuestionAnswers(
        answers: List<Long>,
        openedAnswerIds: Set<Long>,
    ) = object : AnswerNavigatorRepository {
        override suspend fun fetchAnswerContent(article: Article): DataHolder.Answer? = null

        override suspend fun fetchQuestionFeeds(
            questionId: Long,
            pageUrl: String?,
        ): AnswerNavigatorPage<Feed> = AnswerNavigatorPage(
            items = answers.map { answerId -> answerFeed(answerId = answerId, questionId = questionId) },
            nextUrl = "",
        )

        override suspend fun fetchCollectionItems(pageUrl: String): AnswerNavigatorPage<CollectionItem> =
            AnswerNavigatorPage(emptyList(), "")

        override suspend fun getAlreadyOpenedAnswerIds(answerIds: List<Long>): Set<Long> =
            answerIds.filterTo(mutableSetOf()) { it in openedAnswerIds }
    }

    private fun answerFeed(
        answerId: Long,
        questionId: Long,
    ): Feed = CommonFeed(
        target = Feed.AnswerTarget(
            id = answerId,
            url = "",
            author = Person(
                id = "author-$answerId",
                url = "",
                userType = "people",
                name = "author",
                headline = "",
                avatarUrl = "",
            ),
            question = Feed.QuestionTarget(
                id = questionId,
                url = "",
                type = "question",
                _title = "question",
            ),
        ),
    )

    private fun cachedAnswer(answerId: Long): CachedAnswerContent = CachedAnswerContent(
        article = Article(
            type = ArticleType.Answer,
            id = answerId,
        ),
        title = "question",
        authorName = "author",
        authorBio = "",
        authorAvatarUrl = "",
        content = "",
        voteUpCount = 0,
        commentCount = 0,
    )
}
