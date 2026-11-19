package com.updates.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

private val Navy = Color(0xFF071A57)
private val Blue = Color(0xFF155EEF)
private val Orange = Color(0xFFFF9900)
private val Page = Color(0xFFF5F7FC)
private val Topics = listOf(
    "Technology", "Science", "Research", "Education", "History",
    "Environment", "Literature", "Business", "Space", "Health"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = getSharedPreferences("updates_preferences", MODE_PRIVATE)
        val displayName = preferences.getString("profile_name", "Knowledge Explorer") ?: "Knowledge Explorer"
        val profileBio = preferences.getString("profile_bio", "") ?: ""
        val completed = preferences.getBoolean("onboarding_completed", false)
        val savedTopics = preferences.getStringSet("selected_topics", emptySet()) ?: emptySet()
        val savedStoryTitles = preferences.getStringSet("saved_story_titles", emptySet()) ?: emptySet()
        val savedBookKeys = preferences.getStringSet("saved_book_keys", emptySet()) ?: emptySet()
        val historyJson = preferences.getString("reading_history", "[]") ?: "[]"
        val storyProgressJson = preferences.getString("story_reading_progress", "{}") ?: "{}"
        val initialStoryProgress = JSONObject(storyProgressJson).let { obj ->
            buildMap { obj.keys().forEach { key -> put(key, obj.optInt(key, 0).coerceAtLeast(0)) } }
        }
        val articleArray = JSONArray(preferences.getString("community_articles", "[]") ?: "[]")
        val initialArticles = mutableListOf<CommunityArticle>()
        for (index in 0 until articleArray.length()) {
            val item = articleArray.optJSONObject(index) ?: continue
            initialArticles.add(CommunityArticle(item.optString("id"), item.optString("title"),
                item.optString("category"), item.optString("body"), item.optLong("publishedAt"), item.optString("authorName", "Reader"), item.optString("authorUid", "")) )
        }
        setContent {
            AuthGate { firebaseUser, onSignOut ->
            UpdatesApp(
                showOnboardingInitially = !completed,
                initialTopics = savedTopics,
                initialSavedStories = savedStoryTitles,
                initialSavedBooks = savedBookKeys,
                initialReadingHistory = JSONArray(historyJson).let { a -> (0 until a.length()).map { a.optString(it) } },
                initialStoryProgress = initialStoryProgress,
                initialArticles = initialArticles,
                initialDisplayName = displayName,
                initialProfileBio = profileBio,
                userId = firebaseUser.uid,
                userEmail = firebaseUser.email ?: "",
                onSignOut = onSignOut,
                onProfileChange = { name, bio ->
                    preferences.edit().putString("profile_name", name).putString("profile_bio", bio).apply()
                },
                onArticlesChange = { articles ->
                    val array = JSONArray()
                    articles.forEach { article ->
                        array.put(JSONObject().put("id", article.id).put("title", article.title)
                            .put("category", article.category).put("body", article.body)
                            .put("publishedAt", article.publishedAt).put("authorName", article.authorName).put("authorUid", article.authorUid))
                    }
                    preferences.edit().putString("community_articles", array.toString()).apply()
                },
                onOnboardingComplete = { topics ->
                    preferences.edit()
                        .putBoolean("onboarding_completed", true)
                        .putStringSet("selected_topics", topics)
                        .apply()
                },
                onReadingDataChange = { storySet, bookSet, history ->
                    preferences.edit().putStringSet("saved_story_titles", storySet.toSet())
                        .putStringSet("saved_book_keys", bookSet.toSet())
                        .putString("reading_history", JSONArray(history).toString()).apply()
                    CloudReadingRepository.save(
                        firebaseUser.uid,
                        CloudReadingData(storySet, bookSet, history),
                        onError = { android.util.Log.w("UPDATES_SYNC", it) }
                    )
                },
                onReadingProgressChange = { title, position ->
                    val progress = JSONObject(preferences.getString("story_reading_progress", "{}") ?: "{}")
                    progress.put(title, position)
                    preferences.edit().putString("story_reading_progress", progress.toString()).apply()
                    CloudReadingRepository.saveProgress(firebaseUser.uid, title, position,
                        onError = { android.util.Log.w("UPDATES_PROGRESS", it) })
                }
            )
            }
        }
    }
}

