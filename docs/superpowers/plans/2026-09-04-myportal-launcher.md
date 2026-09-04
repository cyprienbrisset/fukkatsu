# MyPortal Launcher — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Kotlin/Jetpack Compose HOME launcher for the Meta Portal+ (2nd gen) that launches installed apps and full-screen web apps from a tile grid, with an ambient banner (clock, weather, greeting) and a built-in alarm clock that rings reliably.

**Architecture:** Single-module Android app, MVVM. Compose UI (Navigation-Compose) hosted by a `MainActivity` declared as HOME. Room persists tiles and alarms; DataStore persists preferences. A dedicated `WebAppActivity` hosts the immersive WebView, and `AlarmRingActivity` shows the ringing screen. Alarms are scheduled with `AlarmManager.setAlarmClock()` and rescheduled on boot.

**Tech Stack:** Kotlin, Jetpack Compose + Material 3, Navigation-Compose, Room (+KSP), DataStore Preferences, OkHttp + kotlinx-serialization (Open-Meteo), Coil, AlarmManager.

---

## Notes on the environment

- **Bleeding-edge versions.** The scaffold pins `agp = 9.4.0`, `compileSdk 37`, `targetSdk 37`. These are preview versions. When a dependency version below fails to resolve, bump it to the newest available and re-run the build — do not silently skip. Every phase ends with a build/verify step precisely to catch this early.
- **Build command:** `./gradlew :app:assembleDebug` (compile) and `./gradlew :app:testDebugUnitTest` (JVM unit tests). Instrumented tests require the Portal (or an emulator) connected: `./gradlew :app:connectedDebugAndroidTest`. Prefer JVM unit tests for logic; use manual on-device verification for UI.
- **Package root:** `com.cyprienbrisset.myportal`. Source dir: `app/src/main/java/com/cyprienbrisset/myportal/`. Unit tests: `app/src/test/java/com/cyprienbrisset/myportal/`.
- **Commit after every task.** The repo is already initialized (branch `main`).

---

## File Structure

```
app/src/main/java/com/cyprienbrisset/myportal/
  MyPortalApp.kt                      # Application, DI wiring (manual)
  MainActivity.kt                     # HOME activity, Compose nav host
  ui/
    theme/Theme.kt, Color.kt, Type.kt # Compose theme
    home/HomeScreen.kt                # ambient banner + tile grid
    home/AmbientBanner.kt             # clock/date/greeting/weather/next-alarm
    home/TileGrid.kt                  # grid of tiles
    settings/SettingsScreen.kt
    settings/TileEditScreen.kt        # add/edit/reorder/delete tiles
    alarms/AlarmsScreen.kt            # list + create/edit alarms
  data/
    AppDatabase.kt                    # Room DB
    tile/TileEntity.kt, TileDao.kt, TileRepository.kt
    alarm/AlarmEntity.kt, AlarmDao.kt, AlarmRepository.kt
    settings/SettingsRepository.kt    # DataStore
    weather/WeatherApi.kt, WeatherModels.kt, WeatherRepository.kt
  launch/LaunchIntentResolver.kt      # installed-app launch + installed-app listing
  web/WebAppActivity.kt               # immersive WebView host
  alarm/AlarmScheduler.kt             # setAlarmClock + nextTriggerTime()
  alarm/AlarmReceiver.kt              # fires -> AlarmRingActivity, reschedules
  alarm/BootReceiver.kt               # reschedule on boot
  alarm/AlarmRingActivity.kt          # full-screen ringing UI + sound
app/src/test/java/com/cyprienbrisset/myportal/
  alarm/AlarmSchedulerTest.kt         # nextTriggerTime logic (critical)
  data/weather/WeatherParsingTest.kt  # Open-Meteo parsing
  data/tile/TileRepositoryTest.kt     # (Robolectric or in-memory Room)
```

---

## PHASE 0 — Foundation (Compose + Room + HOME skeleton)

### Task 1: Convert the build to Kotlin + Compose

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `build.gradle.kts` (root)

- [ ] **Step 1: Add versions & libraries to the catalog**

Edit `gradle/libs.versions.toml`. Add under `[versions]`:

```toml
kotlin = "2.0.21"
ksp = "2.0.21-1.0.28"
composeBom = "2024.09.03"
activityCompose = "1.9.3"
lifecycle = "2.8.7"
navigationCompose = "2.8.4"
room = "2.6.1"
datastore = "1.1.1"
okhttp = "4.12.0"
kotlinxSerialization = "1.7.3"
coil = "2.7.0"
coroutinesTest = "1.9.0"
```

Add under `[libraries]`:

```toml
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons = { group = "androidx.compose.material", name = "material-icons-extended" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutinesTest" }
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
```

Add under `[plugins]`:

```toml
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 2: Declare plugins at the root**

Edit root `build.gradle.kts` so the `plugins {}` block lists (all `apply false`):

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
```

- [ ] **Step 3: Rewrite `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.cyprienbrisset.myportal"
    compileSdk { version = release(37) }

    defaultConfig {
        applicationId = "com.cyprienbrisset.myportal"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release { optimization { enable = false } }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
}
```

- [ ] **Step 4: Verify the build resolves**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. If a version fails to resolve (preview SDK/AGP), bump it to the latest available and re-run. Do not proceed until green.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts
git commit -m "build: convert app to Kotlin + Jetpack Compose stack"
```

---

### Task 2: Compose theme + Application class

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/ui/theme/Color.kt`
- Create: `app/src/main/java/com/cyprienbrisset/myportal/ui/theme/Type.kt`
- Create: `app/src/main/java/com/cyprienbrisset/myportal/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/cyprienbrisset/myportal/MyPortalApp.kt`

- [ ] **Step 1: Colors**

`ui/theme/Color.kt`:

```kotlin
package com.cyprienbrisset.myportal.ui.theme

import androidx.compose.ui.graphics.Color

val PortalBg = Color(0xFF12141F)
val PortalSurface = Color(0xFF1B1E2E)
val PortalSurfaceHi = Color(0xFF2A2F45)
val PortalAccent = Color(0xFF4C5FD5)
val PortalOnDark = Color(0xFFDFE3F0)
val PortalMuted = Color(0xFF8B90A8)
```

- [ ] **Step 2: Typography**

`ui/theme/Type.kt`:

```kotlin
package com.cyprienbrisset.myportal.ui.theme

import androidx.compose.material3.Typography

val PortalTypography = Typography()
```

- [ ] **Step 3: Theme (dark, TV-distance friendly)**

`ui/theme/Theme.kt`:

```kotlin
package com.cyprienbrisset.myportal.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val PortalColors = darkColorScheme(
    primary = PortalAccent,
    background = PortalBg,
    surface = PortalSurface,
    surfaceVariant = PortalSurfaceHi,
    onBackground = PortalOnDark,
    onSurface = PortalOnDark,
)

@Composable
fun MyPortalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = PortalColors, typography = PortalTypography, content = content)
}
```

- [ ] **Step 4: Application class (manual DI container placeholder)**

`MyPortalApp.kt`:

```kotlin
package com.cyprienbrisset.myportal

import android.app.Application

class MyPortalApp : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
```

- [ ] **Step 5: Verify build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/ui/theme app/src/main/java/com/cyprienbrisset/myportal/MyPortalApp.kt
git commit -m "feat: add Compose theme and Application class"
```

---

### Task 3: MainActivity as HOME + Compose nav skeleton + manifest

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/MainActivity.kt`
- Create: `app/src/main/java/com/cyprienbrisset/myportal/ui/AppNav.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml` (ensure `app_name`)

- [ ] **Step 1: Navigation host with placeholder screens**

