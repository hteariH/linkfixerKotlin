package com.mamoru.service.url

interface UrlHandler {
    fun getType(): String
    fun findUrls(text: String): List<String>
    fun convertUrl(url: String): String
    fun canHandle(url: String): Boolean
}