package com.autolyrics.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.autolyrics.media.MediaTracker
import com.autolyrics.model.LyricsState
import com.autolyrics.model.LyricsStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LyricsScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {

    private val mediaTracker = MediaTracker.getInstance(carContext)
    private var currentState: LyricsState = mediaTracker.state.value

    init {
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        // Collect state changes
        lifecycleScope.launch {
            mediaTracker.state.collectLatest { state ->
                currentState = state
                invalidate()
            }
        }

        // Loop to sync lyrics position rendering
        lifecycleScope.launch {
            while (isActive) {
                delay(150)
                if (currentState.isPlaying && currentState.status == LyricsStatus.FOUND) {
                    invalidate()
                }
            }
        }
    }

    override fun onGetTemplate(): Template {
        val state = currentState
        val paneBuilder = Pane.Builder()
        val titleText = state.track?.let { "${it.artist} - ${it.title}" } ?: "Waiting for music..."

        when (state.status) {
            LyricsStatus.FOUND -> {
                val lines = state.lines
                if (lines.isEmpty()) {
                    paneBuilder.addRow(Row.Builder().setTitle("♪").build())
                    return buildTemplate(paneBuilder, titleText)
                }
                val currentIdx = state.currentIndex.coerceIn(0, lines.lastIndex)

                val displayCount = 4
                val desiredCurrentRow = 1
                val winStart = maxOf(0, currentIdx - desiredCurrentRow)
                val winEnd = minOf(lines.size, winStart + displayCount)
                val adjStart = maxOf(0, winEnd - displayCount)

                for (i in adjStart until winEnd) {
                    val line = lines[i]
                    val isCurrent = i == currentIdx
                    val prefix = if (isCurrent) "▶  " else ""
                    val text = line.text.ifBlank { "♪" }
                    val content = "$prefix$text"

                    paneBuilder.addRow(Row.Builder().setTitle(content).build())
                }
            }
            LyricsStatus.PLAIN_ONLY -> {
                val text = if (state.lines.isNotEmpty()) "Lyrics not synced" else "♪"
                paneBuilder.addRow(Row.Builder().setTitle(text).build())
            }
            LyricsStatus.LOADING -> {
                paneBuilder.setLoading(true)
            }
            LyricsStatus.NO_MEDIA -> {
                paneBuilder.addRow(Row.Builder().setTitle("Play a song to see lyrics").build())
            }
            else -> {
                paneBuilder.addRow(Row.Builder().setTitle("No lyrics found for this track").build())
            }
        }

        return buildTemplate(paneBuilder, titleText)
    }

    private fun buildTemplate(paneBuilder: Pane.Builder, titleText: String): Template {
        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle(titleText)
            .setHeaderAction(Action.APP_ICON)
            .build()
    }
}
