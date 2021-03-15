package eu.kanade.tachiyomi.extension.en.asurascans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
// import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class AsuraScans : ParsedHttpSource() {

    override val name = "AsuraScans"

    override val baseUrl = "http://www.asurascans.com"

    override val lang = "en"

    override val supportsLatest = true

    private val dateFormat: SimpleDateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/manga/?page=$page&order=update", headers)
    }

    override fun popularMangaSelector() = "div.listupd div.bs div.bsx" // todo might be .listupd > .bs > .bsx

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("a").let {
                setUrlWithoutDomain(it.attr("href"))
                title = it.attr("title")
            }
            thumbnail_url = element.select("img").attr("abs:src") // todo might jsut be src, might just be img
        }
    }

    override fun popularMangaNextPageSelector() = "div.hpage" // todo maybe just .hpage

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/manga/?page=$page&order=update", headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/page/$page/?s=$query")
//        val filterList = if (filters.isEmpty()) getFilterList() else filters

        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Details

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$baseUrl/${manga.url}")
    }

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            title = document.select("h1.entry-title").first().text()
            genre = document.select("span.mgen > a").joinToString(", ") { it.text() }
            description = document.select("div.infox > div.wd-full > div.entry-content.entry-content-single > p").text()
            thumbnail_url = document.select("div.thumbook > div.thumb > img").first().attr("abs:src")
            status = document.select("div.tsinfo > div.imptdt > i").text().let {
                if (it.contains("COMPLETED")) SManga.COMPLETED else SManga.ONGOING
            }
        }
    }

    override fun chapterListSelector() = "div.bixbox.bxcl.epcheck > div.eplister > ul > li"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            name = element.select("span.chapternum").text()
            date_upload = parseChapterDate(element.select("span.chapterdate").text())
            setUrlWithoutDomain(element.select("a").attr("href"))
        }
    }

    // Pages
    private val pageListSelector = "div.rdminimal > p > img"

    // todo check this
    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        document.select(pageListSelector)
            .filterNot { it.attr("src").isNullOrEmpty() }
            .mapIndexed { i, element -> pages.add(Page(i, "", element.attr("abs:src"))) }

        return pages
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    // Filters
    override fun getFilterList() = FilterList(
        GenericFilter("Status", getMangaStatus()),
        GenericFilter("Type", getMangaTypes()),
        GenericFilter("Genre", getMangaGenres())
    )

    private class GenericFilter(name: String, filters: Array<String>) : Filter.Select<String>(name, filters)

    private fun getMangaStatus() = arrayOf(
        "Completed",
        "Ongoing",
        "Hiatus"
    )
    private fun getMangaTypes() = arrayOf(
        "Manga",
        "Manhwa",
        "Manhua",
        "Comic"
    )

    private fun getMangaGenres() = arrayOf(
        "Action",
        "Adaptation",
        "Adult",
        "Adventure",
        "Comedy",
        "Discord",
        "Drama",
        "Dungeons",
        "Ecchi",
        "Fantasy",
        "Game",
        "Harem",
        "Hero",
        "Historical",
        "Isekai",
        "Josei",
        "Loli",
        "Magic",
        "Martial Arts",
        "Mature",
        "Mecha",
        "Monsters",
        "Mystery",
        "Post-Apocalyptic",
        "Psychological",
        "Rebirth",
        "Reincarnation",
        "Romance",
        "School Life",
        "Sci-fi",
        "Seinen",
        "Shoujo",
        "Shounen",
        "Slice of Life",
        "Super Hero",
        "Superhero",
        "Supernatural",
        "Survival",
        "Time Travel",
        "Tragedy",
        "Video Game",
        "Video Games",
        "Virtual Game",
        "Virtual Reality",
        "Wuxia"
    )

    private fun parseChapterDate(date: String?): Long {
        date ?: return 0

        fun SimpleDateFormat.tryParse(string: String): Long {
            return try {
                parse(string)?.time ?: 0
            } catch (_: ParseException) {
                0
            }
        }

        return when {
            date.contains(Regex("""\d(st|nd|rd|th)""")) -> {
                // Clean date (e.g. 5th December 2019 to 5 December 2019) before parsing it
                date.split(" ").map {
                    if (it.contains(Regex("""\d\D\D"""))) {
                        it.replace(Regex("""\D"""), "")
                    } else {
                        it
                    }
                }
                    .let { dateFormat.tryParse(it.joinToString(" ")) }
            }
            else -> dateFormat.tryParse(date)
        }
    }
}