`ui/AppNav.kt`:

```kotlin
package com.cyprienbrisset.myportal.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val TILE_EDIT = "tile_edit"
    const val ALARMS = "alarms"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) { Text("Home") }        // replaced in Task 7
        composable(Routes.SETTINGS) { Text("Settings") } // replaced in Task 8
        composable(Routes.TILE_EDIT) { Text("Tiles") }   // replaced in Task 8
        composable(Routes.ALARMS) { Text("Alarms") }     // replaced in Task 17
    }
}
```

- [ ] **Step 2: MainActivity**

`MainActivity.kt`:

```kotlin
package com.cyprienbrisset.myportal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cyprienbrisset.myportal.ui.AppNav
import com.cyprienbrisset.myportal.ui.theme.MyPortalTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyPortalTheme { AppNav() }
        }
    }
}
```

- [ ] **Step 3: Manifest — register app, declare HOME**

Set `AndroidManifest.xml` `<application>` to `android:name=".MyPortalApp"` and `android:theme="@style/Theme.MyPortal"` (or existing Material theme), then declare MainActivity:

```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:launchMode="singleTask"
    android:stateNotNeeded="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.HOME" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

(`LAUNCHER` category is kept so the app is also visible/launchable during development.)

- [ ] **Step 4: Build + install + manual verify**

Run: `./gradlew :app:installDebug` (Portal or emulator connected).
Expected: App installs. Pressing Home offers MyPortal as a launcher option; selecting it shows the "Home" placeholder text. If no launcher chooser appears on the locked Portal, note it — Task 7 verification revisits this; default-launcher selection may need `adb shell cmd package set-home-activity com.cyprienbrisset.myportal/.MainActivity`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/MainActivity.kt app/src/main/java/com/cyprienbrisset/myportal/ui/AppNav.kt app/src/main/AndroidManifest.xml app/src/main/res/values/strings.xml
git commit -m "feat: MainActivity as HOME with Compose nav skeleton"
```

---

## PHASE 1 — Launcher grid + app launch

### Task 4: Tile entity, DAO, Room database

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/data/tile/TileEntity.kt`
- Create: `app/src/main/java/com/cyprienbrisset/myportal/data/tile/TileDao.kt`
- Create: `app/src/main/java/com/cyprienbrisset/myportal/data/AppDatabase.kt`

- [ ] **Step 1: Entity + type enum**

`data/tile/TileEntity.kt`:

```kotlin
package com.cyprienbrisset.myportal.data.tile

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TileType { APP, WEB }

@Entity(tableName = "tiles")
data class TileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TileType,
    val label: String,
    val packageName: String? = null, // for APP
    val url: String? = null,         // for WEB
    val iconRef: String? = null,     // optional custom icon uri
    val position: Int,
)
```

- [ ] **Step 2: DAO**

`data/tile/TileDao.kt`:

```kotlin
package com.cyprienbrisset.myportal.data.tile

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TileDao {
    @Query("SELECT * FROM tiles ORDER BY position ASC")
    fun observeAll(): Flow<List<TileEntity>>

    @Query("SELECT * FROM tiles ORDER BY position ASC")
    suspend fun getAll(): List<TileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tile: TileEntity): Long

    @Update
    suspend fun update(tile: TileEntity)

    @Update
    suspend fun updateAll(tiles: List<TileEntity>)

    @Delete
    suspend fun delete(tile: TileEntity)

    @Query("SELECT COALESCE(MAX(position), -1) FROM tiles")
    suspend fun maxPosition(): Int
}
```

- [ ] **Step 3: Room database (includes AlarmDao forward-declared in Phase 4)**

`data/AppDatabase.kt`:

```kotlin
package com.cyprienbrisset.myportal.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.cyprienbrisset.myportal.data.tile.TileDao
import com.cyprienbrisset.myportal.data.tile.TileEntity
import com.cyprienbrisset.myportal.data.tile.TileType

class Converters {
    @TypeConverter fun tileType(v: String): TileType = TileType.valueOf(v)
    @TypeConverter fun tileTypeToString(v: TileType): String = v.name
}

