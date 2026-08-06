/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 */

package com.github.zly2006.zhihu.viewmodel.feed

import com.github.zly2006.zhihu.data.CommonFeed
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.Person
import com.github.zly2006.zhihu.data.toFeedDisplayItemNavDestinationJson
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeFeedDeduplicationTest {
    @Test
    fun androidAndWebAnswersUseSameCanonicalKey() {
        val androidItem = item(
            navDestinationJson = Article(type = ArticleType.Answer, id = 42L)
                .toFeedDisplayItemNavDestinationJson(),
        )
        val webItem = item(
            feed = CommonFeed(
                target = Feed.AnswerTarget(
                    id = 42L,
                    url = "",
                    author = Person(
                        id = "author",
                        url = "",
                        userType = "people",
                        name = "author",
                        headline = "",
                        avatarUrl = "",
                    ),
                    question = Feed.QuestionTarget(
                        id = 10L,
                        url = "",
                        type = "question",
                        _title = "question",
                    ),
                ),
            ),
        )

        assertEquals("answer:42", androidItem.homeFeedContentKey)
        assertEquals(androidItem.homeFeedContentKey, webItem.homeFeedContentKey)
    }

    @Test
    fun continuationStopsForContentEndFailureAndThreePageLimit() {
        assertTrue(shouldContinueHomeFeedAfterPage(1, producedVisibleItems = false, isEnd = false, failed = false))
        assertFalse(shouldContinueHomeFeedAfterPage(1, producedVisibleItems = true, isEnd = false, failed = false))
        assertFalse(shouldContinueHomeFeedAfterPage(1, producedVisibleItems = false, isEnd = true, failed = false))
        assertFalse(shouldContinueHomeFeedAfterPage(1, producedVisibleItems = false, isEnd = false, failed = true))
        assertFalse(shouldContinueHomeFeedAfterPage(3, producedVisibleItems = false, isEnd = false, failed = false))
    }

    private fun item(
        navDestinationJson: String? = null,
        feed: Feed? = null,
    ) = FeedDisplayItem(
        title = "title",
        summary = null,
        details = "",
        feed = feed,
        navDestinationJson = navDestinationJson,
    )
}
