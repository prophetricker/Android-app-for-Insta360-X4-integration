package com.omniveye.app.cloud

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubModelsTest {

    @Test
    fun parsesGitHubContentResponse() {
        val json = """
            {
              "content": {
                "name": "IMG_1.jpg",
                "path": "OmniEye/IMG_1.jpg",
                "sha": "abc123",
                "download_url": "https://raw.githubusercontent.com/o/r/b/OmniEye/IMG_1.jpg"
              },
              "commit": { "sha": "commit123" }
            }
        """.trimIndent()

        val response = Gson().fromJson(json, GitHubResponse::class.java)

        assertEquals("abc123", response.content?.sha)
        assertEquals("https://raw.githubusercontent.com/o/r/b/OmniEye/IMG_1.jpg", response.content?.downloadUrl)
        assertEquals("commit123", response.commit?.sha)
    }
}
