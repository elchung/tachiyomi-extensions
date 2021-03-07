package eu.kanade.tachiyomi.extension.en.asurascans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException

class AsuraScans : ParsedHttpSource() {

    override val name = "AsuraScans"

    override val baseUrl = "http://www.asurascans.com"

    override val lang = "en"

    override val supportsLatest = true

    // Clients

    private lateinit var phpSessId: String

    private val searchClient = OkHttpClient().newBuilder()
        .followRedirects(false)
        .cookieJar(CookieJar.NO_COOKIES)
        .build()

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .followRedirects(false)
        .addInterceptor { chain ->
            val originalRequest = chain.request()
            when {
                originalRequest.url().toString() == searchUrl -> {
                    phpSessId = searchClient.newCall(originalRequest).execute()
                        .headers("Set-Cookie")
                        .firstOrNull { it.contains("PHPSESSID") }
                        ?.toString()
                        ?.substringBefore(";")
                        ?: throw IOException("PHPSESSID missing")

                    val newHeaders = headersBuilder()
                        .add("Cookie", phpSessId)

                    val contentLength = originalRequest.body()!!.contentLength()

                    searchClient.newCall(GET("$baseUrl/${if (contentLength > 8000) "result" else "search"}/1", newHeaders.build())).execute()
                }
                originalRequest.url().toString().contains(nextSearchPageUrlRegex) -> {
                    searchClient.newCall(originalRequest).execute()
                }
                else -> chain.proceed(originalRequest)
            }
        }
        .build()

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/manga/?page=$page&order=update", headers)
    }

    override fun popularMangaSelector() = "div.listupd div.bs div.bsx"  // todo might be .listupd > .bs > .bsx

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("a").let {
                setUrlWithoutDomain(it.attr("href"))
                title = it.attr("title")
            }
            thumbnail_url = element.select("img").attr("abs:src")  //todo might jsut be src, might just be img
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
        val filterList = if (filters.isEmpty()) getFilterList() else filters

        return GET(url, headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Details

    override fun mangaDetailsRequest(manga: SManga): Request {  // todo need to check if manga.url is /comics/name or just /name
        return GET("$baseUrl/${manga.url}")
    }

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            title = select("h1.entry-title").first().text()
            genre = select("span.mgen > a").joinToString(", ") { it.text() }
            description = select("div.infox > div.wd-full > div.entry-content.entry-content-single > p").text()
            thumbnail_url = select("div.thumbook > div.thumb > img").first().attr("abs:src")
            status = select("div.tsinfo > div.imptdt > i").text().let {
                if (it.contains("COMPLETED")) status = SManga.COMPLETED else status = SMANGA.ONGOING
            }
        }
    }

    // Chapters
    //do i need chapterListParse??
    override fun chapterListSelector() = "div.bixbox.bxcl.epcheck > div.eplister > ul > li"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a")

        return SChapter.create().apply {
            name = element.select("span.chapternum").text()
            setUrlWithoutDomain(element.attr("href"))
        }
    }

    // Pages

    //todo check this
    override fun pageListParse(document: Document): List<Page> {
        return document.select("img.alignnone.size-full").mapIndexed { i, element ->
            Page(i, element.attr("abs:src"))
        }
    }

    //todo check this
    override fun imageUrlParse(document: Document): String {
        return document.select("img.alignnone.size-full").attr("abs:src")
    }

    // Filters

    override fun getFilterList(): FilterList(
        GenericFilter("Status", getMangaStatus()),
        GenericFilter("Type", getMangaTypes()),
        GenericFilter("Genre", getMangaGenre())
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

    private fun getGenreList() = arrayOf(
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
}
