package com.cyprienbrisset.myportal.store

data class StoreApp(
    val packageName: String,
    val title: String,
    val developer: String,
    val iconUrl: String,
    val versionCode: Int,
)

data class ApkFile(val name: String, val url: String, val size: Long)

class StoreException(message: String, cause: Throwable? = null) : Exception(message, cause)