private data class Story(
    val category: String,
    val title: String,
    val summary: String,
    val readTime: String
)

@Composable
private fun UpdatesApp(
    showOnboardingInitially: Boolean,
    initialTopics: Set<String>,
    initialSavedStories: Set<String>,
    initialSavedBooks: Set<String>,
    initialReadingHistory: List<String>,
    initialStoryProgress: Map<String, Int>,
    initialArticles: List<CommunityArticle>,
    initialDisplayName: String,
    initialProfileBio: String,
    userId: String,
    userEmail: String,
    onSignOut: () -> Unit,
    onProfileChange: (String, String) -> Unit,
    onArticlesChange: (List<CommunityArticle>) -> Unit,
    onOnboardingComplete: (Set<String>) -> Unit,
    onReadingDataChange: (Set<String>, Set<String>, List<String>) -> Unit,
    onReadingProgressChange: (String, Int) -> Unit
) {
    var showOnboarding by remember { mutableStateOf(showOnboardingInitially) }
    var selectedTopics by remember { mutableStateOf(initialTopics) }
    var selectedTab by remember { mutableStateOf("Home") }
    var showExitDialog by remember { mutableStateOf(false) }
    val appContext = androidx.compose.ui.platform.LocalContext.current
    var query by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(initialSavedStories) }
    var savedBooks by remember { mutableStateOf(initialSavedBooks) }
    var readingHistory by remember { mutableStateOf(initialReadingHistory) }
    var storyProgress by remember { mutableStateOf(initialStoryProgress) }
    var communityArticles by remember { mutableStateOf(initialArticles) }
    var displayName by remember { mutableStateOf(initialDisplayName) }
    var profileBio by remember { mutableStateOf(initialProfileBio) }
    var cloudProfileMessage by remember { mutableStateOf("Syncing profile…") }
    var cloudArticleMessage by remember { mutableStateOf("Loading community…") }

    DisposableEffect(userId) {
        val registration = CloudArticleRepository.observe(
            onChange = { articles -> communityArticles = articles; cloudArticleMessage = "Community is up to date" },
            onError = { cloudArticleMessage = "Community unavailable: $it" }
        )
        onDispose { registration.remove() }
    }

    LaunchedEffect(userId) {
        CloudReadingRepository.load(userId, onSuccess = { cloudData ->
            // Merge cloud and device data so a first-time/empty cloud document
            // never wipes a reader's existing local library.
            saved = saved + cloudData.savedStories
            savedBooks = savedBooks + cloudData.savedBooks
            readingHistory = (readingHistory + cloudData.history).distinct().take(50)
            storyProgress = storyProgress + cloudData.storyProgress
            onReadingDataChange(saved, savedBooks, readingHistory)
            storyProgress.forEach { (title, position) -> onReadingProgressChange(title, position) }
        }, onError = { android.util.Log.w("UPDATES_SYNC", it) })
    }

    LaunchedEffect(userId) {
        CloudProfileRepository.load(userId, onSuccess = { cloudName, cloudBio ->
            if (cloudName.isNotBlank()) { displayName = cloudName; profileBio = cloudBio; onProfileChange(cloudName, cloudBio) }
            cloudProfileMessage = "Cloud profile connected"
        }, onError = { cloudProfileMessage = "Cloud sync unavailable: $it" })
    }
    var openedStory by remember { mutableStateOf<Story?>(null) }
    var selectedFeedCategory by remember { mutableStateOf("For You") }

    val stories = listOf(
        Story("TECHNOLOGY", "How artificial intelligence is changing everyday life", "A beginner-friendly look at the ideas and tools shaping the next generation of technology.", "5 min read"),
        Story("SCIENCE", "The science of a healthier planet", "Explore how researchers are studying climate, energy and sustainable solutions.", "7 min read"),
        Story("RESEARCH", "Inside the world of open scientific research", "Learn how research papers are shared, reviewed and used to build new knowledge.", "8 min read"),
        Story("EDUCATION", "Small learning habits that make a big difference", "Practical ways to stay curious, organize ideas and keep learning every day.", "4 min read"),
        Story("HISTORY", "How ancient ideas shaped the modern world", "Discover the connections between historical discoveries and life today.", "6 min read"),
        Story("SPACE", "A closer look at distant galaxies", "Explore how astronomers study objects far beyond our solar system.", "6 min read"),
        Story("ENVIRONMENT", "Everyday choices for a cleaner environment", "Discover practical ideas for reducing waste, saving resources and understanding environmental challenges.", "5 min read"),
        Story("LITERATURE", "Why stories remain powerful across generations", "Explore how books and storytelling preserve ideas, cultures and human experiences.", "6 min read"),
        Story("BUSINESS", "How small businesses turn ideas into value", "A simple introduction to customers, planning, innovation and responsible business growth.", "5 min read"),
        Story("HEALTH", "Understanding the science of good sleep", "Learn why sleep matters for learning, focus and everyday wellbeing.", "4 min read"),
        Story("NEWS", "How to read the news with a critical mind", "Learn to check sources, compare reports and separate verified information from opinion.", "4 min read")
    )

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Blue,
            secondary = Orange,
            background = Page,
            surface = Color.White
        )
    ) {
        BackHandler(enabled = !showOnboarding) {
            when {
                openedStory != null -> openedStory = null
                selectedTab != "Home" -> selectedTab = "Home"
                else -> showExitDialog = true
            }
        }

        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text("Exit UPDATES?") },
                text = { Text("Are you sure you want to close the app?") },
                confirmButton = {
                    TextButton(onClick = { showExitDialog = false; (appContext as? android.app.Activity)?.finish() }) {
                        Text("Exit", color = Blue)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitDialog = false }) {
                        Text("Stay", color = Blue)
                    }
                }
            )
        }

        if (showOnboarding) {
            OnboardingFlow(
                selectedTopics = selectedTopics,
                onTopicsChange = { selectedTopics = it },
                onFinish = {
                    onOnboardingComplete(selectedTopics)
                    showOnboarding = false
                }
            )
        } else if (openedStory != null) {
            ArticleReaderScreen(
                story = openedStory!!,
                isSaved = openedStory!!.title in saved,
                initialProgress = storyProgress[openedStory!!.title] ?: 0,
                onProgressChange = { position ->
                    if (storyProgress[openedStory!!.title] != position) {
                        storyProgress = storyProgress + (openedStory!!.title to position)
                        onReadingProgressChange(openedStory!!.title, position)
                    }
                },
                onBack = { openedStory = null },
                onSave = {
                    val title = openedStory!!.title
                    saved = if (title in saved) saved - title else saved + title
                    onReadingDataChange(saved, savedBooks, readingHistory)
                }
            )
        } else {
            MainDashboard(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                query = query,
                onQueryChange = { query = it },
                saved = saved,
                savedBooks = savedBooks,
                communityArticles = communityArticles,
                displayName = displayName,
                profileBio = profileBio,
                cloudProfileMessage = cloudProfileMessage,
                cloudArticleMessage = cloudArticleMessage,
                currentUserId = userId,
                onSignOut = onSignOut,
                onProfileSave = { name, bio ->
                    displayName = name
                    profileBio = bio
                    onProfileChange(name, bio)
                    CloudProfileRepository.save(userId, userEmail, name, bio, onSuccess = { cloudProfileMessage = "Profile synced to cloud" }, onError = { cloudProfileMessage = "Cloud sync failed: $it" })
                },
                onPublishArticle = { article ->
                    cloudArticleMessage = "Publishing article…"
                    CloudArticleRepository.publish(article, userId,
                        onSuccess = { cloudArticleMessage = "Article published to the community" },
                        onError = { cloudArticleMessage = "Publish failed: $it" })
                },
                onDeleteArticle = { id ->
                    CloudArticleRepository.delete(id,
                        onSuccess = { cloudArticleMessage = "Article deleted" },
                        onError = { cloudArticleMessage = "Delete failed: $it" })
                },
                onSaveBook = { key -> savedBooks = if (key in savedBooks) savedBooks - key else savedBooks + key; onReadingDataChange(saved, savedBooks, readingHistory) },
                onSave = { title ->
                    saved = if (title in saved) saved - title else saved + title
                    onReadingDataChange(saved, savedBooks, readingHistory)
                },
                readingHistory = readingHistory,
                onClearHistory = { readingHistory = emptyList(); onReadingDataChange(saved, savedBooks, readingHistory) },
                stories = stories,
                selectedTopics = selectedTopics,
                selectedFeedCategory = selectedFeedCategory,
                onFeedCategoryChange = { selectedFeedCategory = it },
                onExploreCategory = { category ->
                    selectedFeedCategory = category
                    selectedTab = "Home"
                },
                onOpenStory = { story -> openedStory = story; readingHistory = (listOf(story.title) + readingHistory.filterNot { it == story.title }).take(50); onReadingDataChange(saved, savedBooks, readingHistory) }
            )
        }
    }
}

