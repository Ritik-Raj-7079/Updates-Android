package com.updates.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.HttpURLConnection
import java.net.URL

private data class NewsItem(val title: String, val summary: String, val link: String, val date: String, val source: String)
private data class NewsFeed(val label: String, val url: String)

private val newsFeeds = linkedMapOf(
    "Top" to NewsFeed("BBC News", "https://feeds.bbci.co.uk/news/rss.xml"),
    "India" to NewsFeed("BBC News", "https://feeds.bbci.co.uk/news/world/asia/india/rss.xml"),
    "World" to NewsFeed("BBC News", "https://feeds.bbci.co.uk/news/world/rss.xml"),
    "Technology" to NewsFeed("BBC News", "https://feeds.bbci.co.uk/news/technology/rss.xml"),
    "Science" to NewsFeed("BBC News", "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml"),
    "Business" to NewsFeed("BBC News", "https://feeds.bbci.co.uk/news/business/rss.xml"),
    "Health" to NewsFeed("BBC News", "https://feeds.bbci.co.uk/news/health/rss.xml")
)

@Composable
fun NewsScreen() {
    var category by remember { mutableStateOf("Top") }
    var articles by remember { mutableStateOf<List<NewsItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    LaunchedEffect(category, refreshKey) {
        loading = true
        error = null
        try {
            val feed = newsFeeds[category] ?: newsFeeds.getValue("Top")
            articles = withContext(Dispatchers.IO) { loadNewsFeed(feed) }
            if (articles.isEmpty()) error = "No articles are available in this feed right now."
        } catch (_: Exception) {
            articles = emptyList()
            error = "Could not load news. Check your internet connection and refresh."
        }
        loading = false
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFF5F7FC)).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("News", color = Color(0xFF071A57), fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                Text("Headlines from BBC News RSS feeds", color = Color.Gray, fontSize = 11.sp)
            }
            IconButton(onClick = { refreshKey++ }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh news", tint = Color(0xFF155EEF)) }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("Top", "India", "World", "Technology").forEach { name ->
                FilterChip(selected = category == name, onClick = { category = name }, label = { Text(name, fontSize = 11.sp) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("Science", "Business", "Health").forEach { name ->
                FilterChip(selected = category == name, onClick = { category = name }, label = { Text(name, fontSize = 11.sp) })
            }
        }
        Spacer(Modifier.height(10.dp))
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF155EEF)) }
            error != null -> Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error!!, color = Color.DarkGray, fontSize = 13.sp)
                TextButton(onClick = { refreshKey++ }) { Text("Try again") }
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
                items(articles) { article ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(15.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Article, contentDescription = null, tint = Color(0xFF155EEF), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(article.source, color = Color(0xFF155EEF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text(article.date, color = Color.Gray, fontSize = 9.sp)
                            }
                            Spacer(Modifier.height(7.dp))
                            Text(article.title, color = Color(0xFF071A57), fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 21.sp)
                            if (article.summary.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(article.summary, color = Color.DarkGray, fontSize = 12.sp, lineHeight = 18.sp)
                            }
                            Spacer(Modifier.height(10.dp))
                            Text("Read full report  →", color = Color(0xFF155EEF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.link)))
                            })
                        }
                    }
                }
            }
        }
        Text("Headlines and summaries are supplied by BBC News. Open the publisher link for the complete report.", color = Color.Gray, fontSize = 9.sp, lineHeight = 13.sp)
    }
}

private fun loadNewsFeed(feed: NewsFeed): List<NewsItem> {
    val connection = (URL(feed.url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 12000
        readTimeout = 12000
        setRequestProperty("User-Agent", "UPDATES-Android/1.0 (RSS reader)")
    }
    try {
        if (connection.responseCode !in 200..299) error("RSS request failed")
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(connection.inputStream, "UTF-8")
        val result = mutableListOf<NewsItem>()
        var event = parser.eventType
        var inItem = false
        var title = ""
        var description = ""
        var link = ""
        var date = ""
        var currentTag = ""
        while (event != XmlPullParser.END_DOCUMENT && result.size < 35) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name.lowercase()
                    if (currentTag == "item") { inItem = true; title = ""; description = ""; link = ""; date = "" }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (inItem) {
                    val value = parser.text?.trim().orEmpty()
                    when (currentTag) {
                        "title" -> title += value
                        "description" -> description += value
                        "link" -> link += value
                        "pubdate" -> date += value
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("item", true)) {
                        if (title.isNotBlank() && link.startsWith("https://")) {
                            result.add(NewsItem(title, description.replace(Regex("<[^>]*>"), "").replace("&amp;", "&").take(240), link, date.take(22), feed.label))
                        }
                        inItem = false
                    }
                    currentTag = ""
                }
            }
            event = parser.nextToken()
        }
        return result
    } finally { connection.disconnect() }
}