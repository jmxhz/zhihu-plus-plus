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

import com.github.zly2006.zhihu.shared.data.OnlineHistoryPage
import com.github.zly2006.zhihu.shared.data.zhihuOnlineHistoryUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

const val CLOUD_READ_HISTORY_INCLUDE = "data[*].content,excerpt,headline"
const val CLOUD_READ_HISTORY_SYNC_KEY = "zhihu_read_history"

private const val CLOUD_READ_HISTORY_FULL_PAGE_SIZE = 50
private const val CLOUD_READ_HISTORY_FALLBACK_PAGE_SIZE = 10
private const val CLOUD_READ_HISTORY_FULL_SYNC_INTERVAL_MS = 60 * 60 * 1000L
private const val CLOUD_READ_HISTORY_RETRY_INTERVAL_MS = 5 * 60 * 1000L

class CloudReadHistorySyncer(
    private val database: ContentFilterDatabase,
    private val fetchPage: suspend (String) -> OnlineHistoryPage,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val onFailure: (Exception) -> Unit = {},
) {
    private val mutex = Mutex()
    private var runningJob: Job? = null

    fun startSyncIfNeeded(
        scope: CoroutineScope,
        force: Boolean = false,
    ): Job {
        runningJob?.takeIf { it.isActive }?.let { return it }
        return scope
            .launch {
                syncIfNeeded(force)
            }.also { runningJob = it }
    }

    suspend fun syncIfNeeded(force: Boolean = false): Boolean {
        if (!force && !shouldSync()) {
            return false
        }

        return mutex.withLock {
            if (!force && !shouldSync()) {
                return@withLock false
            }
            runCatching {
                syncAllPages()
            }.fold(
                onSuccess = { true },
                onFailure = { error ->
                    if (error is CancellationException) {
                        throw error
                    }
                    if (error is Exception) {
                        onFailure(error)
                    } else {
                        throw error
                    }
                    false
                },
            )
        }
    }

    suspend fun clearCache() {
        val dao = database.cloudReadHistoryDao()
        dao.clearRecords()
        dao.clearSyncState()
    }

    private suspend fun shouldSync(): Boolean {
        val state = database.cloudReadHistoryDao().getSyncState(CLOUD_READ_HISTORY_SYNC_KEY) ?: return true
        val elapsed = nowMillis() - state.lastSyncTime
        return if (state.fullSyncComplete) {
            elapsed >= CLOUD_READ_HISTORY_FULL_SYNC_INTERVAL_MS
        } else {
            elapsed >= CLOUD_READ_HISTORY_RETRY_INTERVAL_MS
        }
    }

    private suspend fun syncAllPages() {
        val dao = database.cloudReadHistoryDao()
        val state = dao.getSyncState(CLOUD_READ_HISTORY_SYNC_KEY)
        var url = state
            ?.takeUnless { it.fullSyncComplete }
            ?.nextUrl
            ?.takeIf { it.isNotBlank() }
            ?: zhihuOnlineHistoryUrl(limit = CLOUD_READ_HISTORY_FULL_PAGE_SIZE)
        val firstFullPageUrl = zhihuOnlineHistoryUrl(limit = CLOUD_READ_HISTORY_FULL_PAGE_SIZE)

        dao.upsertSyncState(
            CloudReadHistorySyncState(
                syncKey = CLOUD_READ_HISTORY_SYNC_KEY,
                nextUrl = url,
                lastSyncTime = nowMillis(),
                fullSyncComplete = false,
            ),
        )

        while (true) {
            val currentUrl = url
            val page = fetchPageWithFallback(currentUrl, currentUrl == firstFullPageUrl)
            val syncedAt = nowMillis()
            val records = page.data.mapNotNull { item ->
                CloudReadHistoryRecord.fromOnlineHistoryItem(item, syncedAt)
            }
            if (records.isNotEmpty()) {
                dao.upsertRecords(records)
            }

            val nextUrl = page.paging
                ?.takeUnless { it.isEnd }
                ?.next
                ?.takeIf { it.isNotBlank() }
            dao.upsertSyncState(
                CloudReadHistorySyncState(
                    syncKey = CLOUD_READ_HISTORY_SYNC_KEY,
                    nextUrl = nextUrl,
                    lastSyncTime = syncedAt,
                    fullSyncComplete = nextUrl == null,
                ),
            )
            if (nextUrl == null) {
                return
            }
            url = nextUrl
        }
    }

    private suspend fun fetchPageWithFallback(
        url: String,
        allowFallback: Boolean,
    ): OnlineHistoryPage = runCatching {
        fetchPage(url)
    }.getOrElse { error ->
        if (!allowFallback) {
            throw error
        }
        fetchPage(zhihuOnlineHistoryUrl(limit = CLOUD_READ_HISTORY_FALLBACK_PAGE_SIZE))
    }
}
