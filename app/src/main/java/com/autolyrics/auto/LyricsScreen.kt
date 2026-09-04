package com.autolyrics.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.autolyrics.media.MediaTracker
import com.autolyrics.model.LyricLine
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
                delay(500)
                if (currentState.isPlaying && currentState.status == LyricsStatus.FOUND) {
                    invalidate()
                }
            }
        }
    }

    private fun getPositionMs(): Long {
        return try {
            mediaTracker.getCurrentPositionMs()
        } catch (_: Exception) {
            0L
        }
    }

    private fun findLineIndex(lines: List<LyricLine>, posMs: Long): Int {
        var idx = -1
        for (i in lines.indices) {
            if (lines[i].timeMs <= posMs) idx = i
            else break
        }
        return idx
    }

    override fun onGetTemplate(): Template {
        val state = currentState
        val paneBuilder = Pane.Builder()
        val titleText = state.track?.let { "${it.artist} - ${it.title}" } ?: "Waiting for music..."

        when (state.status) {
            LyricsStatus.FOUND -> {
                val lines = state.lines
                val posMs = getPositionMs()
                val currentIdx = findLineIndex(lines, posMs).coerceAtLeast(0)

                val displayCount = 4
                val half = displayCount / 2
                val winStart = maxOf(0, currentIdx - half)
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

        // Action needed by templates as headers
        val headerAction = Action.APP_ICON

        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle(titleText)
            .setHeaderAction(headerAction)
            .build()
    }
}
