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

package com.github.zly2006.zhihu.ui

import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.shared.data.DailyStoryContentResponse
import com.github.zly2006.zhihu.shared.data.DailyStoryContentSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DailyStoryNavigationTest {
    @Test
    fun detectsXiaCheStoryFromSectionOrTitle() {
        assertTrue(
            DailyStoryContentResponse(
                id = 1L,
                title = "如何正确地吐槽",
                section = DailyStoryContentSection(name = "瞎扯"),
            ).isXiaCheStory(),
        )
        assertTrue(
            DailyStoryContentResponse(
                id = 2L,
                title = "瞎扯 · 如何正确地吐槽",
            ).isXiaCheStory(),
        )
        assertFalse(
            DailyStoryContentResponse(
                id = 3L,
                title = "普通日报故事",
            ).isXiaCheStory(),
        )
    }

    @Test
    fun resolvesHiddenOriginUrlForRegularStory() {
        val destination = DailyStoryContentResponse(
            id = 1L,
            title = "普通日报故事",
            bodyHtml =
                """
                <div class="meta">
                  <a href="https://www.zhihu.com/question/10/answer/42" class="originUrl" hidden>查看知乎原文</a>
                </div>
                <div class="content">
                  <a href="https://www.zhihu.com/question/10/answer/43">正文里的其它链接</a>
                </div>
                """.trimIndent(),
        ).originDestination() as Article

        assertEquals(ArticleType.Answer, destination.type)
        assertEquals(42L, destination.id)
    }
}