@Database(entities = [TileEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tileDao(): TileDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "myportal.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
```

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (Room KSP generates the DAO impl).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/data
git commit -m "feat: tile entity, DAO, and Room database"
```

---

### Task 5: TileRepository (with in-memory Room test)

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/data/tile/TileRepository.kt`
- Test: `app/src/androidTest/java/com/cyprienbrisset/myportal/data/TileRepositoryTest.kt`

> Room DAOs need a real SQLite instance, so this test is an **instrumented** test (`androidTest`) using an in-memory database. Run with a device/emulator connected.

- [ ] **Step 1: Write the failing test**

`app/src/androidTest/java/com/cyprienbrisset/myportal/data/TileRepositoryTest.kt`:

```kotlin
package com.cyprienbrisset.myportal.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cyprienbrisset.myportal.data.tile.TileEntity
import com.cyprienbrisset.myportal.data.tile.TileRepository
import com.cyprienbrisset.myportal.data.tile.TileType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TileRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: TileRepository

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        repo = TileRepository(db.tileDao())
    }

    @After fun teardown() = db.close()

    @Test fun addAppendsAtEndAndReorderPersists() = runTest {
        repo.add(TileEntity(type = TileType.APP, label = "Netflix", packageName = "com.netflix", position = 0))
        repo.add(TileEntity(type = TileType.WEB, label = "Jellyfin", url = "http://jelly", position = 0))
        val tiles = repo.getAll()
        assertEquals(listOf("Netflix", "Jellyfin"), tiles.map { it.label })
        assertEquals(listOf(0, 1), tiles.map { it.position })

        repo.reorder(tiles.reversed())
        assertEquals(listOf("Jellyfin", "Netflix"), repo.getAll().map { it.label })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "*TileRepositoryTest*"`
Expected: FAIL — `TileRepository` unresolved.

- [ ] **Step 3: Implement the repository**

`data/tile/TileRepository.kt`:

```kotlin
package com.cyprienbrisset.myportal.data.tile

import kotlinx.coroutines.flow.Flow

class TileRepository(private val dao: TileDao) {
    fun observeAll(): Flow<List<TileEntity>> = dao.observeAll()
    suspend fun getAll(): List<TileEntity> = dao.getAll()

    /** Adds a tile at the end, ignoring the passed-in position. */
    suspend fun add(tile: TileEntity): Long {
        val next = dao.maxPosition() + 1
        return dao.insert(tile.copy(position = next))
    }

    suspend fun update(tile: TileEntity) = dao.update(tile)
    suspend fun delete(tile: TileEntity) = dao.delete(tile)

    /** Rewrites positions to match the given order. */
    suspend fun reorder(ordered: List<TileEntity>) {
        dao.updateAll(ordered.mapIndexed { i, t -> t.copy(position = i) })
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "*TileRepositoryTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/data/tile/TileRepository.kt app/src/androidTest/java/com/cyprienbrisset/myportal/data/TileRepositoryTest.kt
git commit -m "feat: TileRepository with add/reorder + instrumented test"
```

---

### Task 6: LaunchIntentResolver (installed-app launch + listing)

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/launch/LaunchIntentResolver.kt`
- Test: `app/src/test/java/com/cyprienbrisset/myportal/launch/LaunchIntentResolverTest.kt`

- [ ] **Step 1: Write the failing test (pure logic via a thin PackageManager seam)**

`app/src/test/java/com/cyprienbrisset/myportal/launch/LaunchIntentResolverTest.kt`:

```kotlin
package com.cyprienbrisset.myportal.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LaunchIntentResolverTest {
    @Test fun returnsPackageWhenLaunchable() {
        val resolver = LaunchIntentResolver { pkg -> pkg == "com.netflix" }
        assertEquals("com.netflix", resolver.resolvablePackageOrNull("com.netflix"))
    }

    @Test fun returnsNullWhenNotInstalled() {
        val resolver = LaunchIntentResolver { false }
        assertNull(resolver.resolvablePackageOrNull("com.missing"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*LaunchIntentResolverTest*"`
Expected: FAIL — class unresolved.

- [ ] **Step 3: Implement**

`launch/LaunchIntentResolver.kt`:

```kotlin
package com.cyprienbrisset.myportal.launch

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Resolves and launches installed apps. The [isLaunchable] seam keeps the
 * decision logic unit-testable without a real PackageManager.
 */
class LaunchIntentResolver(private val isLaunchable: (String) -> Boolean) {

    fun resolvablePackageOrNull(pkg: String): String? =
        if (isLaunchable(pkg)) pkg else null

    companion object {
        fun fromContext(context: Context): LaunchIntentResolver {
            val pm = context.packageManager
            return LaunchIntentResolver { pkg -> pm.getLaunchIntentForPackage(pkg) != null }
        }

        /** Launches [pkg]; returns true on success. */
        fun launch(context: Context, pkg: String): Boolean {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return true
        }

        data class InstalledApp(val label: String, val packageName: String)

        /** Lists launchable installed apps (for the tile picker). */
        fun installedLaunchableApps(context: Context): List<InstalledApp> {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            return pm.queryIntentActivities(intent, 0)
                .map { InstalledApp(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*LaunchIntentResolverTest*"`
Expected: PASS.

- [ ] **Step 5: Add `<queries>` to manifest** (needed for `queryIntentActivities` / `getLaunchIntentForPackage` on API 30+)

In `AndroidManifest.xml`, directly under `<manifest>` (sibling of `<application>`):

```xml
<queries>
    <intent>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent>
</queries>
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/launch app/src/test/java/com/cyprienbrisset/myportal/launch app/src/main/AndroidManifest.xml
git commit -m "feat: LaunchIntentResolver for launching/listing installed apps"
```

---

### Task 7: HomeScreen — tile grid + launch wiring

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/ui/home/TileGrid.kt`
- Create: `app/src/main/java/com/cyprienbrisset/myportal/ui/home/HomeViewModel.kt`
- Create: `app/src/main/java/com/cyprienbrisset/myportal/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/AppNav.kt`

- [ ] **Step 1: ViewModel exposing tiles**

`ui/home/HomeViewModel.kt`:

```kotlin
package com.cyprienbrisset.myportal.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cyprienbrisset.myportal.data.AppDatabase
import com.cyprienbrisset.myportal.data.tile.TileEntity
import com.cyprienbrisset.myportal.data.tile.TileRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TileRepository(AppDatabase.get(app).tileDao())
    val tiles = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
```

- [ ] **Step 2: TileGrid composable**

`ui/home/TileGrid.kt`:

```kotlin
package com.cyprienbrisset.myportal.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cyprienbrisset.myportal.data.tile.TileEntity

@Composable
fun TileGrid(
    tiles: List<TileEntity>,
    onTileClick: (TileEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(24.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(tiles, key = { it.id }) { tile ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.height(120.dp).clickable { onTileClick(tile) },
            ) {
                Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(tile.label, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
```

- [ ] **Step 3: HomeScreen wiring launch behavior**

`ui/home/HomeScreen.kt`:

```kotlin
package com.cyprienbrisset.myportal.ui.home

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyprienbrisset.myportal.data.tile.TileEntity
import com.cyprienbrisset.myportal.data.tile.TileType
import com.cyprienbrisset.myportal.launch.LaunchIntentResolver
import com.cyprienbrisset.myportal.web.WebAppActivity

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val ctx = LocalContext.current
    val tiles by vm.tiles.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        // AmbientBanner added in Phase 3, placed here.
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "Réglages")
        }
        TileGrid(tiles = tiles, onTileClick = { tile ->
            when (tile.type) {
                TileType.APP -> {
                    val pkg = tile.packageName
                    if (pkg == null || !LaunchIntentResolver.launch(ctx, pkg)) {
                        Toast.makeText(ctx, "App introuvable : ${tile.label}", Toast.LENGTH_SHORT).show()
                    }
                }
                TileType.WEB -> {
                    ctx.startActivity(
                        Intent(ctx, WebAppActivity::class.java)
                            .putExtra(WebAppActivity.EXTRA_URL, tile.url)
                    )
                }
            }
        })
    }
}
```

> Note: `WebAppActivity` is created in Task 9. Until then, comment out the `TileType.WEB` branch body or stub the activity. Recommended: implement Task 9 immediately after this task; the import will resolve then. If executing strictly in order, temporarily replace the WEB branch with a `Toast` and restore it in Task 9.

- [ ] **Step 4: Wire into AppNav**

Replace the `HOME` and `SETTINGS` composables in `ui/AppNav.kt`:

```kotlin
composable(Routes.HOME) {
    com.cyprienbrisset.myportal.ui.home.HomeScreen(
        onOpenSettings = { nav.navigate(Routes.SETTINGS) }
    )
}
```

- [ ] **Step 5: Build + manual verify**

Run: `./gradlew :app:installDebug`
Expected: Home shows an (empty) grid + a settings icon. No crash. Grid populates once tiles are added (Task 8).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/ui/home app/src/main/java/com/cyprienbrisset/myportal/ui/AppNav.kt
git commit -m "feat: HomeScreen tile grid with app/web launch wiring"
```

---

### Task 8: Settings + tile management (add/reorder/delete)

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/ui/settings/TileEditViewModel.kt`
- Create: `app/src/main/java/com/cyprienbrisset/myportal/ui/settings/TileEditScreen.kt`
- Create: `app/src/main/java/com/cyprienbrisset/myportal/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/AppNav.kt`

- [ ] **Step 1: ViewModel — expose tiles, installed apps, mutations**

`ui/settings/TileEditViewModel.kt`:

```kotlin
package com.cyprienbrisset.myportal.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cyprienbrisset.myportal.data.AppDatabase
import com.cyprienbrisset.myportal.data.tile.TileEntity
import com.cyprienbrisset.myportal.data.tile.TileRepository
import com.cyprienbrisset.myportal.data.tile.TileType
import com.cyprienbrisset.myportal.launch.LaunchIntentResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TileEditViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TileRepository(AppDatabase.get(app).tileDao())
    val tiles = repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun installedApps() = withContext(Dispatchers.IO) {
        LaunchIntentResolver.installedLaunchableApps(getApplication())
    }

    fun addApp(label: String, pkg: String) = viewModelScope.launch {
        repo.add(TileEntity(type = TileType.APP, label = label, packageName = pkg, position = 0))
    }

    fun addWeb(label: String, url: String) = viewModelScope.launch {
        val normalized = if (url.startsWith("http")) url else "https://$url"
        repo.add(TileEntity(type = TileType.WEB, label = label, url = normalized, position = 0))
    }

    fun delete(tile: TileEntity) = viewModelScope.launch { repo.delete(tile) }
    fun moveUp(tile: TileEntity) = viewModelScope.launch {
        val list = repo.getAll().toMutableList()
        val i = list.indexOfFirst { it.id == tile.id }
        if (i > 0) { list.add(i - 1, list.removeAt(i)); repo.reorder(list) }
    }
    fun moveDown(tile: TileEntity) = viewModelScope.launch {
        val list = repo.getAll().toMutableList()
        val i = list.indexOfFirst { it.id == tile.id }
        if (i in 0 until list.lastIndex) { list.add(i + 1, list.removeAt(i)); repo.reorder(list) }
    }
}
```

- [ ] **Step 2: TileEditScreen — list existing + add dialog (installed app or URL)**

`ui/settings/TileEditScreen.kt`:

```kotlin
package com.cyprienbrisset.myportal.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyprienbrisset.myportal.data.tile.TileType
import com.cyprienbrisset.myportal.launch.LaunchIntentResolver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TileEditScreen(onBack: () -> Unit, vm: TileEditViewModel = viewModel()) {
    val tiles by vm.tiles.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Tuiles") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Retour") }
        }) },
        floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, "Ajouter") } },
    ) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            items(tiles, key = { it.id }) { tile ->
                ListItem(
                    headlineContent = { Text(tile.label) },
                    supportingContent = { Text(tile.packageName ?: tile.url ?: "") },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { vm.moveUp(tile) }) { Icon(Icons.Filled.KeyboardArrowUp, "Monter") }
                            IconButton(onClick = { vm.moveDown(tile) }) { Icon(Icons.Filled.KeyboardArrowDown, "Descendre") }
                            IconButton(onClick = { vm.delete(tile) }) { Icon(Icons.Filled.Delete, "Supprimer") }
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }

    if (showAdd) AddTileDialog(vm = vm, onDismiss = { showAdd = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTileDialog(vm: TileEditViewModel, onDismiss: () -> Unit) {
    var mode by remember { mutableStateOf(TileType.APP) }
    var apps by remember { mutableStateOf<List<LaunchIntentResolver.Companion.InstalledApp>>(emptyList()) }
    var webLabel by remember { mutableStateOf("") }
    var webUrl by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { apps = vm.installedApps() }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            if (mode == TileType.WEB) TextButton(onClick = {
                if (webLabel.isNotBlank() && webUrl.isNotBlank()) { vm.addWeb(webLabel, webUrl); onDismiss() }
            }) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
        title = { Text("Ajouter une tuile") },
        text = {
            Column {
                Row {
                    FilterChip(selected = mode == TileType.APP, onClick = { mode = TileType.APP }, label = { Text("App") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = mode == TileType.WEB, onClick = { mode = TileType.WEB }, label = { Text("Web") })
                }
                Spacer(Modifier.height(12.dp))
                if (mode == TileType.APP) {
                    Column(Modifier.heightIn(max = 320.dp)) {
                        LazyColumn {
                            items(apps) { a ->
                                ListItem(
                                    headlineContent = { Text(a.label) },
                                    supportingContent = { Text(a.packageName) },
                                    modifier = Modifier.clickable { vm.addApp(a.label, a.packageName); onDismiss() },
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(webLabel, { webLabel = it }, label = { Text("Nom") })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(webUrl, { webUrl = it }, label = { Text("URL (ex. jellyfin.local)") })
                }
            }
        },
    )
}
```

- [ ] **Step 3: SettingsScreen — entry points (tiles, weather city, alarms)**

`ui/settings/SettingsScreen.kt`:

```kotlin
package com.cyprienbrisset.myportal.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onTiles: () -> Unit, onAlarms: () -> Unit, onWeather: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text("Réglages") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Retour") }
        })
    }) { pad ->
        androidx.compose.foundation.layout.Column(Modifier.padding(pad)) {
            ListItem(headlineContent = { Text("Tuiles") }, modifier = Modifier.clickable { onTiles() })
            ListItem(headlineContent = { Text("Alarmes") }, modifier = Modifier.clickable { onAlarms() })
            ListItem(headlineContent = { Text("Ville météo") }, modifier = Modifier.clickable { onWeather() })
        }
    }
}
```

- [ ] **Step 4: Wire routes**

In `ui/AppNav.kt`, replace `SETTINGS` and `TILE_EDIT` composables:

```kotlin
composable(Routes.SETTINGS) {
    com.cyprienbrisset.myportal.ui.settings.SettingsScreen(
        onBack = { nav.popBackStack() },
        onTiles = { nav.navigate(Routes.TILE_EDIT) },
        onAlarms = { nav.navigate(Routes.ALARMS) },
        onWeather = { nav.navigate(Routes.SETTINGS + "/weather") }, // implemented in Task 10
    )
}
composable(Routes.TILE_EDIT) {
    com.cyprienbrisset.myportal.ui.settings.TileEditScreen(onBack = { nav.popBackStack() })
}
```

- [ ] **Step 5: Build + manual verify**

Run: `./gradlew :app:installDebug`
Expected: Settings → Tuiles → add an installed app (appears in Home grid) and a web URL. Reorder + delete work and persist across relaunch.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/ui/settings app/src/main/java/com/cyprienbrisset/myportal/ui/AppNav.kt
git commit -m "feat: settings + tile management (add app/web, reorder, delete)"
```

---

## PHASE 2 — Web tiles (immersive WebView)

### Task 9: WebAppActivity

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/web/WebAppActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Implement the activity**

`web/WebAppActivity.kt`:

```kotlin
package com.cyprienbrisset.myportal.web

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

class WebAppActivity : ComponentActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) { finish(); return }

        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                loadWithOverviewMode = true
                useWideViewPort = true
            }
            webViewClient = WebViewClient() // keep navigation inside the WebView
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        setContentView(webView)
        goImmersive()
        webView.loadUrl(url)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    private fun goImmersive() {
        val c = window.decorView
        @Suppress("DEPRECATION")
        c.systemUiVisibility = (android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
            or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    companion object { const val EXTRA_URL = "extra_url" }
}
```

- [ ] **Step 2: Register in manifest + INTERNET permission**

Add `<uses-permission android:name="android.permission.INTERNET" />` under `<manifest>`, and inside `<application>`:

```xml
<activity
    android:name=".web.WebAppActivity"
    android:exported="false"
    android:configChanges="orientation|screenSize|keyboardHidden"
    android:theme="@style/Theme.MyPortal" />
```

- [ ] **Step 3: Restore the WEB branch in HomeScreen** (if it was stubbed in Task 7). Confirm the import `com.cyprienbrisset.myportal.web.WebAppActivity` resolves.

- [ ] **Step 4: Build + manual verify**

Run: `./gradlew :app:installDebug`
Expected: Tapping a web tile (e.g. your Jellyfin URL) opens full-screen; logging in and reopening keeps the session.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/web app/src/main/AndroidManifest.xml app/src/main/java/com/cyprienbrisset/myportal/ui/home/HomeScreen.kt
git commit -m "feat: immersive WebView host for web tiles"
```

---

## PHASE 3 — Ambient banner (clock, greeting, weather)

### Task 10: SettingsRepository (DataStore) + weather-city screen

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/data/settings/SettingsRepository.kt`
- Create: `app/src/main/java/com/cyprienbrisset/myportal/ui/settings/WeatherSettingsScreen.kt`
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/AppNav.kt`

- [ ] **Step 1: SettingsRepository**

`data/settings/SettingsRepository.kt`:

```kotlin
package com.cyprienbrisset.myportal.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class WeatherLocation(val city: String, val lat: Double, val lon: Double)

class SettingsRepository(private val context: Context) {
    private val CITY = stringPreferencesKey("weather_city")
    private val LAT = doublePreferencesKey("weather_lat")
    private val LON = doublePreferencesKey("weather_lon")

    val weatherLocation: Flow<WeatherLocation?> = context.dataStore.data.map { p ->
        val city = p[CITY]; val lat = p[LAT]; val lon = p[LON]
        if (city != null && lat != null && lon != null) WeatherLocation(city, lat, lon) else null
    }

    suspend fun setWeatherLocation(loc: WeatherLocation) {
        context.dataStore.edit { it[CITY] = loc.city; it[LAT] = loc.lat; it[LON] = loc.lon }
    }
}
```

- [ ] **Step 2: WeatherSettingsScreen — geocode city via Open-Meteo geocoding**

`ui/settings/WeatherSettingsScreen.kt`:

```kotlin
package com.cyprienbrisset.myportal.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherSettingsScreen(onBack: () -> Unit, vm: WeatherSettingsViewModel = viewModel()) {
    var query by remember { mutableStateOf("") }
    val results by vm.results.collectAsState()

    Scaffold(topBar = {
        TopAppBar(title = { Text("Ville météo") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Retour") }
        })
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            OutlinedTextField(query, { query = it; vm.search(it) }, label = { Text("Rechercher une ville") })
            LazyColumn {
                items(results) { r ->
                    ListItem(
                        headlineContent = { Text(r.label) },
                        modifier = Modifier.clickable { vm.select(r); onBack() },
                    )
                }
            }
        }
    }
}
```

`WeatherSettingsViewModel` is defined in Task 11 (it depends on the geocoding call). If executing strictly in order, create a minimal stub now returning empty results and flesh it out in Task 11.

- [ ] **Step 3: Wire route** in `ui/AppNav.kt`:

```kotlin
composable(Routes.SETTINGS + "/weather") {
    com.cyprienbrisset.myportal.ui.settings.WeatherSettingsScreen(onBack = { nav.popBackStack() })
}
```

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/data/settings app/src/main/java/com/cyprienbrisset/myportal/ui/settings/WeatherSettingsScreen.kt app/src/main/java/com/cyprienbrisset/myportal/ui/AppNav.kt
git commit -m "feat: settings repository + weather city screen"
```

---

### Task 11: WeatherRepository (Open-Meteo) with parsing test

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/data/weather/WeatherModels.kt`
- Create: `app/src/main/java/com/cyprienbrisset/myportal/data/weather/WeatherRepository.kt`
- Create: `app/src/main/java/com/cyprienbrisset/myportal/ui/settings/WeatherSettingsViewModel.kt`
- Test: `app/src/test/java/com/cyprienbrisset/myportal/data/weather/WeatherParsingTest.kt`

- [ ] **Step 1: Models (serialization)**

`data/weather/WeatherModels.kt`:

```kotlin
package com.cyprienbrisset.myportal.data.weather

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ForecastResponse(
    @SerialName("current") val current: CurrentWeather? = null,
)

@Serializable
data class CurrentWeather(
    @SerialName("temperature_2m") val temperature: Double,
    @SerialName("weather_code") val weatherCode: Int,
)

@Serializable
data class GeocodeResponse(
    @SerialName("results") val results: List<GeocodeResult> = emptyList(),
)

@Serializable
data class GeocodeResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val admin1: String? = null,
)

