package com.autolyrics.auto

import android.content.Context
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.autolyrics.media.MediaTracker
import com.autolyrics.model.LyricsState
import com.autolyrics.model.LyricsStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LyricsScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {

    private val mediaTracker = MediaTracker.getInstance(carContext)
    private val prefs = carContext.getSharedPreferences("auto_lyrics_prefs", Context.MODE_PRIVATE)
    private var currentState: LyricsState = mediaTracker.state.value

    private var stateJob: Job? = null
    private var loopJob: Job? = null
    private var lastRenderedIdx = -1

    init {
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        stateJob?.cancel()
        stateJob = lifecycleScope.launch {
            mediaTracker.state.collectLatest { state ->
                currentState = state
                lastRenderedIdx = -1
                invalidate()
            }
        }

        loopJob?.cancel()
        loopJob = lifecycleScope.launch {
            while (isActive) {
                delay(150)
                if (currentState.isPlaying && currentState.status == LyricsStatus.FOUND) {
                    val currentIdx = resolveCurrentIndex(currentState)
                    if (currentIdx != lastRenderedIdx) {
                        lastRenderedIdx = currentIdx
                        invalidate()
                    }
                }
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        stateJob?.cancel()
        loopJob?.cancel()
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
                val currentIdx = resolveCurrentIndex(state)

                val displayCount = 4
                val desiredCurrentRow = 1
                val winStart = if (currentIdx >= 0) maxOf(0, currentIdx - desiredCurrentRow) else 0
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

    private fun resolveCurrentIndex(state: LyricsState): Int {
        val lines = state.lines
        if (lines.isEmpty()) return -1

        val adjustedPositionMs = try {
            mediaTracker.getCurrentPositionMs() + prefs.getLong("aa_offset_ms", 0L)
        } catch (_: Exception) {
            return state.currentIndex.coerceIn(-1, lines.lastIndex)
        }

        var idx = -1
        for (i in lines.indices) {
            if (lines[i].timeMs <= adjustedPositionMs) {
                idx = i
            } else {
                break
            }
        }
        return idx
    }
}
