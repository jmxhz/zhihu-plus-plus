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

package com.github.zly2006.zhihu

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.zly2006.zhihu.data.AccountData
import com.github.zly2006.zhihu.test.InstrumentedTestEnvironment
import com.github.zly2006.zhihu.test.ZhihuMockApi
import com.github.zly2006.zhihu.util.signFetchRequest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountDataNetworkInstrumentedTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        InstrumentedTestEnvironment.reseed(context)
    }

    @Test
    fun fetchGetAutomaticallySignsOnlyZhihuWebApiRequests() = runBlocking {
        AccountData.fetchGet(context, "https://www.zhihu.com/api/v4/search/hot_search")
        val webApiRequest = lastRequestContaining("/api/v4/search/hot_search")
        assertTrue(webApiRequest.hasHeader("x-zse-93"))
        assertTrue(webApiRequest.hasHeader("x-zse-96"))
        assertTrue(webApiRequest.hasHeader("x-requested-with"))

        AccountData.fetchGet(context, "https://api.zhihu.com/moments/recent?type=raw")
        val androidApiRequest = lastRequestContaining("https://api.zhihu.com/moments/recent")
        assertFalse(androidApiRequest.hasHeader("x-zse-93"))
        assertFalse(androidApiRequest.hasHeader("x-zse-96"))
    }

    @Test
    fun explicitSignFetchRequestIsNotDuplicatedByAutomaticSigning() = runBlocking {
        AccountData.fetchGet(context, "https://www.zhihu.com/api/v4/search/hot_search") {
            signFetchRequest()
        }

        val request = lastRequestContaining("/api/v4/search/hot_search")
        assertEquals(1, request.headerValues("x-zse-93").size)
        assertEquals(1, request.headerValues("x-zse-96").size)
        assertEquals(1, request.headerValues("x-requested-with").size)
    }

    @Test
    fun verifyLoginUsesSignedRequestAndReturnsFalseForUnauthorized() = runBlocking {
        val login = AccountData.verifyLogin(
            context = context,
            cookies = mapOf(
                "z_c0" to "android-test-zc0",
                "d_c0" to "android-test-dc0",
            ),
        )
        assertTrue(login)
        assertTrue(lastRequestContaining("/api/v4/me").hasHeader("x-zse-96"))

        ZhihuMockApi.mockJson(
            method = HttpMethod.Get,
            url = "https://www.zhihu.com/api/v4/me",
            body = """{"error":{"message":"unauthorized"}}""",
            status = HttpStatusCode.Unauthorized,
        )
        val unauthorizedLogin = AccountData.verifyLogin(
            context = context,
            cookies = mapOf(
                "z_c0" to "expired-zc0",
                "d_c0" to "android-test-dc0",
            ),
        )
        assertFalse(unauthorizedLogin)
    }

    private fun lastRequestContaining(urlPart: String): ZhihuMockApi.RecordedRequest =
        ZhihuMockApi.recordedRequests().last { it.url.contains(urlPart) }

    private fun ZhihuMockApi.RecordedRequest.headerValues(name: String): List<String> =
        headers
            .entries
            .firstOrNull { it.key.equals(name, ignoreCase = true) }
            ?.value
            .orEmpty()

    private fun ZhihuMockApi.RecordedRequest.hasHeader(name: String): Boolean = headerValues(name).isNotEmpty()
}