/** Parsed UI-ready weather. */
data class Weather(val temperatureC: Int, val description: String)

fun weatherCodeToText(code: Int): String = when (code) {
    0 -> "Ciel clair"
    1, 2, 3 -> "Nuageux"
    45, 48 -> "Brouillard"
    in 51..67 -> "Pluie"
    in 71..77 -> "Neige"
    in 80..82 -> "Averses"
    in 95..99 -> "Orage"
    else -> "—"
}
```

- [ ] **Step 2: Write the failing parsing test**

`app/src/test/java/com/cyprienbrisset/myportal/data/weather/WeatherParsingTest.kt`:

```kotlin
package com.cyprienbrisset.myportal.data.weather

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherParsingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun parsesCurrentWeather() {
        val body = """{"current":{"temperature_2m":18.6,"weather_code":3}}"""
        val parsed = json.decodeFromString(ForecastResponse.serializer(), body)
        val w = parsed.current!!.toWeather()
        assertEquals(19, w.temperatureC)          // rounded
        assertEquals("Nuageux", w.description)
    }

    @Test fun parsesGeocode() {
        val body = """{"results":[{"name":"Lyon","latitude":45.75,"longitude":4.85,"country":"France"}]}"""
        val parsed = json.decodeFromString(GeocodeResponse.serializer(), body)
        assertEquals("Lyon", parsed.results.first().name)
    }
}
```

This requires a `toWeather()` extension — add it in the next step.

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*WeatherParsingTest*"`
Expected: FAIL — `toWeather` unresolved.

