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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val Navy = Color(0xFF263238)
private val Blue = Color(0xFF8FA99A)
private val Orange = Color(0xFFC8795D)
private val Page = Color(0xFFF6F3E9)
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

private data class StorySection(val title: String, val body: String)

private data class Story(
    val category: String,
    val title: String,
    val summary: String,
    val readTime: String,
    val sections: List<StorySection> = emptyList()
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
        Story("TECHNOLOGY", "How artificial intelligence is changing everyday life", "A beginner-friendly look at the ideas and tools shaping the next generation of technology.", "5 min read", listOf(
            StorySection("What AI means", "Artificial intelligence (AI) is a broad field of computing that builds systems able to perform tasks that usually require human-like abilities, such as recognizing patterns, understanding language and making predictions. Most modern applications learn statistical patterns from examples rather than following only a fixed list of instructions."),
            StorySection("AI around us", "Recommendation systems help organize music, videos and shopping suggestions. Maps estimate travel times, email services filter unwanted messages, and phones use AI for speech recognition and photography. These tools can save time, but their results are not always correct and should be checked when decisions matter."),
            StorySection("Opportunities and responsibility", "AI can support medical research, accessibility, education and scientific discovery. It can also produce errors, reflect unfair patterns in training data or raise privacy concerns. Responsible use means protecting personal information, checking important claims, keeping people involved in high-impact decisions and understanding the limits of each tool.")
        )),
        Story("SCIENCE", "The science of a healthier planet", "Explore how researchers are studying climate, energy and sustainable solutions.", "7 min read", listOf(
            StorySection("Understanding the challenge", "Earth's climate is influenced by energy from the Sun, the atmosphere, oceans, land and living systems. Human activities add greenhouse gases such as carbon dioxide and methane to the atmosphere. These gases slow the escape of heat into space, contributing to long-term warming and changes in weather patterns."),
            StorySection("Measuring change", "Scientists study the climate using weather stations, ocean buoys, satellites, ice cores and computer models. No single measurement tells the whole story. Researchers compare many independent records over long periods to identify trends, understand regional differences and estimate possible future conditions."),
            StorySection("Solutions and adaptation", "Cleaner electricity, efficient buildings, public transport, ecosystem protection and reduced waste can lower environmental pressures. Communities also prepare for impacts through heat plans, flood management and resilient infrastructure. Effective solutions combine reliable evidence, practical design and cooperation across society.")
        )),
        Story("RESEARCH", "Inside the world of open scientific research", "Learn how research papers are shared, reviewed and used to build new knowledge.", "8 min read", listOf(
            StorySection("From question to study", "Research often begins with a focused question. A team reviews earlier work, chooses a method, gathers observations or data, and analyzes the results. A clear method helps other researchers understand what was done and judge whether the evidence supports the conclusions."),
            StorySection("Publication and peer review", "Researchers may submit a paper to a scholarly journal, where editors and reviewers assess its relevance, methods and clarity. Peer review can improve a paper, but it does not guarantee that every result is correct. Readers should still consider sample size, limitations, conflicts of interest and whether other studies reach similar findings."),
            StorySection("Open access and careful reading", "Open access makes research outputs available to read without a subscription, although reuse terms can differ. Abstracts provide a quick overview, while methods, results and limitations give essential context. A single paper is one contribution to a larger body of evidence, not automatically a final answer.")
        )),
        Story("EDUCATION", "Small learning habits that make a big difference", "Practical ways to stay curious, organize ideas and keep learning every day.", "4 min read", listOf(
            StorySection("Make learning active", "Learning becomes more durable when you do something with new information. After reading a topic, close the material and explain the main idea in your own words. Solving a small problem, drawing a concept map or teaching the idea to someone else can reveal what you understand and what needs more practice."),
            StorySection("Space and revisit practice", "Short, focused study sessions with breaks can make it easier to maintain attention. Revisiting a topic over several days is generally more effective for long-term memory than trying to learn everything in one sitting. A simple review schedule can include a quick recap the next day and another review later in the week."),
            StorySection("Track understanding", "Keep notes of questions, examples and mistakes rather than copying every sentence. At the end of a session, write down what you can now explain and what remains unclear. Adjust your next session around those gaps, and remember that sleep, rest and a balanced routine support learning.")
        )),
        Story("HISTORY", "How ancient ideas shaped the modern world", "Discover the connections between historical discoveries and life today.", "6 min read", listOf(
            StorySection("Ideas travel across cultures", "Knowledge has rarely developed in isolation. Trade, migration, translation and travel helped mathematics, medicine, astronomy, engineering and philosophy move between societies. As ideas crossed languages and regions, scholars adapted them to new problems and combined them with local knowledge."),
            StorySection("Records preserve discoveries", "Written manuscripts, inscriptions, maps, tools and buildings provide evidence about earlier societies. Historians compare different sources because each record reflects the purpose and viewpoint of its creator. Archaeology adds material evidence that can confirm, complicate or challenge written accounts."),
            StorySection("The past and the present", "Modern institutions and technologies often have long histories. Number systems, calendars, navigation methods, civic ideas and learning traditions changed over centuries. Studying these developments helps explain both continuity and change, while reminding us to understand historical people within the context of their own time.")
        )),
        Story("SPACE", "A closer look at distant galaxies", "Explore how astronomers study objects far beyond our solar system.", "6 min read", listOf(
            StorySection("What a galaxy contains", "A galaxy is a large system of stars, gas, dust and dark matter held together by gravity. Galaxies come in different shapes, including spiral, elliptical and irregular forms. Our Solar System is located in the Milky Way, a spiral galaxy containing an enormous number of stars."),
            StorySection("How astronomers observe them", "Telescopes collect light and other forms of electromagnetic radiation from distant objects. Different wavelengths reveal different features: visible light shows many stars, infrared observations can look through dust, and radio observations reveal gas and energetic processes. Spectra help scientists estimate composition, temperature and motion."),
            StorySection("Looking back in time", "Light takes time to travel, so observing a very distant galaxy means seeing it as it was in the past. By comparing galaxies at different distances, astronomers study how galaxies formed and changed over cosmic history. Measurements have uncertainties, so conclusions are tested against multiple observations and models.")
        )),
        Story("ENVIRONMENT", "Everyday choices for a cleaner environment", "Discover practical ideas for reducing waste, saving resources and understanding environmental challenges.", "5 min read", listOf(
            StorySection("Think about resources", "Products require materials, energy, water and transport before they reach consumers. Considering the full life cycle helps explain why reducing unnecessary purchases and choosing durable, repairable items can reduce environmental impacts. Reuse and sharing can also extend the useful life of products."),
            StorySection("Waste and recycling", "Separating waste correctly makes recycling systems more effective, but not every material is accepted everywhere. Local rules matter. Preventing waste in the first place, avoiding unnecessary packaging and composting suitable organic waste can reduce the amount sent to disposal sites."),
            StorySection("Small actions, shared systems", "Saving electricity and water, choosing lower-impact travel where practical and protecting local green spaces are useful steps. Individual choices matter most when supported by wider systems such as clean energy, accessible public transport and responsible product design. Community participation can help improve those systems.")
        )),
        Story("LITERATURE", "Why stories remain powerful across generations", "Explore how books and storytelling preserve ideas, cultures and human experiences.", "6 min read", listOf(
            StorySection("Stories carry experience", "Stories allow readers to encounter places, decisions and perspectives beyond their own lives. Through characters and events, literature can explore friendship, conflict, identity, justice, courage and change. Different readers may interpret the same work in different ways because they bring different experiences to it."),
            StorySection("Many forms of literature", "Literature includes novels, short stories, poetry, drama, essays, oral traditions and many other forms. Each form uses language differently. Poetry may rely on rhythm and imagery, while a novel can develop characters and ideas across a longer narrative. Oral storytelling also preserves knowledge through performance and memory."),
            StorySection("Reading with attention", "Noticing setting, point of view, structure, repeated images and word choice can deepen understanding. Historical and cultural context can explain why a work mattered to its first audience, while discussion helps reveal interpretations that an individual reader may miss.")
        )),
        Story("BUSINESS", "How small businesses turn ideas into value", "A simple introduction to customers, planning, innovation and responsible business growth.", "5 min read", listOf(
            StorySection("Start with a real need", "A business creates value by offering a product or service that people find useful. Before investing heavily, a founder can learn about potential customers, existing alternatives and the problem people want solved. Listening to feedback helps distinguish a promising idea from an assumption."),
            StorySection("Plan the basics", "A practical plan describes the customer, the offer, pricing, costs, suppliers and how the business will reach people. Tracking cash flow is essential because a business can appear profitable on paper while still lacking money to pay bills on time. Clear records support better decisions."),
            StorySection("Build trust over time", "Reliable quality, honest communication, fair treatment and responsive service help create lasting customer relationships. Businesses should follow applicable laws, protect customer information and review performance regularly. Sustainable growth comes from learning and improving, not simply increasing sales as quickly as possible.")
        )),
        Story("HEALTH", "Understanding the science of good sleep", "Learn why sleep matters for learning, focus and everyday wellbeing.", "4 min read", listOf(
            StorySection("Why sleep matters", "Sleep is an active biological process. It supports memory, attention, emotional regulation and many functions involved in physical health. Sleep needs vary with age and individual circumstances, and regularly getting too little sleep can make everyday learning and concentration more difficult."),
            StorySection("The body clock", "The body's circadian rhythm helps coordinate periods of alertness and sleep across roughly a day. Light exposure, daily routines and timing of activities can influence this rhythm. A consistent sleep and wake schedule can help the body maintain a predictable pattern."),
            StorySection("Supportive habits", "A calm, comfortable sleep environment and a regular wind-down routine can make rest easier. Limiting bright screens close to bedtime may help some people, especially when device use delays sleep. Persistent sleep difficulties are worth discussing with a trusted adult or qualified health professional.")
        )),
        Story("NEWS", "How to read the news with a critical mind", "Learn to check sources, compare reports and separate verified information from opinion.", "4 min read", listOf(
            StorySection("Check the source", "Look at who published a report, who wrote it and when it appeared. A clear author, date and link to original evidence make a story easier to evaluate. Be cautious when a post gives no source, uses an unfamiliar account or makes a dramatic claim without supporting information."),
            StorySection("Separate evidence from interpretation", "News reports may include confirmed facts, expert explanations, estimates and opinions. These are not interchangeable. Read beyond the headline, check whether numbers have context and see whether a quote represents the original speaker accurately."),
            StorySection("Compare and verify", "For important or surprising claims, compare coverage from multiple credible outlets and look for primary documents or statements. Images and videos can be old or taken out of context. Pausing before sharing gives time to verify details and reduces the spread of misinformation.")
        ))
    )

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Blue,
            onPrimary = Color(0xFF263238),
            secondary = Orange,
            background = Page,
            surface = Color(0xFFFFFEFA),
            onSurface = Navy
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
                storyProgress = storyProgress,
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
            BrandLockup(size = 104)
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
private fun BrandLockup(size: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.updates_mark),
            contentDescription = "Updates open-book logo",
            modifier = Modifier.size(size.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            "Updates",
            color = Navy,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
            fontSize = (size * 0.43f).sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1.2).sp
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
    storyProgress: Map<String, Int>,
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
                listOf("Home", "Explore", "News", "Research", "Stats", "Library", "Profile").forEach { tab ->
                    val icon = when (tab) {
                        "Home" -> Icons.Filled.Home
                        "Explore" -> Icons.Filled.Explore
                        "News" -> Icons.Filled.Article
                        "Research" -> Icons.Filled.Science
                        "Stats" -> Icons.Filled.Insights
                        "Library" -> Icons.Filled.Bookmarks
                        "Profile" -> Icons.Filled.Person
                        else -> Icons.Filled.Edit
                    }
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { onTabSelected(tab) },
                        icon = { Icon(icon, contentDescription = tab) },
                        label = { Text(tab) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFF536F60), selectedTextColor = Color(0xFF536F60), indicatorColor = Color(0xFFE5EBE2))
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                "Home" -> HomeScreen(stories, query, onQueryChange, saved, onSave, selectedTopics, readingHistory, storyProgress, selectedFeedCategory, onFeedCategoryChange, onOpenStory, { onTabSelected("Profile") }, { onTabSelected("News") }, { onTabSelected("Research") })
                "Explore" -> KnowledgeExploreScreen(onExploreCategory)
                "News" -> NewsScreen()
                "Research" -> ResearchScreen()
                "Stats" -> ReadingStatisticsScreen(stories, readingHistory, storyProgress, saved)
                "Library" -> PersonalLibraryScreen(stories, saved, readingHistory, onSave, onClearHistory, onOpenStory, savedBooks, onSaveBook)
                "Profile" -> UserProfileScreen(displayName, profileBio, communityArticles.count { it.authorUid == currentUserId }, cloudProfileMessage, onProfileSave, { onTabSelected("Write") }, onSignOut)
                else -> ArticlePublishingScreen(communityArticles, displayName, onPublishArticle, onDeleteArticle, currentUserId, cloudArticleMessage)
            }
        }
    }
}

