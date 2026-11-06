package com.blackledger.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate

sealed interface Screen {
    data object Todo : Screen
    data object Shop : Screen
    data object Report : Screen
    data object Log : Screen
    data class Edit(val id: Int?) : Screen
}

class MainActivity : ComponentActivity() {
    private val screen = mutableStateOf<Screen>(Screen.Todo)
    private val today = mutableStateOf(LocalDate.now())
    private val intro = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val black = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(black, black)
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        if (intent.getBooleanExtra("shop", false)) screen.value = Screen.Shop
        // Opening on a cold start only; not when the widget or a notification sent you here mid-task,
        // and not when Android's "Remove animations" is on.
        val animations = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        intro.value = savedInstanceState == null && animations && !intent.hasExtra("shop") && !intent.hasExtra("note")
        Ledger.get(this)
        Alarms.schedule(this)
        LedgerWidget.refresh(this)
        setContent { App(screen, today.value, intro) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        screen.value = if (intent.getBooleanExtra("shop", false)) Screen.Shop else Screen.Todo
    }

    override fun onResume() {
        super.onResume()
        // Coming back after midnight: settle yesterday into the log and show the new day.
        Ledger.get(this)
        today.value = LocalDate.now()
    }
}

@Composable
fun App(screen: MutableState<Screen>, today: LocalDate, intro: MutableState<Boolean>) {
    val data by Ledger.state
    val go = { s: Screen -> screen.value = s }
    val current = screen.value
    BackHandler(enabled = current != Screen.Todo) {
        screen.value = if (current == Screen.Report) Screen.Shop else Screen.Todo
    }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Ink.Black).systemBarsPadding().imePadding()
                .padding(horizontal = 24.dp)
        ) {
            when (current) {
                Screen.Todo -> DirectivesScreen(data, today, go)
                Screen.Shop -> RequisitionScreen(data, go)
                Screen.Report -> ReportScreen(data, today, go)
                Screen.Log -> LogScreen(data, today, go)
                is Screen.Edit -> key(current.id) { EditorScreen(data, current.id, today, go) }
            }
        }
        // Cold-start opening, drawn over the app so the app is already there underneath.
        if (intro.value) {
            val rows = data.today(today)
            Intro(
                dots = rows.sortedBy { it.status != Status.CLEARED }.map { it.dot() },
                cleared = rows.count { it.status == Status.CLEARED },
                onDone = { intro.value = false },
            )
        }
    }
}