- [ ] **Step 4: Implement repository + `toWeather()`**

`data/weather/WeatherRepository.kt`:

```kotlin
package com.cyprienbrisset.myportal.data.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.roundToInt

fun CurrentWeather.toWeather(): Weather =
    Weather(temperatureC = temperature.roundToInt(), description = weatherCodeToText(weatherCode))

class WeatherRepository(
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun currentWeather(lat: Double, lon: Double): Weather? = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code"
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                val body = resp.body?.string() ?: return@use null
                json.decodeFromString(ForecastResponse.serializer(), body).current?.toWeather()
            }
        }.getOrNull()
    }

    suspend fun geocode(query: String): List<GeocodeResult> = withContext(Dispatchers.IO) {
        if (query.length < 2) return@withContext emptyList()
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=$query&count=5&language=fr"
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                val body = resp.body?.string() ?: return@use emptyList<GeocodeResult>()
                json.decodeFromString(GeocodeResponse.serializer(), body).results
            }
        }.getOrDefault(emptyList())
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*WeatherParsingTest*"`
Expected: PASS.

- [ ] **Step 6: WeatherSettingsViewModel** (used by Task 10 screen)

`ui/settings/WeatherSettingsViewModel.kt`:

```kotlin
package com.cyprienbrisset.myportal.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cyprienbrisset.myportal.data.settings.SettingsRepository
import com.cyprienbrisset.myportal.data.settings.WeatherLocation
import com.cyprienbrisset.myportal.data.weather.WeatherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class CityResult(val label: String, val city: String, val lat: Double, val lon: Double)

class WeatherSettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val weather = WeatherRepository()
    private val settings = SettingsRepository(app)
    private val _results = MutableStateFlow<List<CityResult>>(emptyList())
    val results: StateFlow<List<CityResult>> = _results

    fun search(q: String) = viewModelScope.launch {
        _results.value = weather.geocode(q).map {
            CityResult(
                label = listOfNotNull(it.name, it.admin1, it.country).joinToString(", "),
                city = it.name, lat = it.latitude, lon = it.longitude,
            )
        }
    }

    fun select(r: CityResult) = viewModelScope.launch {
        settings.setWeatherLocation(WeatherLocation(r.city, r.lat, r.lon))
    }
}
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/data/weather app/src/main/java/com/cyprienbrisset/myportal/ui/settings/WeatherSettingsViewModel.kt app/src/test/java/com/cyprienbrisset/myportal/data/weather
git commit -m "feat: Open-Meteo weather + geocoding repository with parsing tests"
```

---

### Task 12: AmbientBanner (clock, date, greeting, weather, next alarm)

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/ui/home/AmbientBanner.kt`
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/home/HomeScreen.kt`

- [ ] **Step 1: Extend HomeViewModel with time + weather + next alarm**

Add to `HomeViewModel`:

```kotlin
// imports: kotlinx.coroutines.flow.*, kotlinx.coroutines.delay, java.time.*
private val settings = SettingsRepository(app)
private val weatherRepo = WeatherRepository()

val now: StateFlow<LocalDateTime> = flow {
    while (true) { emit(LocalDateTime.now()); delay(1000) }
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LocalDateTime.now())

val weather: StateFlow<Weather?> = settings.weatherLocation
    .flatMapLatest { loc ->
        flow {
            while (true) {
                emit(loc?.let { weatherRepo.currentWeather(it.lat, it.lon) })
                delay(15 * 60 * 1000)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

// nextAlarm wired in Phase 4 (Task 17); default null for now.
```

