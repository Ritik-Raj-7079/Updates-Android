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
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private val ExploreNavy = Color(0xFF071A57)
private val ExploreBlue = Color(0xFF155EEF)
private val ExplorePage = Color(0xFFF5F7FC)
private val ExploreTopics = listOf("Technology", "Science", "Research", "Education", "History", "Environment", "Literature", "Business", "Space", "Health")

private data class WikiSearchItem(
    val title: String,
    val description: String,
    val excerpt: String,
    val url: String
)

@Composable
private fun KnowledgeExploreScreen(onExploreCategory: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<WikiSearchItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    LaunchedEffect(query) {
        val term = query.trim()
        if (term.length < 2) {
            results = emptyList()
            loading = false
            error = null
        } else {
            delay(450)
            loading = true
            error = null
            try {
                results = withContext(Dispatchers.IO) { searchWikipedia(term) }
            } catch (_: Exception) {
                results = emptyList()
                error = "Could not load results. Check your internet connection and try again."
            }
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().background(ExplorePage).padding(horizontal = 20.dp, vertical = 18.dp)) {
        Text("Explore", color = ExploreNavy, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text("Search a growing world of knowledge", color = Color.Gray, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search science, technology, history...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(15.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White)
        )
        Spacer(Modifier.height(12.dp))
        if (query.isBlank()) {
            Text("Explore by category", color = ExploreNavy, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(10.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(ExploreTopics) { category ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onExploreCategory(category) },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(category, modifier = Modifier.weight(1f), color = ExploreNavy, fontWeight = FontWeight.SemiBold)
                            Text("Explore  ›", color = ExploreBlue, fontSize = 12.sp)
                        }
                    }
                }
            }
        } else if (loading) {
            Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = ExploreBlue) }
        } else if (error != null) {
            Text(error!!, color = Color(0xFFB42318), fontSize = 13.sp)
        } else if (results.isEmpty()) {
            Text("No results found. Try a different search term.", color = Color.Gray, fontSize = 13.sp)
        } else {
            Text("${results.size} results · Wikipedia", color = Color.Gray, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(results) { item ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(15.dp)) {
                        Column(Modifier.fillMaxWidth().padding(15.dp)) {
                            Text(item.title, color = ExploreNavy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            if (item.description.isNotBlank()) {
                                Spacer(Modifier.height(3.dp))
                                Text(item.description, color = ExploreBlue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.height(7.dp))
                            Text(item.excerpt, color = Color.DarkGray, fontSize = 13.sp, lineHeight = 19.sp)
                            Spacer(Modifier.height(9.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                Text("Read on Wikipedia", color = ExploreBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
                                })
                                Spacer(Modifier.width(5.dp))
                                Icon(Icons.Filled.OpenInNew, contentDescription = "Open source", tint = ExploreBlue, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Search results are provided by Wikipedia. Open the source to read the full article and review its references.", color = Color.Gray, fontSize = 10.sp, lineHeight = 14.sp)
    }
}

private fun searchWikipedia(query: String): List<WikiSearchItem> {
    val encoded = URLEncoder.encode(query, "UTF-8")
    val url = URL("https://en.wikipedia.org/w/rest.php/v1/search/page?q=$encoded&limit=15")
    val connection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 10000
        readTimeout = 10000
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "UPDATES-Android/1.0 (knowledge discovery app)")
    }
    try {
        if (connection.responseCode !in 200..299) error("Wikipedia request failed: ${connection.responseCode}")
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val pages = JSONObject(body).optJSONArray("pages") ?: return emptyList()
        return (0 until pages.length()).mapNotNull { index ->
            val page = pages.optJSONObject(index) ?: return@mapNotNull null
            val key = page.optString("key")
            val title = page.optString("title")
            if (title.isBlank() || key.isBlank()) return@mapNotNull null
            WikiSearchItem(
                title = title,
                description = page.optString("description").takeUnless { it == "null" } ?: "",
                excerpt = page.optString("excerpt").replace(Regex("<[^>]*>"), "").replace("&quot;", "\"").replace("&#39;", "'"),
                url = "https://en.wikipedia.org/wiki/" + Uri.encode(key)
            )
        }
    } finally {
        connection.disconnect()
    }
}