@Composable
private fun OnboardingFlow(
    selectedTopics: Set<String>,
    onTopicsChange: (Set<String>) -> Unit,
    onFinish: () -> Unit
) {
    var page by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize().background(Page).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = if (page == 0) "1 OF 2" else "2 OF 2",
                color = Blue,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.weight(1f))
        if (page == 0) {
            BrandMark(size = 104)
            Spacer(Modifier.height(24.dp))
            Text(
                "Welcome to UPDATES",
                color = Navy,
                fontSize = 27.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Build Your Own Treasure of Knowledge.",
                color = Blue,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "Discover ideas, explore research, read inspiring stories and grow your knowledge every day.",
                color = Color.DarkGray,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                "What are you curious about?",
                color = Navy,
                fontSize = 25.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Choose a few interests to shape your reading experience. You can change them later.",
                color = Color.Gray,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Topics.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { topic ->
                        val selected = topic in selectedTopics
                        FilterChip(
                            selected = selected,
                            onClick = {
                                onTopicsChange(
                                    if (selected) selectedTopics - topic else selectedTopics + topic
                                )
                            },
                            label = { Text(topic) },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = {
                if (page == 0) page = 1 else onFinish()
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Blue)
        ) {
            Text(if (page == 0) "Get started" else "Continue to UPDATES", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.ArrowForward, contentDescription = null)
        }
        if (page == 1) {
            TextButton(onClick = onFinish) {
                Text("Skip for now", color = Color.Gray)
            }
        }
    }
}

@Composable
private fun BrandMark(size: Int) {
    Box(
        modifier = Modifier.size(size.dp).background(Navy, RoundedCornerShape((size / 4).dp)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.updates_mark),
            contentDescription = "UPDATES knowledge logo",
            modifier = Modifier.fillMaxSize().padding((size / 8).dp)
        )
    }
}

