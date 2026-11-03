package app.pleromastisk.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelsTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    private fun status(extra: String) = """
        {"id":"1","content":"<p>hei</p>","favourites_count":3,"reblogs_count":2,"replies_count":0,
         "account":{"id":"a","acct":"gaute","display_name":"Gaute"}$extra}
    """.trimIndent()

    @Test fun leserProfilfelt() {
        val a = json.decodeFromString<Account>(
            """{"id":"1","acct":"gaute","fields":[
                {"name":"Lenketre","value":"<a href=\"https://gauteweb.net/\">gauteweb.net</a>","verified_at":"2026-08-01T10:24:43.000Z"},
                {"name":"Min kode","value":"x","verified_at":null}]}""",
        )
        assertEquals(2, a.fields.size)
        assertEquals("Lenketre", a.fields[0].name)
        assertEquals("2026-08-01T10:24:43.000Z", a.fields[0].verifiedAt)
        assertNull(a.fields[1].verifiedAt)
    }

    @Test fun leserSpråkPåInnlegg() {
        assertEquals("nb", json.decodeFromString<Status>(status(""","language":"nb"""")).language)
        assertNull(json.decodeFromString<Status>(status(""","language":null""")).language)
    }

    @Test fun leserRoller() {
        val m = json.decodeFromString<Account>(
            """{"id":"1","roles":[{"id":"3","name":"Owner","color":"#ff3838"}]}""",
        )
        assertEquals("Owner", m.roles.single().name)
        assertEquals("#ff3838", m.roles.single().color)
        val p = json.decodeFromString<Account>(
            """{"id":"1","pleroma":{"is_admin":true,"is_moderator":null,"relationship":{}}}""",
        )
        assertEquals(true, p.pleroma?.isAdmin)
        assertEquals(false, p.pleroma?.isModerator)
    }

    @Test fun svarOgNevnelserTilMeg() {
        val mention = json.decodeFromString<Status>(status(""","mentions":[{"id":"me","acct":"meg"}]"""))
        assertEquals(true, mention.addresses("me"))
        assertEquals(false, mention.addresses("andre"))
        assertEquals(false, mention.addresses(null))
        val reply = json.decodeFromString<Status>(status(""","in_reply_to_account_id":"me""""))
        assertEquals(true, reply.addresses("me"))
        assertEquals(false, json.decodeFromString<Status>(status("")).addresses("me"))
    }

    @Test fun leserBotFlagg() {
        assertEquals(true, json.decodeFromString<Account>("""{"id":"1","bot":true}""").bot)
        assertEquals(false, json.decodeFromString<Account>("""{"id":"1"}""").bot)
    }

    @Test fun kontoUtenFeltGirTomListe() {
        assertEquals(0, json.decodeFromString<Account>("""{"id":"1"}""").fields.size)
    }

    @Test fun leserAppNavn() {
        val s = json.decodeFromString<Status>(status(""","application":{"name":"Pleromastisk","website":"https://example.org"}"""))
        assertEquals("Pleromastisk", s.application?.name)
        assertEquals("Feditastic", s.application?.displayName)
        assertEquals("Tusky", AppInfo("Tusky").displayName)
        assertEquals(3, s.favouritesCount)
        assertEquals(2, s.reblogsCount)
    }

    @Test fun manglendeEllerNullApp() {
        assertNull(json.decodeFromString<Status>(status("")).application)
        assertNull(json.decodeFromString<Status>(status(""","application":null""")).application)
    }

    @Test fun appUtenWebsiteOgNullNavn() {
        val s = json.decodeFromString<Status>(status(""","application":{"name":null,"website":null}"""))
        assertEquals("", s.application?.name)
    }

    @Test fun leserHelsesjekk() {
        val h = json.decodeFromString<Healthcheck>(
            """{"active":1,"idle":9,"pool_size":null,"memory_used":255.41,"healthy":true,
                "job_queue_stats":{"processed_jobs":2316,"queues":{
                  "federator_incoming":{"success":32,"failure":191,"processed_jobs":223}}}}""",
        )
        assertEquals(true, h.healthy)
        assertEquals(9, h.idle)
        assertNull(h.poolSize)
        assertEquals(2316L, h.jobQueueStats?.processedJobs)
        assertEquals(191L, h.jobQueueStats?.queues?.get("federator_incoming")?.failure)
        // Slått av på tjeneren: svaret er bare {}
        assertNull(json.decodeFromString<Healthcheck>("{}").healthy)
    }
}
