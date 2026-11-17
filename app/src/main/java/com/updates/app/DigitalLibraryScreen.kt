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
import androidx.compose.material.icons.filled.*
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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private data class LibraryBook(
    val key: String,
    val title: String,
    val authors: String,
    val year: String,
    val editions: String
)

@Composable
fun DigitalLibraryScreen(savedTitles: Set<String>, onToggleSaved: (String) -> Unit) {
    var query by remember { mutableStateOf("science") }
    var submittedQuery by remember { mutableStateOf("science") }
    var books by remember { mutableStateOf<List<LibraryBook>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedBook by remember { mutableStateOf<LibraryBook?>(null) }
    val context = LocalContext.current

    LaunchedEffect(submittedQuery) {
        loading = true
        error = null
        try {
            books = withContext(Dispatchers.IO) {
                val encoded = java.net.URLEncoder.encode(submittedQuery, "UTF-8")
                val connection = URL("https://openlibrary.org/search.json?q=$encoded&limit=25&fields=key,title,author_name,first_publish_year,edition_count").openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 12000
                connection.readTimeout = 12000
                try {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val docs = JSONObject(body).optJSONArray("docs")
                    buildList {
                        if (docs != null) for (i in 0 until docs.length()) {
                            val item = docs.optJSONObject(i) ?: continue
                            val key = item.optString("key", "")
                            val title = item.optString("title", "Untitled")
                            if (key.isNotBlank() && title.isNotBlank()) {
                                val authors = item.optJSONArray("author_name")
                                val authorText = if (authors != null && authors.length() > 0) authors.optString(0) else "Author not listed"
                                add(LibraryBook(key, title, authorText, item.optInt("first_publish_year", 0).let { if (it > 0) it.toString() else "Year not listed" }, item.optInt("edition_count", 0).toString()))
                            }
                        }
                    }
                } finally { connection.disconnect() }
            }
        } catch (_: Exception) {
            error = "Couldn't load books. Check your internet connection and try again."
            books = emptyList()
        } finally { loading = false }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFF5F7FC)).padding(horizontal = 18.dp, vertical = 16.dp)) {
        Text("Digital Library", color = Color(0xFF071A57), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text("Discover books from the Open Library catalogue", color = Color.Gray, fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search books, authors or subjects") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = { IconButton(onClick = { if (query.isNotBlank()) submittedQuery = query.trim() }) { Icon(Icons.Filled.ArrowForward, contentDescription = "Search") } },
            singleLine = true, shape = RoundedCornerShape(15.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White)
        )
        Spacer(Modifier.height(10.dp))
        Text("BOOK DISCOVERY", color = Color(0xFF155EEF), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        if (loading) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF155EEF)) }
        } else if (error != null) {
            Text(error!!, color = Color(0xFFB42318), modifier = Modifier.padding(vertical = 20.dp))
        } else if (books.isEmpty()) {
            Text("No books found. Try another search.", color = Color.Gray, modifier = Modifier.padding(vertical = 20.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(books) { book ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(46.dp).background(Color(0xFFEAF0FF), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.MenuBook, contentDescription = null, tint = Color(0xFF155EEF))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f).clickable { selectedBook = book }) {
                                Text(book.title, color = Color(0xFF071A57), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(book.authors, color = Color.DarkGray, fontSize = 11.sp)
                                Text("${book.year}  •  ${book.editions} editions", color = Color.Gray, fontSize = 10.sp)
                            }
                            IconButton(onClick = { onToggleSaved(book.key) }) {
                                Icon(if (book.key in savedTitles) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, contentDescription = "Save book", tint = Color(0xFF155EEF))
                            }
                        }
                    }
                }
            }
        }
        Text("Catalogue information is provided by Open Library. A catalogue listing does not mean the full book is free to read; availability and borrowing rules depend on each title.", color = Color.Gray, fontSize = 10.sp, lineHeight = 15.sp)
    }

    selectedBook?.let { book ->
        AlertDialog(
            onDismissRequest = { selectedBook = null },
            title = { Text(book.title, color = Color(0xFF071A57), fontWeight = FontWeight.Bold) },
            text = { Column {
                Text("Author: ${book.authors}")
                Text("First publication: ${book.year}")
                Text("Known editions: ${book.editions}")
                Spacer(Modifier.height(8.dp))
                Text("Reading or borrowing access depends on the edition. Open the catalogue record to check available options.", color = Color.Gray, fontSize = 12.sp)
            } },
            confirmButton = { TextButton(onClick = {
                val url = "https://openlibrary.org${book.key}"
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                selectedBook = null
            }) { Text("View catalogue") } },
            dismissButton = { TextButton(onClick = { selectedBook = null }) { Text("Close") } }
        )
    }
}