@Composable
private fun MainDashboard(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    saved: Set<String>,
    savedBooks: Set<String>,
    communityArticles: List<CommunityArticle>,
    displayName: String,
    profileBio: String,
    cloudProfileMessage: String,
    cloudArticleMessage: String,
    currentUserId: String,
    onSignOut: () -> Unit,
    onProfileSave: (String, String) -> Unit,
    onPublishArticle: (CommunityArticle) -> Unit,
    onDeleteArticle: (String) -> Unit,
    onSaveBook: (String) -> Unit,
    onSave: (String) -> Unit,
    readingHistory: List<String>,
    onClearHistory: () -> Unit,
    stories: List<Story>,
    selectedTopics: Set<String>,
    selectedFeedCategory: String,
    onFeedCategoryChange: (String) -> Unit,
    onExploreCategory: (String) -> Unit,
    onOpenStory: (Story) -> Unit
) {
    Scaffold(
        containerColor = Page,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                listOf("Home", "Explore", "News", "Research", "Library", "Profile").forEach { tab ->
                    val icon = when (tab) {
                        "Home" -> Icons.Filled.Home
                        "Explore" -> Icons.Filled.Explore
                        "News" -> Icons.Filled.Article
                        "Research" -> Icons.Filled.Science
                        "Library" -> Icons.Filled.Bookmarks
                        "Profile" -> Icons.Filled.Person
                        else -> Icons.Filled.Edit
                    }
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { onTabSelected(tab) },
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
                "Home" -> HomeScreen(stories, query, onQueryChange, saved, onSave, selectedTopics, selectedFeedCategory, onFeedCategoryChange, onOpenStory, { onTabSelected("Profile") })
                "Explore" -> KnowledgeExploreScreen(onExploreCategory)
                "News" -> NewsScreen()
                "Research" -> ResearchScreen()
                "Library" -> PersonalLibraryScreen(stories, saved, readingHistory, onSave, onClearHistory, onOpenStory, savedBooks, onSaveBook)
                "Profile" -> UserProfileScreen(displayName, profileBio, communityArticles.count { it.authorUid == currentUserId }, cloudProfileMessage, onProfileSave, { onTabSelected("Write") }, onSignOut)
                else -> ArticlePublishingScreen(communityArticles, displayName, onPublishArticle, onDeleteArticle, currentUserId, cloudArticleMessage)
            }
        }
    }
}

