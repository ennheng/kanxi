package com.kanxi.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.kanxi.app.ui.KanxiApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: KanxiViewModel by viewModels {
        KanxiViewModelFactory(application as KanxiApplication)
    }
    private val pendingSharedText = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) acceptShareIntent(intent, clearCurrentIntent = true)

        setContent {
            val sharedText by pendingSharedText.collectAsState()
            KanxiApp(
                viewModel = viewModel,
                pendingSharedText = sharedText,
                onSharedTextConsumed = {
                    lifecycleScope.launch { pendingSharedText.emit(null) }
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Keep the Activity's canonical launcher intent. Otherwise a later recreation can replay
        // ACTION_SEND, and ActivityScenario cannot reliably match the running Activity instance.
        acceptShareIntent(intent, clearCurrentIntent = false)
    }

    private fun acceptShareIntent(intent: Intent?, clearCurrentIntent: Boolean) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT) ?: return
        val text = shared
            .subSequence(0, minOf(shared.length, 4_000))
            .toString()
            .trim()
        if (text.isNotEmpty()) {
            pendingSharedText.value = text
            if (clearCurrentIntent) {
                // A cold ACTION_SEND launch becomes an ordinary launcher intent after ingestion,
                // so rotation cannot replay an already-consumed share event.
                setIntent(
                    Intent(this, MainActivity::class.java).apply {
                        action = Intent.ACTION_MAIN
                        addCategory(Intent.CATEGORY_LAUNCHER)
                    },
                )
            }
        }
    }
}
