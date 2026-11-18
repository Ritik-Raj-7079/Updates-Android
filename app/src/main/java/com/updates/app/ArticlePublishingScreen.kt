package com.updates.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class CommunityArticle(
    val id: String,
    val title: String,
    val category: String,
    val body: String,
    val publishedAt: Long,
    val authorName: String = "Reader",
    val authorUid: String = ""
)

@Composable
fun ArticlePublishingScreen(
    articles: List<CommunityArticle>,
    authorName: String,
    onPublish: (CommunityArticle) -> Unit,
    onDelete: (String) -> Unit,
    currentUserId: String = "",
    publishStatus: String = ""
) {
    var composing by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Technology") }
    var body by remember { mutableStateOf("") }
    var selectedArticle by remember { mutableStateOf<CommunityArticle?>(null) }
    val categories = listOf("Technology", "Science", "Education", "Research", "History", "Environment", "Literature", "Business", "Other")

    if (selectedArticle != null) {
        val article = selectedArticle!!
        Column(Modifier.fillMaxSize().background(Color(0xFFF5F7FC))) {
            Row(Modifier.fillMaxWidth().background(Color.White).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selectedArticle = null }) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                Text("COMMUNITY ARTICLE", fontWeight = FontWeight.Bold, color = Color(0xFF071A57))
            }
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Text(article.category.uppercase(), color = Color(0xFF155EEF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(article.title, color = Color(0xFF071A57), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 33.sp)
                Spacer(Modifier.height(18.dp))
                HorizontalDivider()
                Spacer(Modifier.height(18.dp))
                Text(article.body, color = Color.DarkGray, fontSize = 16.sp, lineHeight = 27.sp)
                Spacer(Modifier.height(18.dp))
                Text("Published by ${article.authorName} · UPDATES Community", color = Color.Gray, fontSize = 11.sp)
            }
        }
        return
    }

    if (composing) {
        Column(Modifier.fillMaxSize().background(Color(0xFFF5F7FC)).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { composing = false }) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                Text("Write an article", color = Color(0xFF071A57), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Text("Share something worth knowing.", color = Color.Gray, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Article title") }, placeholder = { Text("Give your article a clear title") }, shape = RoundedCornerShape(14.dp), singleLine = true)
            Spacer(Modifier.height(12.dp))
            Text("Choose a category", color = Color(0xFF071A57), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                categories.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { item ->
                            FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item, fontSize = 11.sp) })
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = body, onValueChange = { body = it }, modifier = Modifier.weight(1f).fillMaxWidth(), label = { Text("Your article") }, placeholder = { Text("Write your ideas, explanations or story here...") }, shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    onPublish(CommunityArticle(java.util.UUID.randomUUID().toString(), title.trim(), category, body.trim(), System.currentTimeMillis(), authorName, currentUserId))
                    title = ""; body = ""; category = "Technology"; composing = false
                },
                enabled = title.trim().length >= 5 && body.trim().length >= 30,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Icon(Icons.Filled.Send, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Publish article") }
            Text("Publishing as $authorName · Shared with the UPDATES community.", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
        }
    } else {
        Column(Modifier.fillMaxSize().background(Color(0xFFF5F7FC)).padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Community", color = Color(0xFF071A57), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Ideas and knowledge shared by readers", color = Color.Gray, fontSize = 12.sp)
                }
                Button(onClick = { composing = true }, shape = RoundedCornerShape(13.dp)) { Icon(Icons.Filled.Add, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("Write") }
            }
            Spacer(Modifier.height(16.dp))
            if (publishStatus.isNotBlank()) {
                Text(publishStatus, color = Color(0xFF155EEF), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
            }
            if (articles.isEmpty()) {
                Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("Your ideas belong here", color = Color(0xFF071A57), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Publish your first article and start building your knowledge collection.", color = Color.Gray, fontSize = 13.sp)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { composing = true }) { Icon(Icons.Filled.Edit, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Create an article") }
                }
            } else {
                Text("COMMUNITY ARTICLES · ${articles.size}", color = Color(0xFF155EEF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(articles, key = { it.id }) { article ->
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.padding(15.dp)) {
                                Text(article.category.uppercase(), color = Color(0xFF155EEF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(5.dp))
                                Text(article.title, color = Color(0xFF071A57), fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { selectedArticle = article })
                                Spacer(Modifier.height(5.dp))
                                Text(article.body.take(150) + if (article.body.length > 150) "…" else "", color = Color.DarkGray, fontSize = 12.sp, lineHeight = 18.sp)
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("By ${article.authorName}", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { selectedArticle = article }) { Text("Read") }
                                    if (article.authorUid == currentUserId && currentUserId.isNotBlank()) {
                                        IconButton(onClick = { onDelete(article.id) }) { Icon(Icons.Filled.Delete, contentDescription = "Delete article", tint = Color(0xFFB42318)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}