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

import androidx.room.Entity
import androidx.room.Index
import com.github.zly2006.zhihu.navigation.resolveContent
import com.github.zly2006.zhihu.shared.data.OnlineHistoryItem
import com.github.zly2006.zhihu.shared.filter.ContentOpenEventSupport

@Entity(
    tableName = CloudReadHistoryRecord.TABLE_NAME,
    primaryKeys = ["contentType", "contentId"],
    indices = [
        Index(value = ["contentType"]),
        Index(value = ["readTime"]),
    ],
)
data class CloudReadHistoryRecord(
    val contentType: String,
    val contentId: String,
    val questionId: String? = null,
    val actionUrl: String? = null,
    val readTime: Long,
    val syncedAt: Long,
) {
    companion object {
        const val TABLE_NAME = "cloud_read_history_records"

        fun fromOnlineHistoryItem(
            item: OnlineHistoryItem,
            syncedAt: Long,
        ): CloudReadHistoryRecord? {
            val extra = item.data.extra
            val extraType = extra.contentType.trim().lowercase()
            val extraToken = extra.contentToken.trim()
            val identity = if (extraType.isNotBlank() && extraToken.isNotBlank()) {
                extraType to extraToken
            } else {
                resolveContent(item.data.action.url)
                    ?.let(ContentOpenEventSupport::toTrackedContentIdentity)
                    ?.let { it.type to it.id }
            } ?: return null

            return CloudReadHistoryRecord(
                contentType = identity.first,
                contentId = identity.second,
                questionId = extra.questionToken.trim().ifBlank { null },
                actionUrl = item.data.action.url
                    .ifBlank { null },
                readTime = extra.readTime,
                syncedAt = syncedAt,
            )
        }
    }
}