@Composable
private fun ReadingStatisticsScreen(
    stories: List<Story>, readingHistory: List<String>, storyProgress: Map<String, Int>, saved: Set<String>
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("updates_preferences", android.content.Context.MODE_PRIVATE) }
    val activeDates = remember { prefs.getStringSet("reading_active_dates", emptySet())?.toSet() ?: emptySet() }
    fun dateKey(daysAgo: Int): String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(
        java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -daysAgo) }.time
    )
    val week = remember { (6 downTo 0).map { daysAgo ->
        val calendar = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -daysAgo) }
        Triple(dateKey(daysAgo), java.text.SimpleDateFormat("EEE", java.util.Locale.US).format(calendar.time), calendar.get(java.util.Calendar.DAY_OF_MONTH))
    } }
    val monthActive = (0 until 30).count { dateKey(it) in activeDates }
    val completed = storyProgress.count { (title, position) ->
        val story = stories.firstOrNull { it.title == title }
        story != null && position >= story.sections.size + 1
    }
    val totalReadingMs = remember(stories) {
        stories.sumOf { story -> prefs.getLong("reading_time_ms_" + story.title, 0L) }
    }
    val totalMinutes = totalReadingMs / 60000L
    val formattedReadingTime = if (totalMinutes >= 60) "${totalMinutes / 60}h ${totalMinutes % 60}m" else "${totalMinutes}m"
    val recent = readingHistory.take(8)
    LazyColumn(Modifier.fillMaxSize().background(Page), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Reading Statistics", color = Navy, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("A clear view of your learning activity", color = Color.Gray, fontSize = 13.sp)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f).background(Color(0xFFE8EEE7), RoundedCornerShape(16.dp)).padding(14.dp)) {
                    Text(readingHistory.size.toString(), color = Navy, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("Stories opened", color = Color.DarkGray, fontSize = 11.sp)
                }
                Column(Modifier.weight(1f).background(Color(0xFFF3E8E1), RoundedCornerShape(16.dp)).padding(14.dp)) {
                    Text(completed.toString(), color = Navy, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("Completed", color = Color.DarkGray, fontSize = 11.sp)
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().background(Color(0xFFE8EEE7), RoundedCornerShape(16.dp)).padding(16.dp)) {
                Text("TOTAL READING TIME", color = Color(0xFF536F60), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(formattedReadingTime, color = Navy, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("Time counted while the reader is open and the app is in the foreground.", color = Color.Gray, fontSize = 10.sp)
            }
        }
        item {
            Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp)).padding(16.dp)) {
                Text("LAST 7 DAYS", color = Color(0xFF536F60), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(12.dp))
                week.forEach { day ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(day.second, modifier = Modifier.width(42.dp), color = Color.DarkGray, fontSize = 11.sp)
                        Box(Modifier.weight(1f).height(9.dp).background(Color(0xFFE8E8E3), RoundedCornerShape(8.dp))) {
                            if (day.first in activeDates) Box(Modifier.fillMaxWidth().height(9.dp).background(Blue, RoundedCornerShape(8.dp)))
                        }
                        Text(if (day.first in activeDates) "Active" else "—", modifier = Modifier.width(48.dp), textAlign = TextAlign.End, color = if (day.first in activeDates) Color(0xFF536F60) else Color.Gray, fontSize = 10.sp)
                    }
                    Spacer(Modifier.height(9.dp))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f).background(Color.White, RoundedCornerShape(16.dp)).padding(14.dp)) {
                    Text(monthActive.toString(), color = Navy, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Active days · last 30", color = Color.Gray, fontSize = 10.sp)
                }
                Column(Modifier.weight(1f).background(Color.White, RoundedCornerShape(16.dp)).padding(14.dp)) {
                    Text(saved.size.toString(), color = Navy, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Saved stories", color = Color.Gray, fontSize = 10.sp)
                }
            }
        }
        item {
            Text("RECENTLY OPENED", color = Color(0xFF536F60), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        if (recent.isEmpty()) item {
            Text("Your reading history will appear here as you explore stories.", color = Color.Gray, fontSize = 13.sp)
        } else items(recent) { title ->
            val story = stories.firstOrNull { it.title == title }
            Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(14.dp)).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = Navy, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2)
                    if (story != null) Text(story.category, color = Color.Gray, fontSize = 10.sp)
                }
                Text(if (storyProgress.containsKey(title)) "Progress saved" else "Opened", color = Color(0xFF536F60), fontSize = 10.sp)
            }
        }
        item { Text("Activity is based on recorded story opens. Reading time is not estimated when reliable timing data is unavailable.", color = Color.Gray, fontSize = 10.sp, lineHeight = 15.sp) }
    }
}
@Composable
private fun HomeScreen(
    stories: List<Story>, query: String, onQuery: (String) -> Unit,
    saved: Set<String>, onSave: (String) -> Unit, selectedTopics: Set<String>, readingHistory: List<String>, storyProgress: Map<String, Int>,
    selectedFeedCategory: String, onFeedCategoryChange: (String) -> Unit,
    onOpenStory: (Story) -> Unit, onProfileClick: () -> Unit,
    onNewsClick: () -> Unit, onResearchClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val goalPrefs = remember { context.getSharedPreferences("updates_preferences", android.content.Context.MODE_PRIVATE) }
    val todayKey = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) }
    var dailyGoal by remember { mutableIntStateOf(goalPrefs.getInt("daily_reading_goal", 3).coerceIn(1, 10)) }
    var activeDates by remember { mutableStateOf(goalPrefs.getStringSet("reading_active_dates", emptySet())?.toSet() ?: emptySet()) }
    fun dateKey(daysAgo: Int): String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(
        java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -daysAgo) }.time
    )
    val streakOffset = if (todayKey in activeDates) 0 else 1
    val currentStreak = remember(todayKey, activeDates) {
        var count = 0
        while (dateKey(streakOffset + count) in activeDates && count < 366) count++
        count
    }
    val weekDays = remember(todayKey) {
        (6 downTo 0).map { daysAgo ->
            val calendar = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -daysAgo) }
            Triple(dateKey(daysAgo), java.text.SimpleDateFormat("EE", java.util.Locale.US).format(calendar.time).take(1), calendar.get(java.util.Calendar.DAY_OF_MONTH))
        }
    }
    var dailyOpened by remember {
        val storedDate = goalPrefs.getString("daily_open_date", "") ?: ""
        mutableIntStateOf(if (storedDate == todayKey) goalPrefs.getInt("daily_open_count", 0) else 0)
    }
    val trackOpen: (Story) -> Unit = { story ->
        if ((goalPrefs.getString("daily_open_date", "") ?: "") != todayKey) {
            dailyOpened = 0
            goalPrefs.edit().putString("daily_open_date", todayKey).putInt("daily_open_count", 0).apply()
        }
        dailyOpened += 1
        activeDates = (activeDates + todayKey).toSet()
        goalPrefs.edit().putInt("daily_open_count", dailyOpened)
            .putStringSet("reading_active_dates", activeDates.take(366).toSet()).apply()
        onOpenStory(story)
    }
    val completedCount = storyProgress.count { (title, position) ->
        val story = stories.firstOrNull { it.title == title }
        story != null && position >= story.sections.size + 1
    }
    val completionRate = if (readingHistory.isEmpty()) 0 else ((completedCount * 100) / readingHistory.size).coerceIn(0, 100)
    val filtered = stories.filter { query.isBlank() || it.title.contains(query,true) || it.category.contains(query,true) || it.summary.contains(query,true) }
    val personalized = stories
        .filter { it.title !in readingHistory }
        .sortedByDescending { story ->
            val topicMatch = selectedTopics.any { topic ->
                story.category.equals(topic, ignoreCase = true) ||
                    story.title.contains(topic, ignoreCase = true) ||
                    story.summary.contains(topic, ignoreCase = true)
            }
            val savedMatch = saved.any { savedTitle ->
                stories.firstOrNull { it.title == savedTitle }?.category.equals(story.category, ignoreCase = true)
            }
            (if (topicMatch) 3 else 0) + (if (savedMatch) 1 else 0)
        }
        .filter { story ->
            selectedTopics.isEmpty() || selectedTopics.any { topic ->
                story.category.equals(topic, ignoreCase = true) ||
                    story.title.contains(topic, ignoreCase = true) ||
                    story.summary.contains(topic, ignoreCase = true)
            } || saved.any { savedTitle ->
                stories.firstOrNull { it.title == savedTitle }?.category.equals(story.category, ignoreCase = true)
            }
        }
        .take(6)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            val inProgressCount = storyProgress.count { (title, position) ->
                val story = stories.firstOrNull { it.title == title }
                story != null && position > 0 && position < story.sections.size + 1
            }
            Column(Modifier.fillMaxWidth().background(Color(0xFFE8EEE7), RoundedCornerShape(18.dp)).padding(16.dp)) {
                Text("YOUR READING JOURNEY", color = Color(0xFF536F60), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(6.dp))
                Text("Every page is progress.", color = Navy, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text(readingHistory.size.toString(), color = Navy, fontSize = 22.sp, fontWeight = FontWeight.Bold); Text("Stories opened", color = Color.DarkGray, fontSize = 10.sp) }
                    Column { Text(saved.size.toString(), color = Navy, fontSize = 22.sp, fontWeight = FontWeight.Bold); Text("Saved stories", color = Color.DarkGray, fontSize = 10.sp) }
                    Column { Text(inProgressCount.toString(), color = Navy, fontSize = 22.sp, fontWeight = FontWeight.Bold); Text("In progress", color = Color.DarkGray, fontSize = 10.sp) }
                }
                Spacer(Modifier.height(8.dp))
                Text("Based on your reading activity on this device and synced account.", color = Color(0xFF536F60), fontSize = 10.sp)
            }
        }
        item {
            Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp)).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("DAILY READING GOAL", color = Color(0xFF536F60), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text("$dailyOpened of $dailyGoal stories opened today", color = Navy, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { dailyGoal = (dailyGoal - 1).coerceAtLeast(1); goalPrefs.edit().putInt("daily_reading_goal", dailyGoal).apply() }, modifier = Modifier.size(34.dp)) { Icon(Icons.Filled.Remove, "Decrease daily goal", tint = Blue) }
                        Text("$dailyGoal", color = Navy, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { dailyGoal = (dailyGoal + 1).coerceAtMost(10); goalPrefs.edit().putInt("daily_reading_goal", dailyGoal).apply() }, modifier = Modifier.size(34.dp)) { Icon(Icons.Filled.Add, "Increase daily goal", tint = Blue) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { (dailyOpened.toFloat() / dailyGoal).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(7.dp), color = Blue, trackColor = Color(0xFFE5EBE2))
                Spacer(Modifier.height(5.dp))
                Text(if (dailyOpened >= dailyGoal) "Daily goal reached — great progress!" else "Open " + (dailyGoal - dailyOpened) + " more stories to reach the daily goal.", color = Color.Gray, fontSize = 11.sp)
            }
        }
        item {
            Column(Modifier.fillMaxWidth().background(Color(0xFFFFF8F2), RoundedCornerShape(18.dp)).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("READING STREAK", color = Orange, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text("$currentStreak ${if (currentStreak == 1) "day" else "days"} in a row", color = Navy, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Text("Open a story each day to build your learning habit.", color = Color.Gray, fontSize = 11.sp)
                    }
                    Icon(Icons.Filled.LocalFireDepartment, contentDescription = "Reading streak", tint = Orange, modifier = Modifier.size(34.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text("LAST 7 DAYS", color = Color(0xFF536F60), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    weekDays.forEach { day ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(30.dp).background(if (day.first in activeDates) Blue else Color(0xFFE8E8E3), CircleShape), contentAlignment = Alignment.Center) {
                                if (day.first in activeDates) Icon(Icons.Filled.Check, contentDescription = "Reading activity", tint = Color.White, modifier = Modifier.size(16.dp))
                                else Text(day.third.toString(), color = Color.Gray, fontSize = 10.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(day.second, color = Color.Gray, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp)).padding(16.dp)) {
                Text("READING INSIGHTS", color = Color(0xFF536F60), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text(completedCount.toString(), color = Navy, fontSize = 23.sp, fontWeight = FontWeight.Bold); Text("Completed", color = Color.Gray, fontSize = 11.sp) }
                    Column { Text("$completionRate%", color = Navy, fontSize = 23.sp, fontWeight = FontWeight.Bold); Text("Completion rate", color = Color.Gray, fontSize = 11.sp) }
                    Column { Text(storyProgress.size.toString(), color = Navy, fontSize = 23.sp, fontWeight = FontWeight.Bold); Text("Progress saved", color = Color.Gray, fontSize = 11.sp) }
                }
                Spacer(Modifier.height(8.dp))
                Text("Completion is based on reaching the final section in the reader. Your reading progress is saved automatically.", color = Color.Gray, fontSize = 10.sp, lineHeight = 15.sp)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value=query,onValueChange=onQuery,modifier=Modifier.weight(1f),placeholder={Text("Search stories")},leadingIcon={Icon(Icons.Filled.Search,null)},singleLine=true,shape=RoundedCornerShape(18.dp))
                IconButton(onClick=onProfileClick) { Icon(Icons.Filled.NotificationsNone,contentDescription="Notifications",tint=Navy) }
            }
        }
        item {
            Text("Featured",color=Navy,fontFamily=androidx.compose.ui.text.font.FontFamily.Serif,fontSize=24.sp,fontWeight=FontWeight.Bold)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                items(filtered.take(4)) { story ->
                    Column(Modifier.width(290.dp).height(210.dp).background(Color(0xFF8FA99A),RoundedCornerShape(22.dp)).clickable{trackOpen(story)}.padding(18.dp),verticalArrangement=Arrangement.SpaceBetween) {
                        Column {
                            Text(story.category.uppercase(),color=Color(0xFF263238),fontSize=11.sp,fontWeight=FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(story.title,color=Color(0xFF263238),fontFamily=androidx.compose.ui.text.font.FontFamily.Serif,fontSize=22.sp,fontWeight=FontWeight.Bold,lineHeight=27.sp,maxLines=3)
                        }
                        Button(onClick={trackOpen(story)},colors=ButtonDefaults.buttonColors(containerColor=Orange),shape=RoundedCornerShape(12.dp)) { Text("Read Now") }
                    }
                }
            }
        }
        if (personalized.isNotEmpty()) {
            item {
                SectionTitle("Picked for You", if (selectedTopics.isEmpty()) "Fresh ideas to continue your discovery" else "Based on your selected interests")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    items(personalized) { story ->
                        Column(
                            Modifier.width(205.dp).height(175.dp)
                                .background(Color(0xFFF3E8E1), RoundedCornerShape(17.dp))
                                .clickable { trackOpen(story) }.padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(story.category, color = Orange, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
                                Spacer(Modifier.height(7.dp))
                                Text(story.title, color = Navy, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 21.sp, maxLines = 3)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(story.readTime, color = Color.DarkGray, fontSize = 10.sp)
                                Spacer(Modifier.weight(1f))
                                Text("Read  →", color = Orange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        item {
            SectionTitle("Trending Stories","Popular reads for curious minds")
            LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                items(filtered.take(8)) { story ->
                    Column(Modifier.width(185.dp).height(215.dp).background(Color(0xFFE2E8DD),RoundedCornerShape(18.dp)).clickable{trackOpen(story)}.padding(13.dp),verticalArrangement=Arrangement.SpaceBetween) {
                        Box(Modifier.fillMaxWidth().height(80.dp).background(Color(0xFFB9C8BA),RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center) { Icon(Icons.Filled.AutoStories,null,tint=Color(0xFF536F60),modifier=Modifier.size(30.dp)) }
                        Column {
                            Text(story.category,color=Color(0xFF718B7C),fontSize=10.sp,fontWeight=FontWeight.Bold)
                            Text(story.title,color=Navy,fontFamily=androidx.compose.ui.text.font.FontFamily.Serif,fontSize=18.sp,lineHeight=21.sp,maxLines=3)
                        }
                    }
                }
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth()
                    .background(Color(0xFFE8EEE7), RoundedCornerShape(18.dp))
                    .clickable(onClick = onNewsClick)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Public, contentDescription = null, tint = Color(0xFF536F60))
                    Spacer(Modifier.width(8.dp))
                    Text("GLOBAL TECH NEWS", color = Color(0xFF536F60), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Filled.ArrowForward, contentDescription = "Open news", tint = Color(0xFF536F60))
                }
                Spacer(Modifier.height(7.dp))
                Text("Stay curious about what's happening", color = Navy, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("Read current headlines across technology, science, India and the world.", color = Color.DarkGray, fontSize = 12.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(10.dp))
                HomeNewsPreview(onNewsClick)
                Spacer(Modifier.height(8.dp))
                Text("Explore latest news  →", color = Color(0xFF536F60), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth()
                    .background(Color(0xFFF3E8E1), RoundedCornerShape(18.dp))
                    .clickable(onClick = onResearchClick)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Science, contentDescription = null, tint = Orange)
                    Spacer(Modifier.width(8.dp))
                    Text("RESEARCH & DISCOVERY", color = Orange, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Filled.ArrowForward, contentDescription = "Open research", tint = Orange)
                }
                Spacer(Modifier.height(7.dp))
                Text("Ideas, papers and new perspectives", color = Navy, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("Search scholarly works, explore authors and discover new research from around the world.", color = Color.DarkGray, fontSize = 12.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(10.dp))
                HomeResearchPreview(onResearchClick)
                Spacer(Modifier.height(8.dp))
                Text("Explore research papers  →", color = Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        item { Text("Curiosity has no finish line.",color=Color(0xFF718B7C),fontWeight=FontWeight.SemiBold,fontSize=12.sp) }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column {
        Text(title, color = Navy, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Color.Gray, fontSize = 12.sp)
    }
}

@Composable
private fun CompactStoryCard(
    story: Story,
    isSaved: Boolean,
    modifier: Modifier = Modifier,
    onSave: () -> Unit,
    onOpen: () -> Unit
) {
    Column(
        modifier.fillMaxWidth()
            .heightIn(min = 174.dp)
            .background(Color(0xFFFFFEFA), RoundedCornerShape(17.dp))
            .clickable { onOpen() }
            .padding(13.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(story.title, color = Navy, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 21.sp, maxLines = 4)
            Spacer(Modifier.height(8.dp))
            Text(story.category, color = Color(0xFF718B7C), fontSize = 9.sp,
                fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Schedule, null, tint = Color.Gray, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(story.readTime, color = Color.Gray, fontSize = 10.sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSave, modifier = Modifier.size(28.dp)) {
                Icon(if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = if (isSaved) "Remove bookmark" else "Save article",
                    tint = Color(0xFFC8795D), modifier = Modifier.size(18.dp))
            }
        }
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
        Text(story.title, color = Navy, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = 19.sp, fontWeight = FontWeight.Bold, lineHeight = 25.sp)
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(story.title, lifecycleOwner) {
        val prefs = context.getSharedPreferences("updates_preferences", android.content.Context.MODE_PRIVATE)
        var sessionStart = 0L
        fun saveSession() {
            if (sessionStart > 0L) {
                val elapsed = (android.os.SystemClock.elapsedRealtime() - sessionStart).coerceAtLeast(0L)
                val key = "reading_time_ms_" + story.title
                prefs.edit().putLong(key, prefs.getLong(key, 0L) + elapsed).apply()
                sessionStart = 0L
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (sessionStart == 0L) sessionStart = android.os.SystemClock.elapsedRealtime()
                Lifecycle.Event.ON_PAUSE -> saveSession()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { saveSession(); lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val sections = remember(story.title, story.summary, story.sections) {
        if (story.sections.isNotEmpty()) story.sections
        else story.summary.split(Regex("\\n\\s*\\n"))
            .mapIndexedNotNull { index, text -> text.trim().takeIf { it.isNotBlank() }?.let { StorySection("Section ${index + 1}", it) } }
            .ifEmpty { listOf(StorySection("Reading content", story.summary.trim().ifBlank { "No reading content is available yet." })) }
    }
    val lastContentIndex = sections.size
    val totalItems = sections.size + 2
    val readerListState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = initialProgress.coerceIn(0, totalItems - 1)
    )
    val readerScope = rememberCoroutineScope()
    var showSectionMenu by remember { mutableStateOf(false) }
    LaunchedEffect(readerListState) {
        androidx.compose.runtime.snapshotFlow { readerListState.firstVisibleItemIndex }
            .collect { onProgressChange(it) }
    }
    var fontSize by remember { mutableFloatStateOf(17f) }
    var readingTheme by remember { mutableStateOf(0) }
    val backgrounds = listOf(Color(0xFFF6F3E9), Color(0xFF17191C), Color(0xFFF2E8D5))
    val foreground = if (readingTheme == 1) Color(0xFFE8E8E8) else Navy
    val bodyColor = if (readingTheme == 1) Color(0xFFCCCCCC) else Color(0xFF303846)
    val currentIndex = readerListState.firstVisibleItemIndex
    val currentSection = (currentIndex - 1).coerceIn(0, sections.lastIndex)
    val readerProgress = ((currentIndex + 1).toFloat() / totalItems).coerceIn(0f, 1f)
    Column(Modifier.fillMaxSize().background(backgrounds[readingTheme])) {
        Row(Modifier.fillMaxWidth().background(if (readingTheme == 1) Color(0xFF222429) else Color(0xFFF0EBDD)).padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = foreground) }
            Text("UPDATES READER", color = foreground, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                val offlinePrefs = context.getSharedPreferences("updates_offline", android.content.Context.MODE_PRIVATE)
                val sectionsJson = org.json.JSONArray().apply { story.sections.forEach { section -> put(org.json.JSONObject().put("title", section.title).put("body", section.body)) } }
                val storyJson = org.json.JSONObject().put("category", story.category).put("title", story.title).put("summary", story.summary).put("readTime", story.readTime).put("sections", sectionsJson).toString()
                val titles = (offlinePrefs.getStringSet("offline_titles", emptySet()) ?: emptySet()).toMutableSet()
                titles.add(story.title)
                offlinePrefs.edit().putString("offline_story_" + story.title, storyJson).putStringSet("offline_titles", titles).apply()
            }) { Icon(Icons.Filled.Download, "Save for offline", tint = Blue) }
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
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(progress = { readerProgress }, modifier = Modifier.weight(1f).height(5.dp), color = Blue, trackColor = if (readingTheme == 1) Color.DarkGray else Color(0xFFDCE3EF))
            Spacer(Modifier.width(10.dp))
            Text("${(readerProgress * 100).toInt()}%", color = bodyColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { readerScope.launch { readerListState.animateScrollToItem((currentIndex - 1).coerceAtLeast(0)) } }, enabled = currentIndex > 0) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null); Text("Previous", fontSize = 11.sp)
            }
            Box {
                TextButton(onClick = { showSectionMenu = true }) {
                    Text(if (currentIndex == 0) "Start" else if (currentIndex == totalItems - 1) "Finish" else "Section ${currentSection + 1} of ${sections.size}", color = Blue, fontSize = 11.sp)
                    Icon(Icons.Filled.ExpandMore, contentDescription = "Choose section")
                }
                DropdownMenu(expanded = showSectionMenu, onDismissRequest = { showSectionMenu = false }) {
                    DropdownMenuItem(text = { Text("Article overview") }, onClick = { showSectionMenu = false; readerScope.launch { readerListState.animateScrollToItem(0) } })
                    sections.forEachIndexed { index, paragraph ->
                        DropdownMenuItem(
                            text = { Text("Section ${index + 1}: " + paragraph.title) },
                            onClick = { showSectionMenu = false; readerScope.launch { readerListState.animateScrollToItem(index + 1) } }
                        )
                    }
                    DropdownMenuItem(text = { Text("Finish reading") }, onClick = { showSectionMenu = false; readerScope.launch { readerListState.animateScrollToItem(lastContentIndex + 1) } })
                }
            }
            TextButton(onClick = { readerScope.launch { readerListState.animateScrollToItem((currentIndex + 1).coerceAtMost(totalItems - 1)) } }, enabled = currentIndex < totalItems - 1) {
                Text("Next", fontSize = 11.sp); Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
            }
        }
        LazyColumn(state = readerListState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                Text(story.category.uppercase(), color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Text(story.title, color = foreground, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = 32.sp, lineHeight = 39.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Schedule, null, tint = bodyColor, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(story.readTime, color = bodyColor, fontSize = 12.sp)
                }
                HorizontalDivider(color = if (readingTheme == 1) Color.DarkGray else Color(0xFFDCE3EF))
            }
            items(sections.size) { index ->
                Column {
                    Text("SECTION ${index + 1}", color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                    Spacer(Modifier.height(5.dp))
                    Text(sections[index].title, color = foreground, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = (fontSize + 3).sp, fontWeight = FontWeight.Bold, lineHeight = (fontSize * 1.5f).sp)
                    Spacer(Modifier.height(8.dp))
                    val readerPrefs = remember { context.getSharedPreferences("updates_reader_notes", android.content.Context.MODE_PRIVATE) }
                    val highlightKey = story.title + "::highlight::" + index
                    val bookmarkKey = story.title + "::bookmark::" + index
                    var isHighlighted by remember(story.title, index) { mutableStateOf(readerPrefs.getBoolean(highlightKey, false)) }
                    var isSectionBookmarked by remember(story.title, index) { mutableStateOf(readerPrefs.getBoolean(bookmarkKey, false)) }
                    Box(Modifier.fillMaxWidth().background(if (isHighlighted) Color(0xFFFFE8A3) else Color.Transparent, RoundedCornerShape(8.dp)).padding(if (isHighlighted) 8.dp else 0.dp)) {
                        Text(sections[index].body, color = if (isHighlighted) Color(0xFF493B20) else bodyColor, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = fontSize.sp, lineHeight = (fontSize * 1.8f).sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { isHighlighted = !isHighlighted; readerPrefs.edit().putBoolean(highlightKey, isHighlighted).apply() }) {
                            Text(if (isHighlighted) "Remove highlight" else "Highlight section", fontSize = 11.sp, color = Blue)
                        }
                        TextButton(onClick = { isSectionBookmarked = !isSectionBookmarked; readerPrefs.edit().putBoolean(bookmarkKey, isSectionBookmarked).apply() }) {
                            Text(if (isSectionBookmarked) "Section bookmarked" else "Bookmark section", fontSize = 11.sp, color = Blue)
                        }
                    }
                    var showNoteEditor by remember(story.title, index) { mutableStateOf(false) }
                    var sectionNote by remember(story.title, index) {
                        mutableStateOf(context.getSharedPreferences("updates_reader_notes", android.content.Context.MODE_PRIVATE).getString(story.title + "::" + index, "") ?: "")
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { showNoteEditor = true }) {
                            Icon(Icons.Filled.EditNote, contentDescription = null, tint = Blue)
                            Text(if (sectionNote.isBlank()) "Add note" else "Edit note", fontSize = 11.sp)
                        }
                        if (sectionNote.isNotBlank()) Text("Note saved", color = Color(0xFF536F60), fontSize = 10.sp)
                    }
                    if (showNoteEditor) {
                        AlertDialog(
                            onDismissRequest = { showNoteEditor = false },
                            title = { Text("Note · ${sections[index].title}") },
                            text = { OutlinedTextField(value = sectionNote, onValueChange = { sectionNote = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, label = { Text("Your personal note") }) },
                            confirmButton = { TextButton(onClick = { context.getSharedPreferences("updates_reader_notes", android.content.Context.MODE_PRIVATE).edit().putString(story.title + "::" + index, sectionNote.trim()).apply(); showNoteEditor = false }) { Text("Save") } },
                            dismissButton = { TextButton(onClick = { showNoteEditor = false }) { Text("Cancel") } }
                        )
                    }
                }
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
        Text("My Library", color = Navy, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("Your personal space for saved and recent reading", color = Color.Gray, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Saved", "History", "Books", "My Notes", "Offline").forEach { tab ->
                FilterChip(selected = section == tab, onClick = { section = tab }, label = { Text(tab) })
            }
        }
        Spacer(Modifier.height(12.dp))
        if (section == "History" && recentStories.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8EEE7)),
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Saved Articles", color = Navy, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                                Text("${savedStories.size} articles in your collection", color = Color.Gray, fontSize = 11.sp)
                            }
                            Icon(Icons.Filled.Bookmarks, null, tint = Color(0xFFC8795D), modifier = Modifier.size(25.dp))
                        }
                    }
                    if (savedStories.isEmpty()) {
                        item {
                            Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.BookmarkBorder, null, tint = Color(0xFF8FA99A), modifier = Modifier.size(42.dp))
                                Spacer(Modifier.height(10.dp))
                                Text("Your reading shelf is waiting", color = Navy, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("Bookmark an article to keep it close.", color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    } else {
                        items(savedStories.chunked(2)) { rowStories ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                rowStories.forEach { story ->
                                    CompactStoryCard(story, true, Modifier.weight(1f),
                                        onSave = { onToggleSaved(story.title) }, onOpen = { onOpenStory(story) })
                                }
                                if (rowStories.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text("Daily Briefing", color = Navy, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text("A little knowledge, every day", color = Color.Gray, fontSize = 11.sp)
                    }
                    items(stories.take(3)) { story ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onOpenStory(story) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEFA))
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(story.category.uppercase(), color = Color(0xFF718B7C), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(story.title, color = Navy, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(5.dp))
                                Text(story.summary, color = Color(0xFF59615C), fontSize = 12.sp, lineHeight = 17.sp, maxLines = 3)
                                Spacer(Modifier.height(7.dp))
                                Text("Read briefing  →", color = Color(0xFF718B7C), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
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
            "My Notes" -> {
                val context = androidx.compose.ui.platform.LocalContext.current
                val readerPrefs = remember { context.getSharedPreferences("updates_reader_notes", android.content.Context.MODE_PRIVATE) }
                val entries = remember(stories) {
                    buildList {
                        stories.forEach { story ->
                            story.sections.forEachIndexed { index, item ->
                                val note = readerPrefs.getString(story.title + "::" + index, "").orEmpty()
                                val highlighted = readerPrefs.getBoolean(story.title + "::highlight::" + index, false)
                                val bookmarked = readerPrefs.getBoolean(story.title + "::bookmark::" + index, false)
                                if (note.isNotBlank() || highlighted || bookmarked) add(Triple(story, index, Triple(note, highlighted, bookmarked)))
                            }
                        }
                    }
                }
                Column(Modifier.fillMaxSize()) {
                    Text("Your reading notes and marked sections", color = Color.Gray, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    if (entries.isEmpty()) {
                        Text("No notes, highlights or section bookmarks yet. Open an article and mark a section to find it here.", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(top = 18.dp))
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                            items(entries) { entry ->
                                val (story, index, details) = entry
                                Card(modifier = Modifier.fillMaxWidth().clickable { onOpenStory(story) }, colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEFA)), shape = RoundedCornerShape(14.dp)) {
                                    Column(Modifier.padding(14.dp)) {
                                        Text(story.title, color = Navy, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(story.sections[index].title, color = Color(0xFF718B7C), fontSize = 12.sp)
                                        if (details.first.isNotBlank()) {
                                            Spacer(Modifier.height(6.dp))
                                            Text(details.first, color = Color.DarkGray, fontSize = 12.sp, maxLines = 4)
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Text(listOfNotNull(if (details.second) "Highlighted" else null, if (details.third) "Bookmarked" else null).joinToString(" · "), color = Color(0xFFC8795D), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Text("Tap to reopen article", color = Blue, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "Offline" -> {
                val context = androidx.compose.ui.platform.LocalContext.current
                val offlinePrefs = remember { context.getSharedPreferences("updates_offline", android.content.Context.MODE_PRIVATE) }
                var offlineTitles by remember { mutableStateOf((offlinePrefs.getStringSet("offline_titles", emptySet()) ?: emptySet()).toSet()) }
                var deleteTitle by remember { mutableStateOf<String?>(null) }
                val offlineEntries = remember(offlineTitles) {
                    offlineTitles.mapNotNull { title ->
                        runCatching {
                            val raw = offlinePrefs.getString("offline_story_" + title, null) ?: return@runCatching null
                            val json = org.json.JSONObject(raw)
                            val array = json.optJSONArray("sections") ?: org.json.JSONArray()
                            val sections = (0 until array.length()).map { index -> val item = array.getJSONObject(index); StorySection(item.optString("title"), item.optString("body")) }
                            val story = Story(json.optString("category"), json.optString("title"), json.optString("summary"), json.optString("readTime"), sections)
                            Triple(story, title, raw.toByteArray(Charsets.UTF_8).size.toLong())
                        }.getOrNull()
                    }
                }
                val totalBytes = offlineEntries.sumOf { it.third }
                val sizeLabel = if (totalBytes < 1024L * 1024L) "${"%.1f".format(totalBytes / 1024.0)} KB" else "${"%.2f".format(totalBytes / (1024.0 * 1024.0))} MB"
                Column(Modifier.fillMaxSize()) {
                    Text("${offlineEntries.size} articles · $sizeLabel used", color = Color.Gray, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    if (offlineEntries.isEmpty()) Text("No offline articles yet. Open an article and tap the download icon to save it for offline reading.", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(top = 18.dp))
                    else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(offlineEntries, key = { it.second }) { entry ->
                            val (story, title, bytes) = entry
                            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f).clickable { onOpenStory(story) }) {
                                        Text(story.category.uppercase(), color = Color(0xFF718B7C), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Text(story.title, color = Navy, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text("${story.readTime} · ${"%.1f".format(bytes / 1024.0)} KB", color = Color.Gray, fontSize = 11.sp)
                                    }
                                    IconButton(onClick = { deleteTitle = title }) { Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove offline article", tint = Color(0xFFC8795D)) }
                                }
                            }
                        }
                    }
                }
                deleteTitle?.let { title ->
                    AlertDialog(
                        onDismissRequest = { deleteTitle = null },
                        title = { Text("Remove offline article?") },
                        text = { Text("This removes the downloaded copy from this device. Your saved article and reading history are not affected.") },
                        confirmButton = { TextButton(onClick = {
                            offlinePrefs.edit().remove("offline_story_" + title).apply()
                            offlineTitles = (offlineTitles - title).toSet()
                            offlinePrefs.edit().putStringSet("offline_titles", offlineTitles).apply()
                            deleteTitle = null
                        }) { Text("Remove") } },
                        dismissButton = { TextButton(onClick = { deleteTitle = null }) { Text("Cancel") } }
                    )
                }
            }
            else -> {
                Column(Modifier.fillMaxWidth().weight(1f)) {
                    Text("${savedBooks.size} saved catalogue books", color = Blue, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    DigitalLibraryScreen(savedBooks, onToggleBook, savedOnly = true)
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
