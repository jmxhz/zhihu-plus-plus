/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 */

package com.github.zly2006.zhihu.navigation

import com.github.zly2006.zhihu.shared.data.DataHolder
import com.github.zly2006.zhihu.viewmodel.ArticleViewModel.CachedAnswerContent
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import io.ktor.client.HttpClient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AnswerNavigatorTest {
    @Test
    fun questionNavigatorSkipsCurrentSessionAndPersistedOpenedAnswers() = runTest {
        val navigator = QuestionAnswerNavigator(
            questionId = 10,
            initialNextAnswers = listOf(1L, 2L, 3L, 4L).map(::answer),
            getAlreadyOpenedAnswerIds = { ids -> ids.filterTo(mutableSetOf()) { it == 3L } },
            environment = environment,
        )
        navigator.pushAnswer(cachedAnswer(1L))

        assertEquals(2L, navigator.loadNext()?.id)
        navigator.pushAnswer(cachedAnswer(2L))
        assertEquals(4L, navigator.loadNext()?.id)
    }

    @Test
    fun paginationNavigatorDeduplicatesQueue() = runTest {
        val navigator = PaginationInfoNavigator(
            questionId = 10,
            initialPaginationInfo = DataHolder.Answer.PaginationInfo(
                index = 0,
                nextAnswerIds = listOf(2L, 2L, 3L),
            ),
            environment = environment,
        )
        navigator.pushAnswer(cachedAnswer(1L))

        assertEquals(2L, navigator.loadNext()?.id)
        navigator.pushAnswer(cachedAnswer(2L))
        assertEquals(3L, navigator.loadNext()?.id)
    }

    @Test
    fun previousNavigationKeepsCurrentSessionChain() {
        val navigator = QuestionAnswerNavigator(questionId = 10, environment = environment)
        navigator.pushAnswer(cachedAnswer(1L))
        navigator.pushAnswer(cachedAnswer(2L))
        navigator.pushAnswer(cachedAnswer(3L))

        assertEquals(2L, navigator.goToPrevious()?.article?.id)
        assertEquals(1L, navigator.goToPrevious()?.article?.id)
    }

    private fun answer(id: Long) = Article(type = ArticleType.Answer, id = id)

    private fun cachedAnswer(id: Long) = CachedAnswerContent(
        article = answer(id),
        title = "answer-$id",
        authorName = "author",
        authorBio = "",
        authorAvatarUrl = "",
        content = "",
        voteUpCount = 0,
        commentCount = 0,
    )

    private val environment = object : ZhihuApiEnvironment {
        override fun httpClient(): HttpClient = HttpClient()

        override fun authenticatedCookies(): Map<String, String> = emptyMap()

        override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
    }
}
