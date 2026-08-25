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

package com.github.zly2006.zhihu.viewmodel.local

import androidx.lifecycle.viewModelScope
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.toFeedDisplayItemNavDestinationJson
import com.github.zly2006.zhihu.viewmodel.ContentInteractionEnvironment
import com.github.zly2006.zhihu.viewmodel.LocalRecommendationEnvironment
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.feed.BaseFeedViewModel
import com.github.zly2006.zhihu.viewmodel.feed.HomeFeedInteractionViewModel
import com.github.zly2006.zhihu.viewmodel.feed.homeFeedContentKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LocalHomeFeedViewModel :
    BaseFeedViewModel(),
    HomeFeedInteractionViewModel {
    private lateinit var recommendationEngine: LocalRecommendationEngine
    private val recommendationResults = mutableMapOf<String, CrawlingResult>()

    override val initialUrl: String
        get() = error("LocalHomeFeedViewModel should not be used directly. Use LocalFeedViewModel instead.")

    override fun displayItemKey(item: FeedDisplayItem): String = item.homeFeedContentKey

    override fun loadMore(environment: PaginationEnvironment) {
        if (displayItems.isEmpty()) {
            super.loadMore(environment)
        }
    }

    override suspend fun fetchFeeds(environment: PaginationEnvironment) {
        try {
            val engine = ensureEngine(environment)
            recommendationResults.clear()
            var remainingBatches = MAX_LOCAL_RECOMMENDATION_BATCHES
            while (remainingBatches-- > 0) {
                val recommendations = engine.generateRecommendations(LOCAL_RECOMMENDATION_BATCH_SIZE)
                if (recommendations.isEmpty()) break
                val candidates = recommendations
                    .map { entry ->
                        FeedDisplayItem(
                            title = entry.feed.title,
                            summary = entry.feed.summary,
                            details = entry.feed.reasonDisplay,
                            feed = null,
                            navDestinationJson = entry.navDestination?.toFeedDisplayItemNavDestinationJson(),
                            isFiltered = false,
                        ).also { item ->
                            recommendationResults[item.stableKey] = entry.result
                        }
                    }.filter { candidate ->
                        displayItems.none { existing -> existing.homeFeedContentKey == candidate.homeFeedContentKey }
                    }
                if (candidates.isEmpty()) continue
                val filterResult = environment.applyHomeFeedFilters(candidates)
                val visibleItems = if (filterResult.reverseBlock) {
                    filterResult.filteredItems
                } else {
                    filterResult.filteredItems.filterNot { it.isFiltered }
                }
                if (visibleItems.isNotEmpty()) {
                    addDisplayItems(visibleItems)
                    latestLoadedDisplayItems.value = filterResult.filteredItems
                    break
                }
            }
            if (displayItems.isEmpty()) {
                generateFallbackContent()
            }
        } catch (e: Exception) {
            environment.handleLocalRecommendationFailure(e)
            if (e.message?.contains("does not exist. Is Room annotation processor correctly configured?") == true) {
                environment.showLocalRecommendationDatabaseError()
            }
            generateFallbackContent()
        } finally {
            isLoading = false
        }
    }

    fun onLocalItemOpened(item: FeedDisplayItem) {
        val result = recommendationResults[item.stableKey] ?: return
        if (!::recommendationEngine.isInitialized) {
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            recommendationEngine.recordContentOpened(result.contentId, result.reason)
        }
    }

    private suspend fun ensureEngine(environment: LocalRecommendationEnvironment): LocalRecommendationEngine {
        if (!::recommendationEngine.isInitialized) {
            recommendationEngine = environment.localRecommendationEngine()
                ?: error("LocalRecommendationEngine is required for local home feed")
        }
        recommendationEngine.initialize()
        return recommendationEngine
    }

    private suspend fun generateFallbackContent() {
        val fallbackItems = listOf(
            FeedDisplayItem(
                title = "本地推荐正在建立候选池",
                summary = "系统会先抓取关注动态、热门内容和相关话题，再根据你的点击逐步调整排序。",
                details = "本地推荐 · 冷启动",
                feed = null,
                isFiltered = false,
            ),
            FeedDisplayItem(
                title = "你的行为只在本地学习",
                summary = "点开内容会影响后续排序，但这些学习信号不会作为推荐特征上传到服务器。",
                details = "本地推荐 · 隐私优先",
                feed = null,
                isFiltered = false,
            ),
        )

        fallbackItems.forEach { item ->
            if (displayItems.none { existing -> existing.stableKey == item.stableKey }) {
                displayItems.add(item)
            }
            delay(300)
        }
        latestLoadedDisplayItems.value = fallbackItems
    }

    override suspend fun recordContentInteraction(
        environment: ContentInteractionEnvironment,
        feed: Feed,
    ) = Unit

    override fun onUiContentClick(environment: ContentInteractionEnvironment, feed: Feed, item: FeedDisplayItem) = Unit
}

private const val LOCAL_RECOMMENDATION_BATCH_SIZE = 20
private const val MAX_LOCAL_RECOMMENDATION_BATCHES = 4
