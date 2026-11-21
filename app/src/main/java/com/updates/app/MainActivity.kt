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
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFF536F60), selectedTextColor = Color(0xFF536F60), indicatorColor = Color(0xFFE5EBE2))
                    )
                }NavigationBar(containerColor = Color(0xFF263238), contentColor = Color.White) {
                listOf("Home", "Library", "Profile").forEach { tab ->
                    val label = if (tab == "Home") "Discover" else tab
                    val icon = when (tab) {
                        "Home" -> Icons.Filled.Explore
                        "Library" -> Icons.Filled.Bookmarks
                        else -> Icons.Filled.Person
                    }
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { onTabSelected(tab) },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF8FA99A), selectedTextColor = Color(0xFF8FA99A),
                            indicatorColor = Color(0xFF34443D), unselectedIconColor = Color(0xFFD9DDD7),
                            unselectedTextColor = Color(0xFFD9DDD7)
                        )
                    )
                }