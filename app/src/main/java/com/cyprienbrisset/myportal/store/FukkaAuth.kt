package com.cyprienbrisset.myportal.store

// TEMPORARY SPIKE: FukkaStore Google-account auth.
// Corrected engine replicating Aurora Store's EXACT AC2DM exchange (oauth_token -> aasToken),
// including the GMS caller params that the previous spike was missing.

import android.content.Context
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.helpers.AuthHelper
import com.aurora.gplayapi.helpers.SearchHelper
import java.util.Locale
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val TOKEN_AUTH_URL = "https://android.clients.google.com/auth"
private const val BUILD_VERSION_SDK = 28
private const val PLAY_SERVICES_VERSION_CODE = 19629032

private val httpClient = OkHttpClient()

/**
 * AC2DM exchange: oauth_token -> aasToken, replicating Aurora's AC2DMTask exactly.
 * Returns response["Token"], throws on failure.
 */
suspend fun exchangeAasToken(email: String, oauthToken: String): String = withContext(Dispatchers.IO) {
    // Params copied verbatim from Aurora's AC2DMTask.getAC2DMResponse (order-independent, url-joined k=v&...).
    val params = linkedMapOf<String, Any>(
        "lang" to Locale.getDefault().toString().replace("_", "-"),
        "google_play_services_version" to PLAY_SERVICES_VERSION_CODE,
        "sdk_version" to BUILD_VERSION_SDK,
        "device_country" to Locale.getDefault().country.lowercase(Locale.US),
        "Email" to email,
        "service" to "ac2dm",
        "get_accountid" to 1,
        "ACCESS_TOKEN" to 1,
        "callerPkg" to "com.google.android.gms",
        "add_account" to 1,
        "Token" to oauthToken,
        "callerSig" to "38918a453d07199354f8b19af05ec6562ced5788",
        "droidguard_results" to "null",
    )

    val body = params.map { "${it.key}=${it.value}" }.joinToString(separator = "&")

    val request = Request.Builder()
        .url(TOKEN_AUTH_URL)
        .addHeader("app", "com.google.android.gms")
        .addHeader("User-Agent", "")
        .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
        .build()

    val raw = httpClient.newCall(request).execute().use { response ->
        String(response.body?.bytes() ?: ByteArray(0))
    }

    val map = parseKeyValueResponse(raw)
    val aasToken = map["Token"]
    if (aasToken.isNullOrBlank()) {
        // Include the raw response head for diagnostics (e.g. Error=BadAuthentication, Url=...).
        throw IllegalStateException("Could not generate AAS Token. Response head: " + raw.take(300))
    }
    aasToken
}

/**
 * Build gplayapi [AuthData] from an AAS token using the bundled device profile,
 * matching Aurora's AuthProvider.buildGoogleAuthData / AuthHelper.build signature.
 */
suspend fun buildAuthDataFromAas(context: Context, email: String, aasToken: String): AuthData =
    withContext(Dispatchers.IO) {
        val props = Properties().apply {
            context.assets.open("fukka_device.properties").use { load(it) }
        }
        // gplayapi 3.2.6 published AuthHelper.build has no tokenType enum; the 2nd arg IS the AAS
        // token: build(email, aasToken, properties, locale). Verified against the resolved jar.
        AuthHelper.build(
            email,
            aasToken,
            props,
            Locale.getDefault(),
        )
    }

/**
 * Sanity search to prove the AuthData works end-to-end. Returns display names of results.
 */
suspend fun searchTitles(authData: AuthData, query: String): List<String> = withContext(Dispatchers.IO) {
    SearchHelper(authData).searchResults(query).appList.map { it.displayName }
}

private fun parseKeyValueResponse(response: String): Map<String, String> {
    val map = HashMap<String, String>()
    response.split("\n", "\r").forEach { line ->
        if (line.isBlank()) return@forEach
        val kv = line.split("=", limit = 2)
        if (kv.size >= 2) map[kv[0]] = kv[1]
    }
    return map
}
