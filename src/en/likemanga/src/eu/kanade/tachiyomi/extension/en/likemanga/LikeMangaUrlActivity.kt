package eu.kanade.tachiyomi.extension.en.likemanga

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class LikeMangaUrlActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val slug =
            intent?.data?.lastPathSegment ?: return

        val mainIntent = Intent().apply {
            action = "eu.kanade.tachiyomi.SEARCH"
            putExtra(
                "query",
                LikeManga.URL_SEARCH_PREFIX + slug,
            )
            putExtra("filter", packageName)
        }

        startActivity(mainIntent)
        finish()
    }
}