(Add corresponding imports: `SettingsRepository`, `WeatherRepository`, `Weather`, `flow`, `flatMapLatest`, `delay`, `LocalDateTime`, `StateFlow`.)

- [ ] **Step 2: AmbientBanner composable**

`ui/home/AmbientBanner.kt`:

```kotlin
package com.cyprienbrisset.myportal.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.myportal.data.weather.Weather
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AmbientBanner(now: LocalDateTime, weather: Weather?, modifier: Modifier = Modifier) {
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    val dateFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)
    val greeting = when (now.hour) {
        in 5..11 -> "Bonjour"
        in 12..17 -> "Bon après-midi"
        else -> "Bonsoir"
    }
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(now.format(timeFmt), fontSize = 64.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground)
            Text("$greeting · ${now.format(dateFmt)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (weather != null) {
            Text("${weather.temperatureC}° · ${weather.description}",
                fontSize = 22.sp, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}
```

- [ ] **Step 3: Place the banner at the top of HomeScreen**

In `HomeScreen`, collect the new flows and render `AmbientBanner(now, weather)` above the settings icon / grid:

```kotlin
val now by vm.now.collectAsStateWithLifecycle()
val weather by vm.weather.collectAsStateWithLifecycle()
// inside Column, first child:
AmbientBanner(now = now, weather = weather)
```

- [ ] **Step 4: Build + manual verify**

Run: `./gradlew :app:installDebug`
Expected: Banner shows a live-updating clock, French date + greeting, and (after setting a city in Réglages → Ville météo) the temperature.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/ui/home
git commit -m "feat: ambient banner with live clock, date, greeting, weather"
```

---

## PHASE 4 — Alarm clock (rings reliably)

### Task 13: Alarm entity, DAO, repository, DB migration

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/data/alarm/AlarmEntity.kt`
- Create: `app/src/main/java/com/cyprienbrisset/myportal/data/alarm/AlarmDao.kt`
- Create: `app/src/main/java/com/cyprienbrisset/myportal/data/alarm/AlarmRepository.kt`
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/data/AppDatabase.kt`

- [ ] **Step 1: Entity**

`data/alarm/AlarmEntity.kt`:

```kotlin
package com.cyprienbrisset.myportal.data.alarm

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * repeatDays is a bitmask: bit 0 = Monday ... bit 6 = Sunday.
 * 0 means a one-shot alarm (fires once, then disables itself).
 */
@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val repeatDays: Int = 0,
    val label: String = "",
    val enabled: Boolean = true,
)
```

- [ ] **Step 2: DAO**

`data/alarm/AlarmDao.kt`:

```kotlin
package com.cyprienbrisset.myportal.data.alarm

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE enabled = 1")
    suspend fun enabledAlarms(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun byId(id: Long): AlarmEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alarm: AlarmEntity): Long

    @Delete suspend fun delete(alarm: AlarmEntity)
}
```

- [ ] **Step 3: Add to AppDatabase (bump version to 2)**

In `AppDatabase.kt`: add `AlarmEntity::class` to `entities`, bump `version = 2`, add `abstract fun alarmDao(): AlarmDao`. `fallbackToDestructiveMigration()` is already set, so no manual migration needed.

- [ ] **Step 4: Repository**

`data/alarm/AlarmRepository.kt`:

```kotlin
package com.cyprienbrisset.myportal.data.alarm

import kotlinx.coroutines.flow.Flow

class AlarmRepository(private val dao: AlarmDao) {
    fun observeAll(): Flow<List<AlarmEntity>> = dao.observeAll()
    suspend fun enabled(): List<AlarmEntity> = dao.enabledAlarms()
    suspend fun byId(id: Long): AlarmEntity? = dao.byId(id)
    suspend fun upsert(alarm: AlarmEntity): Long = dao.upsert(alarm)
    suspend fun delete(alarm: AlarmEntity) = dao.delete(alarm)
    suspend fun setEnabled(alarm: AlarmEntity, enabled: Boolean) = dao.upsert(alarm.copy(enabled = enabled))
}
```

- [ ] **Step 5: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/data
git commit -m "feat: alarm entity, DAO, repository; DB v2"
```

---

### Task 14: AlarmScheduler.nextTriggerTime() — critical logic (TDD)

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/alarm/AlarmScheduler.kt`
- Test: `app/src/test/java/com/cyprienbrisset/myportal/alarm/AlarmSchedulerTest.kt`

- [ ] **Step 1: Write the failing tests (pure time math)**

`app/src/test/java/com/cyprienbrisset/myportal/alarm/AlarmSchedulerTest.kt`:

```kotlin
package com.cyprienbrisset.myportal.alarm

