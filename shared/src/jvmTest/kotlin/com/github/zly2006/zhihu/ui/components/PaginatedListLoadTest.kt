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

package com.github.zly2006.zhihu.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class PaginatedListLoadTest {
    @Test
    fun paginationWithoutPollingWaitsForVisibleProgressAndRespectsEndOfList() = runDesktopComposeUiTest(width = 400, height = 400) {
        var requests = 0
        val items = mutableStateOf(listOf(1))
        val reachedEnd = mutableStateOf(false)
        val settledCount = mutableIntStateOf(1)
        mainClock.autoAdvance = false
        setContent {
            MaterialTheme {
                PaginatedList(
                    items = items.value,
                    onLoadMore = { requests++ },
                    isEnd = { reachedEnd.value },
                    repeatLoadMore = false,
                    loadMoreItemCount = settledCount.intValue,
                ) { Text(it.toString(), modifier = Modifier.height(48.dp)) }
            }
        }
        mainClock.advanceTimeBy(500)
        assertEquals(1, requests)

        runOnIdle { items.value = listOf(1, 2) }
        mainClock.advanceTimeBy(500)
        assertEquals(1, requests)

        runOnIdle { settledCount.intValue = 2 }
        mainClock.advanceTimeBy(500)
        assertEquals(2, requests)

        runOnIdle { items.value = (1..40).toList() }
        mainClock.advanceTimeBy(500)
        runOnIdle { items.value = listOf(1, 2) }
        mainClock.advanceTimeBy(500)
        assertEquals(2, requests)

        runOnIdle {
            items.value = listOf(1, 2, 3)
            reachedEnd.value = true
        }
        mainClock.advanceTimeBy(500)
        assertEquals(2, requests)
    }
}
