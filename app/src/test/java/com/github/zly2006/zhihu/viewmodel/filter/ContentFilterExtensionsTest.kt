package com.github.zly2006.zhihu.viewmodel.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentFilterExtensionsTest {
    // ----- getLinkBasedAdReason tests -----

    @Test
    fun `getLinkBasedAdReason detects zhihu ad platform url`() {
        val reason = ContentFilterExtensions.getLinkBasedAdReason(
            content = "some text with xg.zhihu.com link",
            blockZhihuAdPlatform = true,
            blockZhihuSchool = false,
            blockWeChatOfficialAccount = false,
        )
        assertEquals("知乎广告平台内容", reason)
    }

    @Test
    fun `getLinkBasedAdReason detects zhihu school url`() {
        val reason = ContentFilterExtensions.getLinkBasedAdReason(
            content = "check out d.zhihu.com for courses",
            blockZhihuAdPlatform = false,
            blockZhihuSchool = true,
            blockWeChatOfficialAccount = false,
        )
        assertEquals("知乎学堂内容", reason)
    }

    @Test
    fun `getLinkBasedAdReason detects zhihu school data attribute`() {
        val reason = ContentFilterExtensions.getLinkBasedAdReason(
            content = """data-edu-card-id="12345"""",
            blockZhihuAdPlatform = false,
            blockZhihuSchool = true,
            blockWeChatOfficialAccount = false,
        )
        assertEquals("知乎学堂内容", reason)
    }

    @Test
    fun `getLinkBasedAdReason detects wechat official account url`() {
        val reason = ContentFilterExtensions.getLinkBasedAdReason(
            content = "read more at mp.weixin.qq.com/s/abc",
            blockZhihuAdPlatform = false,
            blockZhihuSchool = false,
            blockWeChatOfficialAccount = true,
        )
        assertEquals("微信公众号文章", reason)
    }

    @Test
    fun `getLinkBasedAdReason returns null for clean content`() {
        val reason = ContentFilterExtensions.getLinkBasedAdReason(
            content = "ordinary article content without ads",
            blockZhihuAdPlatform = true,
            blockZhihuSchool = true,
            blockWeChatOfficialAccount = true,
        )
        assertNull(reason)
    }

    @Test
    fun `getLinkBasedAdReason respects block flags`() {
        val reason = ContentFilterExtensions.getLinkBasedAdReason(
            content = "xg.zhihu.com ad here",
            blockZhihuAdPlatform = false,
            blockZhihuSchool = true,
            blockWeChatOfficialAccount = true,
        )
        assertNull(reason)
    }

    // ----- getFeedBasedAdReason tests -----

    @Test
    fun `getFeedBasedAdReason detects feed_advert type`() {
        val content = ContentFilterExtensions.FilterableContent(
            title = "",
            summary = null,
            content = null,
            authorName = null,
            authorId = null,
            contentId = "1",
            contentType = "answer",
            raw = com.github.zly2006.zhihu.data.DataHolder.DummyContent,
            feedJson = """{"type":"feed_advert"}""",
        )
        val reason = ContentFilterExtensions.getFeedBasedAdReason(content)
        assertEquals("ad feed", reason)
    }

    @Test
    fun `getFeedBasedAdReason detects ad json field`() {
        val content = ContentFilterExtensions.FilterableContent(
            title = "",
            summary = null,
            content = null,
            authorName = null,
            authorId = null,
            contentId = "1",
            contentType = "answer",
            raw = com.github.zly2006.zhihu.data.DataHolder.DummyContent,
            navDestinationJson = """{"ad":"true"}""",
        )
        val reason = ContentFilterExtensions.getFeedBasedAdReason(content)
        assertEquals("ad feed", reason)
    }

    @Test
    fun `getFeedBasedAdReason detects promotion extra`() {
        val content = ContentFilterExtensions.FilterableContent(
            title = "",
            summary = null,
            content = null,
            authorName = null,
            authorId = null,
            contentId = "1",
            contentType = "answer",
            raw = com.github.zly2006.zhihu.data.DataHolder.DummyContent,
            feedJson = "promotion_extra: true",
        )
        val reason = ContentFilterExtensions.getFeedBasedAdReason(content)
        assertEquals("promoted feed", reason)
    }

    @Test
    fun `getFeedBasedAdReason detects action card`() {
        val content = ContentFilterExtensions.FilterableContent(
            title = "",
            summary = null,
            content = null,
            authorName = null,
            authorId = null,
            contentId = "1",
            contentType = "answer",
            raw = com.github.zly2006.zhihu.data.DataHolder.DummyContent,
            navDestinationJson = "action_card data",
        )
        val reason = ContentFilterExtensions.getFeedBasedAdReason(content)
        assertEquals("promoted action card", reason)
    }

    @Test
    fun `getFeedBasedAdReason detects landing url`() {
        val content = ContentFilterExtensions.FilterableContent(
            title = "",
            summary = null,
            content = null,
            authorName = null,
            authorId = null,
            contentId = "1",
            contentType = "answer",
            raw = com.github.zly2006.zhihu.data.DataHolder.DummyContent,
            url = "landing_url=something",
        )
        val reason = ContentFilterExtensions.getFeedBasedAdReason(content)
        assertEquals("ad landing page", reason)
    }

    @Test
    fun `getFeedBasedAdReason detects Chinese ad keywords`() {
        val keywords = listOf("广告", "推广", "购买", "盐选", "课程", "训练营")
        keywords.forEach { keyword ->
            val content = ContentFilterExtensions.FilterableContent(
                title = "title with $keyword inside",
                summary = null,
                content = null,
                authorName = null,
                authorId = null,
                contentId = "1",
                contentType = "answer",
                raw = com.github.zly2006.zhihu.data.DataHolder.DummyContent,
            )
            val reason = ContentFilterExtensions.getFeedBasedAdReason(content)
            assertEquals("keyword=$keyword", "ad or promoted content", reason)
        }
    }

    @Test
    fun `getFeedBasedAdReason returns null for clean feed`() {
        val content = ContentFilterExtensions.FilterableContent(
            title = "Normal article about technology",
            summary = "Interesting summary",
            content = null,
            authorName = "John",
            authorId = "123",
            contentId = "1",
            contentType = "article",
            raw = com.github.zly2006.zhihu.data.DataHolder.DummyContent,
        )
        val reason = ContentFilterExtensions.getFeedBasedAdReason(content)
        assertNull(reason)
    }

    @Test
    fun `getFeedBasedAdReason is case insensitive`() {
        val content = ContentFilterExtensions.FilterableContent(
            title = "ACTION_CARD with mixed case",
            summary = null,
            content = null,
            authorName = null,
            authorId = null,
            contentId = "1",
            contentType = "answer",
            raw = com.github.zly2006.zhihu.data.DataHolder.DummyContent,
        )
        val reason = ContentFilterExtensions.getFeedBasedAdReason(content)
        assertEquals("promoted action card", reason)
    }

    // ----- checkForAd tests -----

    @Test
    fun `checkForAd returns false for DummyContent`() {
        val content = ContentFilterExtensions.FilterableContent(
            title = "test",
            summary = null,
            content = null,
            authorName = null,
            authorId = null,
            contentId = "1",
            contentType = "answer",
            raw = com.github.zly2006.zhihu.data.DataHolder.DummyContent,
        )
        assertTrue(!ContentFilterExtensions.checkForAd(content))
    }
}
