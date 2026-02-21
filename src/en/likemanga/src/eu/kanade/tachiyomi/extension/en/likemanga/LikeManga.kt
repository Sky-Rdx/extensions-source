package eu.kanade.tachiyomi.extension.en.likemanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class LikeManga : ParsedHttpSource() {

    override val name = "LikeManga"
    override val baseUrl = "https://likemanga.org"
    override val lang = "en"
    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(1, 2)
        .build()

    override fun headersBuilder() =
        super.headersBuilder().add("Referer", "$baseUrl/")

    // ================= POPULAR =================

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/manga/?m_orderby=views&paged=$page", headers)

    override fun popularMangaSelector() =
        "div.page-item-detail"

    override fun popularMangaFromElement(element: Element) =
        mangaFromElement(element)

    override fun popularMangaNextPageSelector() =
        "div.pagination a.next"

    // ================= LATEST =================

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/manga/?m_orderby=latest&paged=$page", headers)

    override fun latestUpdatesSelector() =
        "div.page-item-detail"

    override fun latestUpdatesFromElement(element: Element) =
        mangaFromElement(element)

    override fun latestUpdatesNextPageSelector() =
        "div.pagination a.next"

    // ================= SEARCH =================

    override fun searchMangaRequest(
        page: Int,
        query: String,
        filters: FilterList,
    ): Request {
        val url = if (query.isNotBlank()) {
            "$baseUrl/page/$page/?s=${query.trim()}&post_type=wp-manga"
        } else {
            "$baseUrl/manga/?paged=$page"
        }
        return GET(url, headers)
    }

    override fun searchMangaSelector() =
        "div.page-item-detail"

    override fun searchMangaFromElement(element: Element) =
        mangaFromElement(element)

    override fun searchMangaNextPageSelector() =
        "div.pagination a.next"

    // ================= CARD PARSER =================

    private fun mangaFromElement(element: Element) =
        SManga.create().apply {
            val link = element.selectFirst("h3 a")!!
            setUrlWithoutDomain(link.attr("href"))
            title = link.text()
            thumbnail_url =
                element.selectFirst("img")?.absUrl("src") ?: ""
        }

    // ================= DETAILS =================

    override fun mangaDetailsParse(document: Document) =
        SManga.create().apply {
            title =
                document.selectFirst(".post-title h1")?.text() ?: ""
            thumbnail_url =
                document.selectFirst(".summary_image img")
                    ?.absUrl("src") ?: ""
            description =
                document.selectFirst(".summary__content")
                    ?.text() ?: ""
            genre =
                document.select(".genres-content a")
                    .joinToString { it.text() }
            author =
                document.selectFirst(".author-content a")
                    ?.text() ?: ""
            status = parseStatus(
                document.selectFirst(
                    ".post-status .summary-content",
                )?.text(),
            )
        }

    private fun parseStatus(status: String?): Int =
        when {
            status == null -> SManga.UNKNOWN
            status.contains("Completed", true) ->
                SManga.COMPLETED
            status.contains("OnGoing", true) ->
                SManga.ONGOING
            else -> SManga.UNKNOWN
        }

    // ================= CHAPTERS =================

    override fun chapterListSelector() =
        "li.wp-manga-chapter"

    override fun chapterFromElement(element: Element) =
        SChapter.create().apply {
            val link = element.selectFirst("a")!!
            setUrlWithoutDomain(link.attr("href"))
            name = link.text()
            date_upload = parseDate(
                element.selectFirst(".chapter-release-date")
                    ?.text(),
            )
        }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.use { it.asJsoup() }
        return document.select(chapterListSelector())
            .map(::chapterFromElement)
    }

    private fun parseDate(date: String?): Long =
        try {
            SimpleDateFormat(
                "MMMM dd, yyyy",
                Locale.ENGLISH,
            ).parse(date ?: "")?.time ?: 0L
        } catch (_: Exception) {
            0L
        }

    // ================= PAGES =================

    override fun pageListParse(document: Document): List<Page> =
        document.select("div.reading-content img")
            .mapIndexed { index, img ->
                Page(index, "", img.absUrl("src"))
            }

    override fun imageUrlParse(document: Document): String =
        throw UnsupportedOperationException()

    companion object {
        const val URL_SEARCH_PREFIX = "slug:"
    }
}