@Composable
private fun HomeScreen(
    stories: List<Story>, query: String, onQuery: (String) -> Unit,
    saved: Set<String>, onSave: (String) -> Unit, selectedTopics: Set<String>,
    selectedFeedCategory: String, onFeedCategoryChange: (String) -> Unit,
    onOpenStory: (Story) -> Unit, onProfileClick: () -> Unit
) {
    val feedCategories = listOf("For You", "News", "Research", "Stories") + Topics
    val filtered = stories.filter {
        val matchesSearch = query.isBlank() || it.title.contains(query, true) || it.category.contains(query, true) || it.summary.contains(query, true)
        val matchesInterest = selectedFeedCategory != "For You" || selectedTopics.isEmpty() || it.category.lowercase() in selectedTopics.map { t -> t.lowercase() }
        val matchesFeed = when (selectedFeedCategory) {
            "For You" -> true
            "Stories" -> it.category == "HISTORY" || it.category == "LITERATURE"
            else -> it.category.equals(selectedFeedCategory, true)
        }
        matchesSearch && matchesInterest && matchesFeed
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(40); Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("UPDATES", color = Navy, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, letterSpacing = 1.2.sp)
                    Text("Your space for ideas & discovery", color = Color.Gray, fontSize = 10.sp)
                }
                IconButton(onClick = onProfileClick) { Icon(Icons.Filled.AccountCircle, contentDescription = "Profile", tint = Navy) }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().background(Navy, RoundedCornerShape(24.dp)).padding(21.dp)) {
                Text("THE KNOWLEDGE JOURNEY", color = Orange, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Spacer(Modifier.height(8.dp))
                Text("Ideas worth reading.\nKnowledge worth keeping.", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 32.sp)
                Spacer(Modifier.height(9.dp))
                Text("Discover new perspectives, one story at a time.", color = Color(0xFFDCE5FF), fontSize = 13.sp)
                Spacer(Modifier.height(15.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoStories, null, tint = Orange, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Read · Explore · Save · Learn", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item {
            OutlinedTextField(
                value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search ideas, topics and stories") },
                leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White)
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(feedCategories) { category ->
                    FilterChip(selected = selectedFeedCategory == category, onClick = { onFeedCategoryChange(category) }, label = { Text(category) })
                }
            }
        }
        item {
            SectionTitle(if (selectedFeedCategory == "For You") "Editor's picks" else selectedFeedCategory,
                if (selectedTopics.isEmpty()) "Handpicked ideas for curious minds" else "Your interests · " + selectedTopics.take(3).joinToString(" · "))
        }
        if (filtered.isNotEmpty()) {
            item {
                val featured = filtered.first()
                Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).clickable { onOpenStory(featured) }) {
                    Box(Modifier.fillMaxWidth().height(132.dp).background(Blue, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.AutoStories, null, tint = Color.White, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(5.dp))
                            Text("FEATURED READING", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        }
                    }
                    Column(Modifier.padding(16.dp)) {
                        Text(featured.category, color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(featured.title, color = Navy, fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(6.dp))
                        Text(featured.summary, color = Color.DarkGray, fontSize = 13.sp, lineHeight = 19.sp, maxLines = 3)
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(featured.readTime, color = Color.Gray, fontSize = 11.sp)
                            Spacer(Modifier.weight(1f))
                            Text("Start reading →", color = Blue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
            items(filtered.drop(1)) { story ->
                StoryCard(story, story.title in saved, onSave = { onSave(story.title) }, onOpen = { onOpenStory(story) })
            }
        } else {
            item { Text("No matching reads yet. Try a different category or search.", color = Color.Gray) }
        }
        item { Text("Curiosity has no finish line.", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp)) }
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
private fun StoryCard(story: Story, isSaved: Boolean, onSave: () -> Unit, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp)).clickable { onOpen() }.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.background(Color(0xFFEAF0FF), RoundedCornerShape(8.dp)).padding(horizontal = 9.dp, vertical = 5.dp)) {
                Text(story.category, color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .6.sp)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSave, modifier = Modifier.size(34.dp)) {
                Icon(if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, contentDescription = if (isSaved) "Remove bookmark" else "Save article", tint = Blue)
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(story.title, color = Navy, fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp)
        Spacer(Modifier.height(7.dp))
        Text(story.summary, color = Color(0xFF4B5563), fontSize = 13.sp, lineHeight = 20.sp, maxLines = 3)
        Spacer(Modifier.height(13.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Schedule, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(story.readTime, color = Color.Gray, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(if (isSaved) "Saved" else "Read story", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(5.dp))
            Icon(Icons.Filled.ArrowForward, null, tint = Blue, modifier = Modifier.size(15.dp))
        }
    }
}
@Composable
private fun ArticleReaderScreen(
    story: Story,
    isSaved: Boolean,
    initialProgress: Int,
    onProgressChange: (Int) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    val readerListState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = initialProgress.coerceIn(0, 2))
    LaunchedEffect(readerListState) {
        androidx.compose.runtime.snapshotFlow { readerListState.firstVisibleItemIndex }
            .collect { onProgressChange(it) }
    }
    var fontSize by remember { mutableFloatStateOf(17f) }
    var readingTheme by remember { mutableStateOf(0) }
    val backgrounds = listOf(Page, Color(0xFF17191C), Color(0xFFF2E8D5))
    val foreground = if (readingTheme == 1) Color(0xFFE8E8E8) else Navy
    val bodyColor = if (readingTheme == 1) Color(0xFFCCCCCC) else Color(0xFF303846)
    Column(Modifier.fillMaxSize().background(backgrounds[readingTheme])) {
        Row(Modifier.fillMaxWidth().background(if (readingTheme == 1) Color(0xFF222429) else Color.White).padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = foreground) }
            Text("UPDATES READER", color = foreground, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onSave) { Icon(if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, "Bookmark", tint = Blue) }
            IconButton(onClick = { readingTheme = (readingTheme + 1) % backgrounds.size }) { Icon(Icons.Filled.Palette, "Reading theme", tint = foreground) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Text size", color = bodyColor, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { if (fontSize > 14f) fontSize -= 1f }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Remove, "Decrease text", tint = Blue) }
            Text("${fontSize.toInt()} sp", color = bodyColor, fontSize = 12.sp)
            IconButton(onClick = { if (fontSize < 26f) fontSize += 1f }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Add, "Increase text", tint = Blue) }
        }
        LazyColumn(state = readerListState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text(story.category, color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Text(story.title, color = foreground, fontSize = 29.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Schedule, null, tint = bodyColor, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(story.readTime, color = bodyColor, fontSize = 12.sp)
                }
                HorizontalDivider(color = if (readingTheme == 1) Color.DarkGray else Color(0xFFDCE3EF))
            }
            item {
                Text(story.summary, color = bodyColor, fontSize = fontSize.sp, lineHeight = (fontSize * 1.65f).sp)
                Spacer(Modifier.height(5.dp))
                Text("Why this topic matters", color = foreground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Knowledge grows when we stay curious, ask thoughtful questions and explore reliable information. This introduction is a starting point for understanding the topic and discovering the ideas behind it.", color = bodyColor, fontSize = fontSize.sp, lineHeight = (fontSize * 1.65f).sp)
                Text("Keep exploring", color = foreground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Use the Explore section to discover related subjects. Save this reading to return to it later.", color = bodyColor, fontSize = fontSize.sp, lineHeight = (fontSize * 1.65f).sp)
            }
            item {
                Row(Modifier.fillMaxWidth().background(if (readingTheme == 1) Color(0xFF24262A) else Color.White, RoundedCornerShape(14.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Bookmark, null, tint = Blue)
                    Spacer(Modifier.width(9.dp))
                    Text(if (isSaved) "Saved to your library" else "Save this reading to revisit later", color = bodyColor, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onSave) { Text(if (isSaved) "Saved" else "Save") }
                }
            }
        }
    }
}
@Composable
private fun ExploreScreen(onChooseCategory: (String) -> Unit) {
    val descriptions = mapOf(
        "Technology" to "AI, computers, software and innovation",
        "Science" to "Discoveries, experiments and the natural world",
        "Research" to "Studies, papers and new findings",
        "Education" to "Learning methods, skills and knowledge",
        "History" to "People, events and the past",
        "Environment" to "Climate, nature and sustainability",
        "Literature" to "Books, writing and storytelling",
        "Business" to "Ideas, markets and entrepreneurship",
        "Space" to "Astronomy, planets and the universe",
        "Health" to "Wellbeing and health science"
    )
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
        SectionTitle("Explore", "Choose a subject and discover something new")
        Spacer(Modifier.height(8.dp))
        Text("Explore by interest", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(Topics) { category ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onChooseCategory(category) },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(42.dp).background(Page, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.AutoStories, contentDescription = null, tint = Blue)
                        }
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(category, color = Navy, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(descriptions[category] ?: "Explore this subject", color = Color.Gray, fontSize = 11.sp)
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Open $category", tint = Blue)
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(savedStories: List<Story>, onOpenStory: (Story) -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        SectionTitle("My Library", "Your saved reading, all in one place")
        Spacer(Modifier.height(16.dp))
        if (savedStories.isEmpty()) {
            Text("Your library is empty. Save a story from Home to find it here.", color = Color.Gray)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(savedStories) { story -> StoryCard(story, true, onSave = {}, onOpen = { onOpenStory(story) }) }
            }
        }
    }
}

@Composable
private fun PersonalLibraryScreen(
    stories: List<Story>, savedTitles: Set<String>, history: List<String>,
    onToggleSaved: (String) -> Unit, onClearHistory: () -> Unit,
    onOpenStory: (Story) -> Unit, savedBooks: Set<String>, onToggleBook: (String) -> Unit
) {
    var section by remember { mutableStateOf("Saved") }
    val savedStories = stories.filter { it.title in savedTitles }
    val recentStories = history.mapNotNull { title -> stories.find { it.title == title } }
    Column(Modifier.fillMaxSize().background(Page).padding(horizontal = 18.dp, vertical = 16.dp)) {
        Text("My Library", color = Navy, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
        Text("Your personal space for saved and recent reading", color = Color.Gray, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Saved", "History", "Books").forEach { tab ->
                FilterChip(selected = section == tab, onClick = { section = tab }, label = { Text(tab) })
            }
        }
        Spacer(Modifier.height(12.dp))
        if (section == "History" && recentStories.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF0FF)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("CONTINUE READING", color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(recentStories.first().title, color = Navy, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    TextButton(onClick = { onOpenStory(recentStories.first()) }) { Text("Continue reading") }
                }
            }
        }
        when (section) {
            "Saved" -> {
                Text("${savedStories.size} saved articles", color = Blue, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                if (savedStories.isEmpty()) Text("No saved articles yet. Tap the bookmark icon on any story to keep it here.", color = Color.Gray, modifier = Modifier.padding(top = 20.dp))
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(savedStories) { story -> StoryCard(story, true, onSave = { onToggleSaved(story.title) }, onOpen = { onOpenStory(story) }) }
                }
            }
            "History" -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${recentStories.size} recently opened", color = Blue, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    if (recentStories.isNotEmpty()) TextButton(onClick = onClearHistory) { Text("Clear history") }
                }
                if (recentStories.isEmpty()) Text("Your reading history will appear here when you open an article.", color = Color.Gray, modifier = Modifier.padding(top = 20.dp))
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recentStories) { story -> StoryCard(story, story.title in savedTitles, onSave = { onToggleSaved(story.title) }, onOpen = { onOpenStory(story) }) }
                }
            }
            else -> {
                Column(Modifier.fillMaxWidth().weight(1f)) {
                    Text("${savedBooks.size} saved catalogue books", color = Blue, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    DigitalLibraryScreen(savedBooks, onToggleBook)
                }
            }
        }
    }
}

