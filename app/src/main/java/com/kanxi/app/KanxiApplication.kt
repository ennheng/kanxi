package com.kanxi.app

import android.app.Application
import android.webkit.WebView
import com.kanxi.app.bilibili.BilibiliLinkParser
import com.kanxi.app.bilibili.BilibiliPageListResolver
import com.kanxi.app.data.KanxiDatabase
import com.kanxi.app.data.OperaRepository
import com.kanxi.app.data.UserPreferencesRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class KanxiApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
        container = AppContainer(this)
    }
}

class AppContainer(application: Application) {
    private val database = KanxiDatabase.create(application)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    val operaRepository = OperaRepository(database.operaDao())
    val preferencesRepository = UserPreferencesRepository(application)
    val bilibiliLinkParser = BilibiliLinkParser(httpClient)
    val bilibiliPageListResolver = BilibiliPageListResolver(httpClient)
}
