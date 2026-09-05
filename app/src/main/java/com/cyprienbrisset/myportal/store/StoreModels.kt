package com.cyprienbrisset.myportal.store

data class StoreApp(
    val packageName: String,
    val title: String,
    val developer: String,
    val iconUrl: String,
    val versionCode: Int,
)

data class ApkFile(val name: String, val url: String, val size: Long)

/** A browsable store category (or the synthetic "Populaires" entry). */
data class StoreCategory(val key: String, val title: String, val browseUrl: String?)

class StoreException(message: String, cause: Throwable? = null) : Exception(message, cause)
