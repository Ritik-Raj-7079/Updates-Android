package com.updates.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
    initialArticles: List<CommunityArticle>,
    initialDisplayName: String,
    initialProfileBio: String,
    userId: String,
    userEmail: String,
    onSignOut: () -> Unit,
    onProfileChange: (String, String) -> Unit,
    onArticlesChange: (List<CommunityArticle>) -> Unit,
    onOnboardingComplete: (Set<String>) -> Unit
) {
    var showOnboarding by remember { mutableStateOf(showOnboardingInitially) }
    var selectedTopics by remember { mutableStateOf(initialTopics) }
    var selectedTab by remember { mutableStateOf("Home") }
    var query by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(setOf<String>()) }
    var savedBooks by remember { mutableStateOf(setOf<String>()) }
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
                onBack = { openedStory = null },
                onSave = {
                    val title = openedStory!!.title
                    saved = if (title in saved) saved - title else saved + title
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
                onSignOut = onSignOut,\n                cloudArticleMessage = cloudArticleMessage,\n                currentUserId = userId,\n                onSignOut = onSignOut,
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
                onSaveBook = { key -> savedBooks = if (key in savedBooks) savedBooks - key else savedBooks + key },
                onSave = { title ->
                    saved = if (title in saved) saved - title else saved + title
                },
                stories = stories,
                selectedTopics = selectedTopics,
                selectedFeedCategory = selectedFeedCategory,
                onFeedCategoryChange = { selectedFeedCategory = it },
                onExploreCategory = { category ->
                    selectedFeedCategory = category
                    selectedTab = "Home"
                },
                onOpenStory = { openedStory = it }
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("U", color = Color.White, fontSize = (size / 2.3).sp, fontWeight = FontWeight.ExtraBold, lineHeight = (size / 2.5).sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width((size / 3).dp).height(3.dp).background(Orange, RoundedCornerShape(4.dp)))
                Spacer(Modifier.width(4.dp))
                Box(Modifier.width((size / 3).dp).height(3.dp).background(Color.White, RoundedCornerShape(4.dp)))
            }
        }
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
                "Library" -> DigitalLibraryScreen(savedBooks, onSaveBook)
                "Profile" -> UserProfileScreen(displayName, profileBio, communityArticles.count { it.authorUid == currentUserId }, cloudProfileMessage, onProfileSave, { onTabSelected("Write") }, onSignOut)
                else -> ArticlePublishingScreen(communityArticles, displayName, onPublishArticle, onDeleteArticle, currentUserId, cloudArticleMessage)
            }
        }
    }
}

@Composable
private fun HomeScreen(
    stories: List<Story>,
    query: String,
    onQuery: (String) -> Unit,
    saved: Set<String>,
    onSave: (String) -> Unit,
    selectedTopics: Set<String>,
    selectedFeedCategory: String,
    onFeedCategoryChange: (String) -> Unit,
    onOpenStory: (Story) -> Unit,
    onProfileClick: () -> Unit
) {
    val feedCategories = listOf("For You", "News", "Research", "Stories") + Topics
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(42)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("UPDATES", color = Navy, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, letterSpacing = 1.sp)
                    Text("Build Your Own Treasure of Knowledge.", color = Color.Gray, fontSize = 10.sp)
                }
                IconButton(onClick = onProfileClick) {
                    Icon(Icons.Filled.AccountCircle, contentDescription = "Profile", tint = Navy)
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().background(Navy, RoundedCornerShape(22.dp)).padding(20.dp)) {
                Text("YOUR DAILY DOSE OF KNOWLEDGE", color = Orange, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                Text("Discover something\nnew every day.", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold, lineHeight = 31.sp)
                Spacer(Modifier.height(8.dp))
                Text("Ideas, discoveries and stories worth your time.", color = Color(0xFFDCE5FF), fontSize = 13.sp)
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search topics, stories and research") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
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
            SectionTitle(
                if (selectedFeedCategory == "For You") "Recommended for you" else selectedFeedCategory,
                if (selectedTopics.isEmpty()) "A little knowledge for every curious mind"
                else "Your interests: " + selectedTopics.take(3).joinToString(", ")
            )
        }
        val filtered = stories.filter {
            val matchesSearch = query.isBlank() || it.title.contains(query, true) || it.category.contains(query, true) || it.summary.contains(query, true)
            val matchesInterest = selectedFeedCategory != "For You" || selectedTopics.isEmpty() || it.category.lowercase() in selectedTopics.map { topic -> topic.lowercase() }
            val matchesFeed = when (selectedFeedCategory) {
                "For You" -> true
                "Stories" -> it.category == "HISTORY" || it.category == "LITERATURE"
                else -> it.category.equals(selectedFeedCategory, ignoreCase = true)
            }
            matchesSearch && matchesInterest && matchesFeed
        }
        if (filtered.isEmpty()) {
            item { Text("No matching stories yet. Try another search or update your interests.", color = Color.Gray) }
        } else {
            items(filtered) { story ->
                StoryCard(story, story.title in saved, onSave = { onSave(story.title) }, onOpen = { onOpenStory(story) })
            }
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
private fun StoryCard(story: Story, isSaved: Boolean, onSave: () -> Unit, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(story.category, color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onSave, modifier = Modifier.size(32.dp)) {
                Icon(if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, contentDescription = "Save story", tint = Blue)
            }
        }
        Text(story.title, color = Navy, fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp, modifier = Modifier.clickable { onOpen() })
        Spacer(Modifier.height(6.dp))
        Text(story.summary, color = Color.DarkGray, fontSize = 13.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(story.readTime, color = Color.Gray, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text("Read more  →", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onOpen() })
        }
    }
}

@Composable
private fun ArticleReaderScreen(story: Story, isSaved: Boolean, onBack: () -> Unit, onSave: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Page)) {
        Row(Modifier.fillMaxWidth().background(Color.White).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Navy) }
            Text("READING", color = Navy, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onSave) { Icon(if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, contentDescription = "Bookmark", tint = Blue) }
        }
        Column(Modifier.fillMaxSize().padding(22.dp)) {
            Text(story.category, color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(12.dp))
            Text(story.title, color = Navy, fontSize = 27.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(12.dp))
            Text(story.readTime, color = Color.Gray, fontSize = 12.sp)
            Spacer(Modifier.height(22.dp))
            HorizontalDivider(color = Color(0xFFE3E8F2))
            Spacer(Modifier.height(22.dp))
            Text(story.summary, color = Color.DarkGray, fontSize = 16.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(18.dp))
            Text("Why this topic matters", color = Navy, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Knowledge grows when we stay curious, ask thoughtful questions and explore reliable information. This introduction is a starting point for understanding the topic and discovering the ideas behind it.", color = Color.DarkGray, fontSize = 15.sp, lineHeight = 25.sp)
            Spacer(Modifier.height(16.dp))
            Text("Keep exploring", color = Navy, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Use the Explore section to discover related subjects. Save this reading to return to it later.", color = Color.DarkGray, fontSize = 15.sp, lineHeight = 25.sp)
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
        }\n        Spacer(Modifier.height(16.dp))\n        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) {\n            Icon(Icons.Filled.Logout, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Sign out")\n        }
    }
}