import com.cyprienbrisset.myportal.data.alarm.AlarmEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class AlarmSchedulerTest {
    // Reference "now": Wednesday 2026-09-02, 10:00.
    private val now = LocalDateTime.of(2026, 9, 2, 10, 0)

    @Test fun oneShotLaterToday() {
        val a = AlarmEntity(hour = 14, minute = 30, repeatDays = 0)
        assertEquals(LocalDateTime.of(2026, 9, 2, 14, 30), nextTriggerTime(a, now))
    }

    @Test fun oneShotAlreadyPassedRollsToTomorrow() {
        val a = AlarmEntity(hour = 8, minute = 0, repeatDays = 0)
        assertEquals(LocalDateTime.of(2026, 9, 3, 8, 0), nextTriggerTime(a, now))
    }

    @Test fun repeatingPicksNextMatchingDay() {
        // Repeat Mondays only (bit 0). From Wednesday -> next Monday 2026-09-07.
        val a = AlarmEntity(hour = 7, minute = 0, repeatDays = 1 shl 0)
        assertEquals(LocalDateTime.of(2026, 9, 7, 7, 0), nextTriggerTime(a, now))
    }

    @Test fun repeatingTodayButTimePassedGoesNextWeek() {
        // Repeat Wednesdays (bit 2). 08:00 already passed -> next Wednesday.
        val a = AlarmEntity(hour = 8, minute = 0, repeatDays = 1 shl 2)
        assertEquals(LocalDateTime.of(2026, 9, 9, 8, 0), nextTriggerTime(a, now))
    }

    @Test fun repeatingTodayTimeNotPassedFiresToday() {
        val a = AlarmEntity(hour = 22, minute = 0, repeatDays = 1 shl 2) // Wednesday
        assertEquals(LocalDateTime.of(2026, 9, 2, 22, 0), nextTriggerTime(a, now))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*AlarmSchedulerTest*"`
Expected: FAIL — `nextTriggerTime` unresolved.

- [ ] **Step 3: Implement the scheduler (logic + Android scheduling)**

`alarm/AlarmScheduler.kt`:

```kotlin
package com.cyprienbrisset.myportal.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.cyprienbrisset.myportal.data.alarm.AlarmEntity
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

/** Pure function: next fire time for [alarm] relative to [from]. bit0=Mon..bit6=Sun. */
fun nextTriggerTime(alarm: AlarmEntity, from: LocalDateTime): LocalDateTime {
    val todayAt = from.toLocalDate().atTime(alarm.hour, alarm.minute)
    if (alarm.repeatDays == 0) {
        return if (todayAt.isAfter(from)) todayAt else todayAt.plusDays(1)
    }
    for (offset in 0..7) {
        val candidateDate = from.toLocalDate().plusDays(offset.toLong())
        val bit = candidateDate.dayOfWeek.bitIndex()
        val matches = (alarm.repeatDays and (1 shl bit)) != 0
        if (matches) {
            val candidate = candidateDate.atTime(alarm.hour, alarm.minute)
            if (candidate.isAfter(from)) return candidate
        }
    }
    // Fallback (should not happen): one week out.
    return todayAt.plusWeeks(1)
}

private fun DayOfWeek.bitIndex(): Int = this.value - 1 // MONDAY(1)->0 .. SUNDAY(7)->6

class AlarmScheduler(private val context: Context) {
    private val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(alarm: AlarmEntity) {
        if (!alarm.enabled) { cancel(alarm.id); return }
        val trigger = nextTriggerTime(alarm, LocalDateTime.now())
        val triggerMillis = trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val show = PendingIntent.getActivity(
            context, alarm.id.toInt(),
            Intent(context, com.cyprienbrisset.myportal.alarm.AlarmRingActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerMillis, show), firePendingIntent(alarm.id))
    }

    fun cancel(alarmId: Long) = am.cancel(firePendingIntent(alarmId))

    private fun firePendingIntent(alarmId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
        }
        return PendingIntent.getBroadcast(
            context, alarmId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*AlarmSchedulerTest*"`
Expected: PASS (all 5). `AlarmReceiver` / `AlarmRingActivity` are referenced but created in Tasks 15–16; the JVM unit test only exercises `nextTriggerTime`, so it compiles once those classes exist. If executing strictly in order, create empty stub classes for `AlarmReceiver` and `AlarmRingActivity` now, fleshed out next.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/alarm/AlarmScheduler.kt app/src/test/java/com/cyprienbrisset/myportal/alarm/AlarmSchedulerTest.kt
git commit -m "feat: AlarmScheduler with tested nextTriggerTime logic"
```

---

### Task 15: AlarmReceiver + BootReceiver + manifest

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/alarm/AlarmReceiver.kt`
- Create: `app/src/main/java/com/cyprienbrisset/myportal/alarm/BootReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: AlarmReceiver — launch ring UI + reschedule repeating**

`alarm/AlarmReceiver.kt`:

```kotlin
package com.cyprienbrisset.myportal.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cyprienbrisset.myportal.data.AppDatabase
import com.cyprienbrisset.myportal.data.alarm.AlarmRepository
import kotlinx.coroutines.runBlocking

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1)
        if (alarmId < 0) return

        // Show the ringing screen immediately.
        context.startActivity(
            Intent(context, AlarmRingActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_ALARM_ID, alarmId)
        )

        // Reschedule repeating alarms; disable one-shots.
        val repo = AlarmRepository(AppDatabase.get(context).alarmDao())
        val scheduler = AlarmScheduler(context)
        runBlocking {
            val alarm = repo.byId(alarmId) ?: return@runBlocking
            if (alarm.repeatDays == 0) {
                repo.setEnabled(alarm, false)
            } else {
                scheduler.schedule(alarm) // computes the next occurrence
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.cyprienbrisset.myportal.ALARM_FIRE"
        const val EXTRA_ALARM_ID = "alarm_id"
    }
}
```

- [ ] **Step 2: BootReceiver — reschedule all enabled alarms**

`alarm/BootReceiver.kt`:

```kotlin
package com.cyprienbrisset.myportal.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cyprienbrisset.myportal.data.AppDatabase
import com.cyprienbrisset.myportal.data.alarm.AlarmRepository
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val repo = AlarmRepository(AppDatabase.get(context).alarmDao())
        val scheduler = AlarmScheduler(context)
        runBlocking { repo.enabled().forEach { scheduler.schedule(it) } }
    }
}
```

- [ ] **Step 3: Register receivers + permissions in manifest**

Add permissions under `<manifest>`:

```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

Inside `<application>`:

```xml
<receiver android:name=".alarm.AlarmReceiver" android:exported="false" />
<receiver android:name=".alarm.BootReceiver" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/alarm/AlarmReceiver.kt app/src/main/java/com/cyprienbrisset/myportal/alarm/BootReceiver.kt app/src/main/AndroidManifest.xml
git commit -m "feat: alarm + boot receivers with reschedule logic"
```

---

### Task 16: AlarmRingActivity (full-screen, sound, snooze/dismiss)

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/alarm/AlarmRingActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Implement the ringing activity**

`alarm/AlarmRingActivity.kt`:

```kotlin
package com.cyprienbrisset.myportal.alarm

import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.myportal.data.AppDatabase
import com.cyprienbrisset.myportal.data.alarm.AlarmEntity
import com.cyprienbrisset.myportal.data.alarm.AlarmRepository
import com.cyprienbrisset.myportal.ui.theme.MyPortalTheme
import kotlinx.coroutines.runBlocking

class AlarmRingActivity : ComponentActivity() {
    private var ringtone: Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        startRinging()

        val alarmId = intent.getLongExtra(AlarmReceiver.EXTRA_ALARM_ID, -1)

        setContent {
            MyPortalTheme {
                Column(
                    Modifier.fillMaxSize().padding(48.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Réveil", fontSize = 48.sp)
                    Spacer(Modifier.height(48.dp))
                    Button(onClick = { stopAndFinish() }) { Text("Arrêter") }
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { snooze(alarmId) }) { Text("Snooze 10 min") }
                }
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true); setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    or android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    private fun startRinging() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(applicationContext, uri).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
            streamType = AudioManager.STREAM_ALARM
            play()
        }
    }

    private fun snooze(alarmId: Long) {
        // Schedule a one-shot 10 minutes out using a transient alarm entity id space is reused;
        // simplest: re-arm this same alarm 10 min later via a dedicated snooze entity.
        val snooze = AlarmEntity(id = alarmId, hour = 0, minute = 0, repeatDays = 0, enabled = true)
        // Recompute an explicit +10min trigger:
        AlarmSnooze.schedule(this, alarmId, minutes = 10)
        stopAndFinish()
    }

    private fun stopAndFinish() {
        ringtone?.stop(); ringtone = null; finish()
    }

    override fun onDestroy() {
        ringtone?.stop(); super.onDestroy()
    }
}
```

- [ ] **Step 2: Snooze helper (explicit +N minutes)**

Append to `alarm/AlarmScheduler.kt`:

```kotlin
object AlarmSnooze {
    fun schedule(context: Context, alarmId: Long, minutes: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerMillis = System.currentTimeMillis() + minutes * 60_000L
        val fire = PendingIntent.getBroadcast(
            context, alarmId.toInt(),
            Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_FIRE
                putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val show = PendingIntent.getActivity(
            context, alarmId.toInt(),
            Intent(context, AlarmRingActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerMillis, show), fire)
    }
}
```

(Remove the unused `snooze` local `AlarmEntity` in the activity — it was illustrative; keep only the `AlarmSnooze.schedule(...)` call.)

- [ ] **Step 3: Register activity in manifest (show when locked, exclude from recents)**

```xml
<activity
    android:name=".alarm.AlarmRingActivity"
    android:exported="false"
    android:excludeFromRecents="true"
    android:launchMode="singleInstance"
    android:showWhenLocked="true"
    android:turnScreenOn="true"
    android:theme="@style/Theme.MyPortal" />
```

- [ ] **Step 4: Build + on-device verify**

