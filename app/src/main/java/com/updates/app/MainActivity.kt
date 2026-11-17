package com.updates.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Navy = Color(0xFF071A57)
private val Blue = Color(0xFF155EEF)
private val Orange = Color(0xFFFF9900)
private val Page = Color(0xFFF5F7FC)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { UpdatesApp() }
    }
}

private data class Story(val category: String, val title: String, val summary: String, val readTime: String)

@Composable
private fun UpdatesApp() {
    var selectedTab by remember { mutableStateOf("Home") }
    var query by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(setOf<String>()) }
    val stories = listOf(
        Story("TECHNOLOGY", "How artificial intelligence is changing everyday life", "A beginner-friendly look at the ideas and tools shaping the next generation of technology.", "5 min read"),
        Story("SCIENCE", "The science of a healthier planet", "Explore how researchers are studying climate, energy and sustainable solutions.", "7 min read"),
        Story("RESEARCH", "Inside the world of open scientific research", "Learn how research papers are shared, reviewed and used to build new knowledge.", "8 min read"),
        Story("EDUCATION", "Small learning habits that make a big difference", "Practical ways to stay curious, organize ideas and keep learning every day.", "4 min read")
    )
    Scaffold(
        containerColor = Page,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                listOf("Home", "Explore", "Library", "Profile").forEach { tab ->
                    val icon = when (tab) {
                        "Home" -> Icons.Filled.Home
                        "Explore" -> Icons.Filled.Explore
                        "Library" -> Icons.Filled.Bookmarks
                        else -> Icons.Filled.Person
                    }
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(icon, contentDescription = tab) },
                        label = { Text(tab) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Blue, selectedTextColor = Blue)
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                "Home" -> HomeScreen(stories, query, { query = it }, saved, { title ->
                    saved = if (title in saved) saved - title else saved + title
                })
                "Explore" -> ExploreScreen { selectedTab = "Home" }
                "Library" -> LibraryScreen(stories.filter { it.title in saved })
                else -> ProfileScreen()
            }
        }
    }
}

@Composable
private fun HomeScreen(
    stories: List<Story>, query: String, onQuery: (String) -> Unit,
    saved: Set<String>, onSave: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).background(Navy, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Text("U", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("UPDATES", color = Navy, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, letterSpacing = 1.sp)
                    Text("Build Your Own Treasure of Knowledge.", color = Color.Gray, fontSize = 10.sp)
                }
                IconButton(onClick = {}) { Icon(Icons.Filled.NotificationsNone, contentDescription = "Notifications", tint = Navy) }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().background(Navy, RoundedCornerShape(22.dp)).padding(20.dp)) {
                Text("YOUR DAILY DOSE OF KNOWLEDGE", color = Orange, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                Text("Discover something\\nnew every day.", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold, lineHeight = 31.sp)
                Spacer(Modifier.height(8.dp))
                Text("Ideas, discoveries and stories worth your time.", color = Color(0xFFDCE5FF), fontSize = 13.sp)
            }
        }
        item {
            OutlinedTextField(
                value = query, onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search topics, stories and research") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White)
            )
        }
        item { SectionTitle("Recommended for you", "Based on your curiosity") }
        val filtered = stories.filter { it.title.contains(query, true) || it.category.contains(query, true) || it.summary.contains(query, true) }
        if (filtered.isEmpty()) {
            item { Text("No matching stories yet. Try another search.", color = Color.Gray) }
        } else {
            items(filtered) { story -> StoryCard(story, story.title in saved) { onSave(story.title) } }
        }
        item {
            Text("More knowledge. More perspectives. Every day.", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column {
        Text(title, color = Navy, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Color.Gray, fontSize = 12.sp)
    }
}

@Composable
private fun StoryCard(story: Story, isSaved: Boolean, onSave: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(story.category, color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onSave, modifier = Modifier.size(32.dp)) {
                Icon(if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, contentDescription = "Save story", tint = Blue)
            }
        }
        Text(story.title, color = Navy, fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp)
        Spacer(Modifier.height(6.dp))
        Text(story.summary, color = Color.DarkGray, fontSize = 13.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(story.readTime, color = Color.Gray, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text("Read more  →", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ExploreScreen(onChoose: () -> Unit) {
    val categories = listOf("Technology", "Science", "Research", "Education", "History", "Environment", "Literature", "Business", "Health & Wellbeing", "Space")
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        SectionTitle("Explore", "Find a topic that sparks your curiosity")
        Spacer(Modifier.height(18.dp))
        categories.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { category ->
                    Card(
                        modifier = Modifier.weight(1f).padding(bottom = 10.dp).clickable { onChoose() },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(category, modifier = Modifier.fillMaxWidth().padding(18.dp), color = Navy, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LibraryScreen(savedStories: List<Story>) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        SectionTitle("My Library", "Your saved reading, all in one place")
        Spacer(Modifier.height(16.dp))
        if (savedStories.isEmpty()) Text("Your library is empty. Save a story from Home to find it here.", color = Color.Gray)
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(savedStories) { StoryCard(it, true) {} }
        }
    }
}

@Composable
private fun ProfileScreen() {
    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(28.dp))
        Box(Modifier.size(82.dp).background(Navy, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text("Welcome to UPDATES", color = Navy, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text("Your knowledge journey starts here.", color = Color.Gray, fontSize = 13.sp)
        Spacer(Modifier.height(24.dp))
        Text("Profile, interests and account settings will be added in the next development milestone.", color = Color.DarkGray, fontSize = 14.sp)
    }
}
