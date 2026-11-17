# UPDATES — Android App

**Tagline:** Build Your Own Treasure of Knowledge.

UPDATES is a knowledge-sharing and discovery app concept bringing together articles, research, stories, books and community publishing.

## Current development status

### Milestone 1 — Project foundation and first UI
- Native Android project using Kotlin and Jetpack Compose.
- Navy, royal-blue and orange visual identity.
- Initial Home, Explore, Library and Profile navigation.
- Search across sample content and locally saved sample stories.
- GitHub Actions workflow to build a debug APK.

**Important:** The current screens use sample content and in-memory state. Accounts, cloud sync, live news/research sources, real publishing, comments, notifications and admin tools are not implemented yet.

## Build
The GitHub Actions workflow runs on pushes to `main` and can also be started manually from the Actions tab. Once a workflow run succeeds, download the `updates-debug-apk` artifact from that run.

Build requirements:
- JDK 17
- Gradle 8.11.1
- Android SDK platform 35

## App details
- Application ID: `com.updates.app`
- Minimum Android: 8.0 (API 26)
- Version: 1.0.0

## Planned milestones
1. Splash screen, onboarding and interest selection.
2. Content detail and reading experience.
3. Authentication and user profiles.
4. Backend, publishing and community interactions.
5. News and research discovery with source attribution.
6. Bookmarks, reading history and offline support.
7. Moderation/admin tools, testing and release build.
