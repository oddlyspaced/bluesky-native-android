package io.nativestarter.showcase.bluesky.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import io.nativestarter.showcase.bluesky.testing.Fixtures

/** Decodes JSON captured with curl from the seeded local dev network (see android/README.md). */
class FixtureDecodingTest {

    @Test
    fun createSession() {
        val s = Fixtures.decode<SessionResponse>("createSession.json")
        assertEquals("alice.test", s.handle)
        assertTrue(s.did.startsWith("did:plc:"))
    }

    @Test
    fun timeline() {
        val feed = Fixtures.decode<FeedResponse>("getTimeline.json").feed
        assertEquals(
            listOf("carla.test", "bob.test", "carla.test", "bob.test", "alice.test"),
            feed.map { it.post.author.handle },
        )
        val reply = feed.first()
        assertEquals("Thread reply", reply.post.record.text)
        assertNotNull(reply.post.record.reply)
        assertNotNull(reply.reply?.parent)
        assertEquals("Bob", feed[1].post.author.displayName)
        assertEquals(1, feed[1].post.replyCount)
        assertNull(feed[1].post.viewer.like)
    }

    @Test
    fun thread() {
        val thread = Fixtures.decode<ThreadResponse>("getPostThread.json").thread
        assertEquals("Thread root", thread.post?.record?.text)
        assertEquals(1, thread.replies.size)
        assertEquals("carla.test", thread.replies.single().post?.author?.handle)
    }

    @Test
    fun profile() {
        val p = Fixtures.decode<ProfileViewDetailed>("getProfile.json")
        assertEquals("Alice", p.displayName)
        assertEquals("Test user 1", p.description)
        assertEquals(2, p.followersCount)
        assertEquals(2, p.followsCount)
        assertEquals(1, p.postsCount)
        assertNull(p.banner)
    }

    @Test
    fun authorFeed() {
        val feed = Fixtures.decode<FeedResponse>("getAuthorFeed.json").feed
        assertEquals(listOf("Post"), feed.map { it.post.record.text })
    }

    @Test
    fun notificationsCountLikeRn() {
        val page = Fixtures.decode<NotificationsResponse>("listNotifications.json")
        assertEquals(2, page.notifications.size)
        assertEquals(1, page.notifications.count { !it.isRead })
        assertFalse(page.notifications.all { it.isRead })
    }
}
