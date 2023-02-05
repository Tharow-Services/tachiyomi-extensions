package eu.kanade.tachiyomi.extension.all.deviantart

import android.annotation.SuppressLint
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import rx.Observable
import java.net.URLEncoder
import java.text.SimpleDateFormat

class DeviantART : HttpSource() {

    override val baseUrl: String
        get() {
            return if (currentBase) {
                "https://www.deviantart.com"
            } else {
                "https://backend.deviantart.com/rss.xml"
            }
        }
    private var currentBase = true
    private val homeQuery = "The-Monster-Shop"
    override val lang = "all"
    override val name = "DeviantART"
    override val supportsLatest = false

    @SuppressLint("SimpleDateFormat")
    private val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz")




    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var q = query
        if (q.isEmpty()) {
            q = homeQuery
        }
        currentBase = true
        return GET("$baseUrl/${q.trim()}/gallery")
    }

    override fun searchMangaParse(response: Response) =
        MangasPage(
            response.asJsoup()
                .select("div[data-hook^=gallection_folder_]")
                .map {
                    SManga.create().apply {
                        val gallery = it.parent().attr("href").toHttpUrl().encodedPathSegments.let {
                            if (it[it.size - 2] != "gallery") {
                                "/${it[it.size - 2]}"
                            } else { "" }
                        }
                        currentBase = false
                        val author0 = response.request.url.encodedPathSegments.first()
                        author = author0
                        setUrlWithoutDomain("?type=deviation&q=gallery:$author0$gallery")
                        title = it.getElementsByTag("h2").first().text()
                        description = "Really Updating V4"
                        status = SManga.ONGOING
                        thumbnail_url = it.selectFirst("img").attr("abs:src")
                        initialized = false
                    }
                },
            false
        )

    override fun chapterListParse(response: Response): List<SChapter> {
        return Jsoup.parse(response.body?.string(), baseUrl, Parser.xmlParser())
            .getElementsByTag("item").filter {
                it.selectFirst("media|content")?.hasAttr("url") ?: false
            }.map {
                SChapter.create().apply {

                    name = it.getElementsByTag("title").first().text()
                    url = it.selectFirst("media|content").attr("url")
                    // setUrlWithoutDomain(it.getElementsByTag("link").first().text())
                    date_upload = dateFormat.parse(
                        it.getElementsByTag("pubDate").first().text()
                    )?.time ?: System.currentTimeMillis()
                    chapter_number = it.selectFirst("link").text().substringAfterLast("-").toFloat()
                    scanlator = it.selectFirst("link").text().substringAfterLast('/')
                }
            }
    }



    override fun mangaDetailsParse(response: Response): SManga {
        return Jsoup.parse(response.body?.string(), "", Parser.xmlParser())
            .selectFirst("channel").let {
                SManga.create().apply {
                    val author0 = it.selectFirst("media|credit").text()
                    description = it.selectFirst("description").text()
                    author = author0
                    artist = author0
                    status = SManga.ONGOING
                    initialized = true
                }
            }
    }

    override fun fetchPageList(chapter: SChapter) = chapter.let {
        Observable.just(listOf(Page(0, chapter.url, chapter.url),))!! }

    override fun popularMangaParse(response: Response) = searchMangaParse(response)
    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, homeQuery, FilterList())


    override fun pageListParse(response: Response) = throw UnsupportedOperationException("Not Used")
    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException("Not Used")
    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException("Not Used")
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException("Not Used")
}