@Composable
private fun UserProfileScreen(
    displayName: String,
    profileBio: String,
    articleCount: Int,
    cloudProfileMessage: String,
    onSave: (String, String) -> Unit,
    onWrite: () -> Unit,
    onSignOut: () -> Unit
) {
    var name by remember(displayName) { mutableStateOf(displayName) }
    var bio by remember(profileBio) { mutableStateOf(profileBio) }
    var savedMessage by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Page).padding(20.dp)) {
        Text("My Profile", color = Navy, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
        Text("Your identity in the UPDATES community", color = Color.Gray, fontSize = 12.sp)
        Spacer(Modifier.height(22.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(86.dp).background(Navy, CircleShape), contentAlignment = Alignment.Center) {
                Text(name.trim().take(1).uppercase().ifBlank { "U" }, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(name.ifBlank { "Knowledge Explorer" }, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Navy, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("$articleCount community articles published", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Color.Gray, fontSize = 12.sp)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(value = name, onValueChange = { name = it; savedMessage = false }, modifier = Modifier.fillMaxWidth(), label = { Text("Display name") }, singleLine = true, shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = bio, onValueChange = { bio = it; savedMessage = false }, modifier = Modifier.fillMaxWidth(), label = { Text("About you") }, placeholder = { Text("Share a little about your interests") }, minLines = 3, maxLines = 4, shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(14.dp))
        Button(onClick = { onSave(name.trim().ifBlank { "Knowledge Explorer" }, bio.trim()); savedMessage = true }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Filled.Save, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Save profile")
        }
        if (savedMessage) Text("Profile saved on this device.", color = Color(0xFF16834A), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onWrite, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Filled.Edit, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Write an article")
        }
        Spacer(Modifier.height(14.dp))
        Text(cloudProfileMessage, color = Blue, fontSize = 11.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Filled.Logout, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Sign out")
        }
    }
}
