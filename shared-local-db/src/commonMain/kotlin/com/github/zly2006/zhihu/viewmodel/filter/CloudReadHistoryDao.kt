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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CloudReadHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecords(records: List<CloudReadHistoryRecord>)

    @Query(
        """
        SELECT contentType || ':' || contentId
        FROM ${CloudReadHistoryRecord.TABLE_NAME}
        WHERE (contentType || ':' || contentId) IN (:keys)
        """,
    )
    suspend fun getReadContentKeysByKeys(keys: List<String>): List<String>

    @Query("SELECT * FROM ${CloudReadHistoryRecord.TABLE_NAME} WHERE contentType = :contentType AND contentId = :contentId")
    suspend fun getRecord(contentType: String, contentId: String): CloudReadHistoryRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncState(state: CloudReadHistorySyncState)

    @Query("SELECT * FROM ${CloudReadHistorySyncState.TABLE_NAME} WHERE syncKey = :syncKey")
    suspend fun getSyncState(syncKey: String): CloudReadHistorySyncState?

    @Query("SELECT COUNT(*) FROM ${CloudReadHistoryRecord.TABLE_NAME}")
    suspend fun getRecordCount(): Int
}
