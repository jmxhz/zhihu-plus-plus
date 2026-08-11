
package com.github.zly2006.zhihu.viewmodel.feed

import com.github.zly2006.zhihu.data.CommonFeed
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.Person
import com.github.zly2006.zhihu.data.QuestionFeedCard
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.resolveContent
import com.github.zly2006.zhihu.viewmodel.za.extractQuestionIdFromRoute
import com.github.zly2006.zhihu.viewmodel.za.parseMobileHomeFeedDisplayItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeFeedDeduplicationExtraTest {
    @Test
    fun mobileFeedSameAnswerDifferentRouteFormsUseSameKey() {
        val viaQuestionRoute = mobileCard("https%3A%2F%2Fwww.zhihu.com%2Fquestion%2F10%2Fanswer%2F42")
        val viaAppScheme = mobileCard("zhihu%3A%2F%2Fanswers%2F42")
        val viaAppView = mobileCard("https%3A%2F%2Fwww.zhihu.com%2Fappview%2Fanswer%2F42")

        val itemA = parseMobileHomeFeedDisplayItem(viaQuestionRoute)
        val itemB = parseMobileHomeFeedDisplayItem(viaAppScheme)
        val itemC = parseMobileHomeFeedDisplayItem(viaAppView)

        assertEquals("answer:42", itemA?.homeFeedContentKey)
        assertEquals(itemA?.homeFeedContentKey, itemB?.homeFeedContentKey)
        assertEquals(itemA?.homeFeedContentKey, itemC?.homeFeedContentKey)
    }

    @Test
    fun webAnswerStandaloneAndInsideGroupFeedUseSameKey() {
        val standalone = webAnswerItem(answerId = 42L, questionId = 10L)
        val inGroup = webAnswerItem(answerId = 42L, questionId = 10L)

        assertEquals(standalone.homeFeedContentKey, inGroup.homeFeedContentKey)
        assertEquals("answer:42", standalone.homeFeedContentKey)
    }

    @Test
    fun questionCardAndAnswerCardKeepDifferentKeysEvenWithSameTitle() {
        val questionCard = questionCardItem(questionId = 10L)
        val answerCard = webAnswerItem(answerId = 42L, questionId = 10L)

        assertEquals("question:10", questionCard.homeFeedContentKey)
        assertEquals("answer:42", answerCard.homeFeedContentKey)
        // 同一问题的“问题卡”和“回答卡”key 不同；若不去重会看到同标题重复卡片。
        assertNotEquals(questionCard.homeFeedContentKey, answerCard.homeFeedContentKey)
        assertTrue(questionCard.title == answerCard.title)
    }

    @Test
    fun questionCardDroppedWhenSameQuestionHasVisibleAnswer() {
        val questionCard = questionCardItem(questionId = 10L)
        val answerCard = webAnswerItem(answerId = 42L, questionId = 10L)
        val otherQuestionCard = questionCardItem(questionId = 99L)

        val deduped = dedupeHomeFeedQuestionCards(
            candidates = listOf(questionCard, otherQuestionCard),
            alreadyVisible = listOf(answerCard),
        )

        assertEquals(listOf(otherQuestionCard), deduped)
    }

    @Test
    fun questionCardKeptWhenNoAnswerForItsQuestionIsVisible() {
        val questionCard = questionCardItem(questionId = 10L)
        val answerCard = webAnswerItem(answerId = 42L, questionId = 20L)

        val deduped = dedupeHomeFeedQuestionCards(
            candidates = listOf(questionCard),
            alreadyVisible = listOf(answerCard),
        )

        assertEquals(listOf(questionCard), deduped)
    }

    @Test
    fun mobileAnswerCardCarriesQuestionId() {
        val card = mobileCard("https%3A%2F%2Fwww.zhihu.com%2Fquestion%2F10%2Fanswer%2F42")
        val item = parseMobileHomeFeedDisplayItem(card)

        assertEquals(10L, item?.questionId)
        assertEquals("answer:42", item?.homeFeedContentKey)
    }

    @Test
    fun zhihuSchemeAnswerRouteResolvesToAnswerNotQuestion() {
        val fromPlural = resolveContent("zhihu://questions/10/answers/42") as Article
        val fromSingular = resolveContent("zhihu://question/10/answer/42") as Article
        assertEquals(ArticleType.Answer, fromPlural.type)
        assertEquals(42L, fromPlural.id)
        assertEquals(ArticleType.Answer, fromSingular.type)
        assertEquals(42L, fromSingular.id)
        assertEquals(Question(10L), resolveContent("zhihu://questions/10"))
        assertEquals(10L, extractQuestionIdFromRoute("https://www.zhihu.com/question/10/answer/42"))
        assertEquals(10L, extractQuestionIdFromRoute("zhihu://questions/10/answers/42"))
        assertNull(extractQuestionIdFromRoute("zhihu://answers/42"))
    }

    private fun questionCardItem(questionId: Long): FeedDisplayItem = FeedDisplayItem(
        title = "如何看待xxx？",
        summary = "问题摘要",
        details = "问题",
        questionId = questionId,
        feed = QuestionFeedCard(
            position = 0,
            target = Feed.QuestionTarget(
                id = questionId,
                url = "",
                type = "question",
                _title = "如何看待xxx？",
            ),
            cursor = "",
            targetType = "question",
        ),
    )

    private fun webAnswerItem(answerId: Long, questionId: Long): FeedDisplayItem = FeedDisplayItem(
        title = "如何看待xxx？",
        summary = null,
        details = "",
        questionId = questionId,
        feed = CommonFeed(
            target = Feed.AnswerTarget(
                id = answerId,
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
                    id = questionId,
                    url = "",
                    type = "question",
                    _title = "question",
                ),
            ),
        ),
    )

    private fun mobileCard(encodedRoute: String): JsonObject = Json
        .parseToJsonElement(
            """
            {
              "type": "ComponentCard",
              "action": { "parameter": "route_url=$encodedRoute" },
              "children": [
                { "id": "Text", "type": "Text", "style": "", "text": "如何看待xxx？" },
                { "id": "text_pin_summary", "type": "Text", "style": "", "text": "摘要" },
                {
                  "style": "RecommendAuthorLine",
                  "type": "Line",
                  "elements": [
                    { "style": "Avatar_default", "type": "Avatar", "image": { "url": "https://avatar" } },
                    { "type": "Text", "text": "作者名" }
                  ]
                },
                {
                  "type": "Line",
                  "style": "LineFooterReaction_feed_v3",
                  "elements": [
                    { "reaction": "Vote", "type": "Vote", "count": 10 },
                    { "reaction": "Comment", "type": "Comment", "count": 2 },
                    { "reaction": "Collect", "type": "Collect", "count": 1 }
                  ]
                }
              ]
            }
            """.trimIndent(),
        ).jsonObject
}
