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

private data class ResearchWork(
    val id: String,
    val title: String,
    val year: String,
    val authors: String,
    val doi: String
)

@Composable
fun ResearchScreen() {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var works by remember { mutableStateOf<List<ResearchWork>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var searched by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            works = emptyList()
            error = ""
            searched = false
            return@LaunchedEffect
        }
        delay(550)
        loading = true
        error = ""
        searched = true
        try {
            works = withContext(Dispatchers.IO) { searchOpenAlex(query.trim()) }
        } catch (e: Exception) {
            error = "Couldn't load research results. Check your internet connection and try again."
            works = emptyList()
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFF5F7FC)).padding(horizontal = 18.dp, vertical = 16.dp)) {
        Text("Research & Journals", color = Color(0xFF071A57), fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(4.dp))
        Text("Discover scholarly works from around the world", color = Color.Gray, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search topics, authors or papers") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(15.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White)
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Powered by OpenAlex", color = Color(0xFF155EEF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("Scholarly metadata", color = Color.Gray, fontSize = 10.sp)
        }
        Spacer(Modifier.height(12.dp))
        when {
            loading -> Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF155EEF)) }
            error.isNotEmpty() -> Text(error, color = Color(0xFFB42318), fontSize = 13.sp, modifier = Modifier.padding(vertical = 18.dp))
            works.isNotEmpty() -> LazyColumn(verticalArrangement = Arrangement.spacedBy(11.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
                items(works) { work ->
                    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).clickable {
                        val target = if (work.doi.isNotBlank()) work.doi else work.id
                        val url = if (target.startsWith("http")) target else "https://openalex.org/" + target.substringAfterLast("/")
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }.padding(15.dp)) {
                        Text(work.title, color = Color(0xFF071A57), fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 21.sp)
                        Spacer(Modifier.height(7.dp))
                        Text(work.authors, color = Color.DarkGray, fontSize = 11.sp, lineHeight = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (work.year.isBlank()) "Year unavailable" else work.year, color = Color(0xFF155EEF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.weight(1f))
                            Text("View source", color = Color(0xFF155EEF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Filled.OpenInNew, contentDescription = "Open source", tint = Color(0xFF155EEF), modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
            searched -> Text("No results found. Try a broader search term.", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(vertical = 18.dp))
            else -> Column(Modifier.fillMaxWidth().padding(top = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Explore the world of research", color = Color(0xFF071A57), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                Text("Search for a subject such as artificial intelligence, climate change, space or education.", color = Color.Gray, fontSize = 12.sp, lineHeight = 19.sp)
            }
        }
    }
}

private fun searchOpenAlex(search: String): List<ResearchWork> {
    val encoded = URLEncoder.encode(search, "UTF-8")
    val url = URL("https://api.openalex.org/works?search=" + encoded + "&per-page=20&select=id,display_name,publication_year,authorships,doi")
    val connection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 12000
        readTimeout = 12000
        setRequestProperty("Accept", "application/json")
    }
    try {
        if (connection.responseCode !in 200..299) throw IllegalStateException("Research service unavailable")
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { index ->
            val item = results.optJSONObject(index) ?: return@mapNotNull null
            val title = item.optString("display_name").trim()
            if (title.isBlank()) return@mapNotNull null
            val authorships = item.optJSONArray("authorships")
            val authorNames = mutableListOf<String>()
            if (authorships != null) {
                for (i in 0 until minOf(authorships.length(), 3)) {
                    val author = authorships.optJSONObject(i)?.optJSONObject("author")
                    val name = author?.optString("display_name").orEmpty()
                    if (name.isNotBlank()) authorNames.add(name)
                }
            }
            ResearchWork(
                id = item.optString("id"),
                title = title,
                year = item.optInt("publication_year").takeIf { it > 0 }?.toString().orEmpty(),
                authors = if (authorNames.isEmpty()) "Author details unavailable" else authorNames.joinToString(", ") + if ((authorships?.length() ?: 0) > 3) " et al." else "",
                doi = item.optString("doi").takeIf { it.startsWith("http") }.orEmpty()
            )
        }
    } finally {
        connection.disconnect()
    }
}