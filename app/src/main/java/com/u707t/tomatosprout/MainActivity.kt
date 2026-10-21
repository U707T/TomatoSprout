package com.u707t.tomatosprout

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.u707t.tomatosprout.notify.FocusCommandStore
import com.u707t.tomatosprout.ui.nav.TomatoApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var navRequest by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            TomatoApp(
                initialNav = navRequest,
                onNavConsumed = { navRequest = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        intent.getStringExtra(EXTRA_FOCUS_ACTION)?.let { action ->
            FocusCommandStore.put(this, action)
            intent.removeExtra(EXTRA_FOCUS_ACTION)
        }
        navRequest = intent.getStringExtra(EXTRA_NAVIGATE)
    }

    companion object {
        const val EXTRA_NAVIGATE = "navigate"
        const val EXTRA_FOCUS_ACTION = "focus_action"
        const val NAV_FOCUS = "focus"
    }
}
