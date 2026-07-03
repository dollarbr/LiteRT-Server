package com.litert.server.hf

import org.junit.Assert.assertEquals
import org.junit.Test

class HfJsonTest {

    @Test
    fun `parses model search response ignoring unknown fields`() {
        val body = """
            [
              {"id":"litert-community/gemma-4-E2B-it-litert-lm","downloads":1234,"likes":56,"private":false,"tags":["litert-lm"]},
              {"id":"litert-community/gemma-4-E4B-it-litert-lm"}
            ]
        """.trimIndent()
        val models = HfJson.parseModels(body)
        assertEquals(2, models.size)
        assertEquals("litert-community/gemma-4-E2B-it-litert-lm", models[0].id)
        assertEquals(1234L, models[0].downloads)
        assertEquals(0L, models[1].downloads)
    }

    @Test
    fun `extracts only litertlm files from model detail`() {
        val body = """
            {
              "id": "litert-community/gemma-4-E2B-it-litert-lm",
              "siblings": [
                {"rfilename": "README.md", "size": 2048},
                {"rfilename": "gemma-4-E2B-it.litertlm", "size": 2770000000},
                {"rfilename": ".gitattributes"}
              ]
            }
        """.trimIndent()
        val detail = HfJson.parseModelDetail(body)
        val files = HfJson.litertlmFiles(detail)
        assertEquals(1, files.size)
        assertEquals("gemma-4-E2B-it.litertlm", files[0].rfilename)
        assertEquals(2_770_000_000L, files[0].size)
    }

    @Test
    fun `includes litertlm sibling with null size`() {
        val body = """
            {
              "id": "litert-community/gemma-4-E2B-it-litert-lm",
              "siblings": [
                {"rfilename": "gemma-4-E2B-it.litertlm", "size": null}
              ]
            }
        """.trimIndent()
        val detail = HfJson.parseModelDetail(body)
        val files = HfJson.litertlmFiles(detail)
        assertEquals(1, files.size)
        assertEquals("gemma-4-E2B-it.litertlm", files[0].rfilename)
        assertEquals(null, files[0].size)
    }

    @Test
    fun `is case-sensitive and excludes uppercase extension`() {
        // HF filenames are lowercase by convention; endsWith(".litertlm") is
        // intentionally case-sensitive and will NOT match an uppercase extension.
        val body = """
            {
              "id": "litert-community/gemma-4-E2B-it-litert-lm",
              "siblings": [
                {"rfilename": "MODEL.LITERTLM", "size": 2770000000}
              ]
            }
        """.trimIndent()
        val detail = HfJson.parseModelDetail(body)
        val files = HfJson.litertlmFiles(detail)
        assertEquals(0, files.size)
    }
}