Run: `./gradlew :app:installDebug`
Expected: Create an alarm 1–2 minutes out (Task 17 UI, or temporarily trigger). At the set time the full-screen ring UI appears over standby and the alarm sound loops; **Arrêter** stops it; **Snooze** re-rings ~10 min later.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/alarm/AlarmRingActivity.kt app/src/main/java/com/cyprienbrisset/myportal/alarm/AlarmScheduler.kt app/src/main/AndroidManifest.xml
git commit -m "feat: full-screen alarm ring activity with sound, snooze, dismiss"
```

---

### Task 17: AlarmsScreen (list, create/edit) + wire scheduling + next-alarm banner

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/ui/alarms/AlarmsViewModel.kt`
- Create: `app/src/main/java/com/cyprienbrisset/myportal/ui/alarms/AlarmsScreen.kt`
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/AppNav.kt`
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/home/HomeViewModel.kt` (next-alarm)
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/home/AmbientBanner.kt` (show next alarm)

- [ ] **Step 1: AlarmsViewModel — CRUD + reschedule on every change**

`ui/alarms/AlarmsViewModel.kt`:

```kotlin
package com.cyprienbrisset.myportal.ui.alarms

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cyprienbrisset.myportal.alarm.AlarmScheduler
import com.cyprienbrisset.myportal.data.AppDatabase
import com.cyprienbrisset.myportal.data.alarm.AlarmEntity
import com.cyprienbrisset.myportal.data.alarm.AlarmRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AlarmRepository(AppDatabase.get(app).alarmDao())
    private val scheduler = AlarmScheduler(app)
    val alarms = repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(hour: Int, minute: Int, repeatDays: Int, label: String, id: Long = 0) = viewModelScope.launch {
        val newId = repo.upsert(AlarmEntity(id = id, hour = hour, minute = minute, repeatDays = repeatDays, label = label, enabled = true))
        repo.byId(newId)?.let { scheduler.schedule(it) }
    }

    fun toggle(alarm: AlarmEntity, enabled: Boolean) = viewModelScope.launch {
        repo.setEnabled(alarm, enabled)
        val updated = alarm.copy(enabled = enabled)
        if (enabled) scheduler.schedule(updated) else scheduler.cancel(alarm.id)
    }

    fun delete(alarm: AlarmEntity) = viewModelScope.launch {
        scheduler.cancel(alarm.id); repo.delete(alarm)
    }
}
```

- [ ] **Step 2: AlarmsScreen — list + time picker dialog**

`ui/alarms/AlarmsScreen.kt`:

```kotlin
package com.cyprienbrisset.myportal.ui.alarms

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmsScreen(onBack: () -> Unit, vm: AlarmsViewModel = viewModel()) {
    val alarms by vm.alarms.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Alarmes") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Retour") }
        }) },
        floatingActionButton = { FloatingActionButton(onClick = { showPicker = true }) { Icon(Icons.Filled.Add, "Ajouter") } },
    ) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            items(alarms, key = { it.id }) { a ->
                ListItem(
                    headlineContent = { Text("%02d:%02d".format(a.hour, a.minute)) },
                    supportingContent = { Text(if (a.repeatDays == 0) "Une fois" else repeatLabel(a.repeatDays)) },
                    trailingContent = {
                        Row {
                            Switch(checked = a.enabled, onCheckedChange = { vm.toggle(a, it) })
                            IconButton(onClick = { vm.delete(a) }) { Icon(Icons.Filled.Delete, "Supprimer") }
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }

    if (showPicker) {
        val state = rememberTimePickerState(is24Hour = true)
        var days by remember { mutableStateOf(0) }
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = { TextButton(onClick = {
                vm.save(state.hour, state.minute, days, ""); showPicker = false
            }) { Text("Enregistrer") } },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Annuler") } },
            title = { Text("Nouvelle alarme") },
            text = {
                Column {
                    TimePicker(state = state)
                    Spacer(Modifier.height(8.dp))
                    DayToggles(days = days, onChange = { days = it })
                }
            },
        )
    }
}

@Composable
private fun DayToggles(days: Int, onChange: (Int) -> Unit) {
    val labels = listOf("L", "M", "M", "J", "V", "S", "D")
    Row {
        labels.forEachIndexed { i, l ->
            FilterChip(
                selected = (days and (1 shl i)) != 0,
                onClick = { onChange(days xor (1 shl i)) },
                label = { Text(l) },
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}

private fun repeatLabel(mask: Int): String {
    val labels = listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")
    return labels.filterIndexed { i, _ -> (mask and (1 shl i)) != 0 }.joinToString(" ")
}
```

- [ ] **Step 3: Wire route** in `ui/AppNav.kt`:

```kotlin
composable(Routes.ALARMS) {
    com.cyprienbrisset.myportal.ui.alarms.AlarmsScreen(onBack = { nav.popBackStack() })
}
```

- [ ] **Step 4: Next-alarm in HomeViewModel + banner**

Add to `HomeViewModel`:

```kotlin
private val alarmRepo = AlarmRepository(AppDatabase.get(app).alarmDao())
val nextAlarm: StateFlow<LocalDateTime?> = alarmRepo.observeAll().map { list ->
    list.filter { it.enabled }
        .map { nextTriggerTime(it, LocalDateTime.now()) }
        .minOrNull()
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
```

(Imports: `AlarmRepository`, `nextTriggerTime`, `map`.) Pass `nextAlarm` into `AmbientBanner` and render, when non-null:

```kotlin
// in AmbientBanner signature add: nextAlarm: LocalDateTime? = null
// under the greeting Text:
if (nextAlarm != null) {
    Text("⏰ ${nextAlarm.format(DateTimeFormatter.ofPattern("EEE HH:mm", Locale.FRENCH))}",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
```

- [ ] **Step 5: Build + on-device verify (end-to-end)**

Run: `./gradlew :app:installDebug`
Expected: Create an alarm → it appears with a repeat summary and a working enable switch; the Home banner shows the next alarm; at the set time the ring screen fires (validating Task 16 end-to-end). Toggle off cancels it.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/ui/alarms app/src/main/java/com/cyprienbrisset/myportal/ui/AppNav.kt app/src/main/java/com/cyprienbrisset/myportal/ui/home
git commit -m "feat: alarms screen with scheduling + next-alarm in banner"
```

---

## Self-Review (author checklist, completed)

**Spec coverage:**
- Launcher HOME → Task 3 ✅
- Tile grid + app launch → Tasks 4–7 ✅
- Web tiles (immersive WebView) → Task 9 ✅
- Settings + tile management (add/reorder/delete) → Task 8 ✅
- Ambient banner (clock, date, greeting) → Task 12 ✅
- Weather (Open-Meteo, fixed city) → Tasks 10–12 ✅
- Alarm subsystem (rings, snooze/dismiss, repeat, reboot) → Tasks 13–17 ✅
- Next-alarm indicator → Task 17 ✅

**Out of scope confirmed absent:** no launch-on-wake, no categories, no resume, no auto-geolocation. ✅

**Type consistency:** `nextTriggerTime(AlarmEntity, LocalDateTime)`, `AlarmScheduler.schedule/cancel`, `AlarmReceiver.ACTION_FIRE/EXTRA_ALARM_ID`, `WebAppActivity.EXTRA_URL`, `TileRepository.add/reorder/delete`, repeat bitmask (bit0=Mon..bit6=Sun) — all consistent across tasks.

**Known ordering seams (called out in tasks):** `WebAppActivity` referenced in Task 7 but built in Task 9; `AlarmReceiver`/`AlarmRingActivity` referenced in Task 14 but built in Tasks 15–16; `WeatherSettingsViewModel` referenced in Task 10 but built in Task 11. Each has an explicit stub-now/flesh-later note.

**Risks (from spec §10):** default-launcher selection on locked Portal (Task 3 note + adb fallback), alarm reliability via `setAlarmClock` (Task 16 on-device verify), WebView DRM (Netflix as APP tile), preview SDK/AGP versions (Task 1 verify gate).
