package com.github.zly2006.zhihu.navigation

import com.github.zly2006.zhihu.viewmodel.ArticleViewModel.CachedAnswerContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class QuestionAnswerNavigatorTest {
    private fun makeArticle(id: Long): Article = Article(
        id = id,
        type = ArticleType.Answer,
        title = "Test Answer $id",
        authorName = "Author $id",
        authorBio = "Bio $id",
        avatarSrc = "https://example.com/avatar/$id",
    )

    private fun makeCachedAnswerContent(id: Long): CachedAnswerContent = CachedAnswerContent(
        article = makeArticle(id),
        title = "Test Title",
        authorName = "Author",
        authorBio = "Bio",
        authorAvatarUrl = "",
        content = "<p>Content</p>",
        voteUpCount = 10,
        commentCount = 5,
        sourceLabel = "this question",
    )

    @Test
    fun previousAnswerPreviewWithInitialPreviousItems() {
        val prevItems = listOf(makeArticle(100), makeArticle(200))
        val nav = QuestionAnswerNavigator(
            questionId = 123,
            initialPreviousItems = prevItems,
        )
        val preview = nav.previousAnswerPreview
        assertNotNull(preview)
        assertEquals(100L, preview!!.article.id)
    }

    @Test
    fun previousAnswerPreviewReturnsNullWhenQueueEmpty() {
        val nav = QuestionAnswerNavigator(questionId = 123)
        assertNull(nav.previousAnswerPreview)
    }

    @Test
    fun constructorFiltersNonAnswerArticles() {
        val article = makeArticle(1)
        val nonAnswer = Article(
            id = 2,
            type = ArticleType.Article,
            title = "Not an answer",
            authorName = "Author",
            authorBio = "",
            avatarSrc = "",
        )
        val nav = QuestionAnswerNavigator(
            questionId = 123,
            initialNextItems = listOf(article, nonAnswer),
            initialPreviousItems = listOf(nonAnswer),
        )
        assertNull(nav.previousAnswerPreview)
    }

    @Test
    fun constructorKeepsInitialNextItemsInSourceOrder() {
        val nav = QuestionAnswerNavigator(
            questionId = 123,
            initialNextItems = listOf(makeArticle(30), makeArticle(20), makeArticle(40)),
        )

        assertEquals(listOf(30L, 20L, 40L), nav.queuedNextAnswerIdsForTesting())
    }

    @Test
    fun pushAnswerAddsToHistoryAndTruncatesForwardBranch() {
        val nav = QuestionAnswerNavigator(questionId = 123)
        val c1 = makeCachedAnswerContent(1)
        val c2 = makeCachedAnswerContent(2)
        val c3 = makeCachedAnswerContent(3)

        nav.pushAnswer(c1)
        assertEquals(0, nav.currentAnswerIndex)
        assertEquals(1, nav.answerHistory.size)

        nav.pushAnswer(c2)
        assertEquals(1, nav.currentAnswerIndex)
        assertEquals(2, nav.answerHistory.size)

        nav.pushAnswer(c3)
        assertEquals(2, nav.currentAnswerIndex)
        assertEquals(3, nav.answerHistory.size)

        nav.goToPrevious()
        assertEquals(1, nav.currentAnswerIndex)
        val c2b = makeCachedAnswerContent(22)
        nav.pushAnswer(c2b)
        assertEquals(2, nav.currentAnswerIndex)
        assertEquals(3, nav.answerHistory.size)
        assertEquals(22L, nav.answerHistory[2].article.id)
    }

    @Test
    fun goToPreviousReturnsNullAtHistoryStart() {
        val nav = QuestionAnswerNavigator(questionId = 123)
        nav.pushAnswer(makeCachedAnswerContent(1))
        assertNull(nav.goToPrevious())
    }

    @Test
    fun goToNextReturnsNullAtHistoryEnd() {
        val nav = QuestionAnswerNavigator(questionId = 123)
        nav.pushAnswer(makeCachedAnswerContent(1))
        assertNull(nav.goToNext())
    }

    @Test
    fun historyBackAndForwardWorksCorrectly() {
        val nav = QuestionAnswerNavigator(questionId = 123)
        nav.pushAnswer(makeCachedAnswerContent(1))
        nav.pushAnswer(makeCachedAnswerContent(2))
        nav.pushAnswer(makeCachedAnswerContent(3))

        assertEquals(2, nav.currentAnswerIndex)

        val prev = nav.goToPrevious()
        assertNotNull(prev)
        assertEquals(2L, prev!!.article.id)
        assertEquals(1, nav.currentAnswerIndex)

        val morePrev = nav.goToPrevious()
        assertNotNull(morePrev)
        assertEquals(1L, morePrev!!.article.id)
        assertEquals(0, nav.currentAnswerIndex)

        val end = nav.goToPrevious()
        assertNull(end)

        val next = nav.goToNext()
        assertNotNull(next)
        assertEquals(2L, next!!.article.id)
        assertEquals(1, nav.currentAnswerIndex)

        val moreNext = nav.goToNext()
        assertNotNull(moreNext)
        assertEquals(3L, moreNext!!.article.id)
        assertEquals(2, nav.currentAnswerIndex)
    }

    @Test
    fun previousAndNextAnswerReflectQueuePositions() {
        val prevItems = listOf(makeArticle(10))
        val nextItems = listOf(makeArticle(20))
        val nav = QuestionAnswerNavigator(
            questionId = 123,
            initialNextItems = nextItems,
            initialPreviousItems = prevItems,
        )
        nav.pushAnswer(makeCachedAnswerContent(15))

        assertNotNull(nav.previousAnswer)
        assertNull(nav.nextAnswer)
    }

    @Test
    fun pushAnswerWithSameArticleUpdatesContentWithoutChangingIndex() {
        val nav = QuestionAnswerNavigator(questionId = 123)
        val c1 = makeCachedAnswerContent(1)
        val c2 = makeCachedAnswerContent(2)
        nav.pushAnswer(c1)
        nav.pushAnswer(c2)
        assertEquals(1, nav.currentAnswerIndex)

        val updated = c2.copy(content = "<p>Updated Content</p>")
        nav.pushAnswer(updated)
        assertEquals(1, nav.currentAnswerIndex)
        assertEquals("<p>Updated Content</p>", nav.answerHistory[1].content)
    }

    @Test
    fun forwardHistoryDoesNotRepeatAfterBranchIsTruncated() {
        val nav = QuestionAnswerNavigator(questionId = 123)
        nav.pushAnswer(makeCachedAnswerContent(1))
        nav.pushAnswer(makeCachedAnswerContent(2))
        nav.pushAnswer(makeCachedAnswerContent(3))

        nav.goToPrevious()
        nav.pushAnswer(makeCachedAnswerContent(4))

        assertEquals(listOf(1L, 2L, 4L), nav.answerHistory.map { it.article.id })
        assertNull(nav.goToNext())
    }
}
