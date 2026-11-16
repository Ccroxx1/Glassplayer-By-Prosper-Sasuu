package com.example

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InnertubePlaylistParserTest {

    @Test
    fun findContinuationToken_inContinuationItemRenderer_extractsToken() {
        val json = JSONObject("""
            {
              "onResponseReceivedActions": [
                {
                  "appendContinuationItemsAction": {
                    "continuationItems": [
                      {
                        "continuationItemRenderer": {
                          "continuationEndpoint": {
                            "continuationCommand": {
                              "token": "4qmFsgI0EAE_valid_continuation_token_123"
                            }
                          }
                        }
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent())

        val token = OnlineMusicService.findContinuationToken(json)
        assertNotNull(token)
        assertEquals("4qmFsgI0EAE_valid_continuation_token_123", token)
    }

    @Test
    fun findContinuationToken_inNextContinuationData_extractsToken() {
        val json = JSONObject("""
            {
              "nextContinuationData": {
                "continuation": "token_next_continuation_data_987654321"
              }
            }
        """.trimIndent())

        val token = OnlineMusicService.findContinuationToken(json)
        assertNotNull(token)
        assertEquals("token_next_continuation_data_987654321", token)
    }

    @Test
    fun findContinuationToken_invalidShortToken_returnsNull() {
        val json = JSONObject("""
            {
              "continuationCommand": {
                "token": "short"
              }
            }
        """.trimIndent())

        val token = OnlineMusicService.findContinuationToken(json)
        assertNull(token)
    }
}
