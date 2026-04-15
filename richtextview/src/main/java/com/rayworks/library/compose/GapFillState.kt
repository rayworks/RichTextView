package com.rayworks.library.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Mutable runtime state for [GapFillText].
 *
 * This class is a Compose state holder: its properties are backed by [mutableStateOf] so any
 * composable that reads them is automatically recomposed when they change.
 *
 * Hoist an instance outside the composable tree to observe and manipulate the answers.
 * Create and remember one with [rememberGapFillState].
 */
class GapFillState {

    /** Map from blank index (0-based) to the answer string chosen or typed by the user. */
    var filledAnswers: Map<Int, String> by mutableStateOf(emptyMap())

    /**
     * Index of the blank for which an input overlay (popup or editor dialog) is currently
     * open, or `null` when no overlay is visible.
     */
    var activeBlankIndex: Int? by mutableStateOf(null)

    /** Clears all filled answers and closes any open input overlay. */
    fun reset() {
        filledAnswers = emptyMap()
        activeBlankIndex = null
    }

    /**
     * Returns the first blank index that has not yet been answered, or `null` if all blanks
     * have been filled.
     *
     * Useful for scrolling the UI to the next unfilled blank (mirrors `getHintLineIndex()`).
     */
    fun firstUnfilledBlankIndex(segments: List<Segment>): Int? =
        segments
            .filterIsInstance<Segment.BlankSegment>()
            .firstOrNull { !filledAnswers.containsKey(it.index) }
            ?.index

    /**
     * Returns the filled text with every answered blank substituted back into [rawText].
     *
     * @throws IllegalStateException if not all blanks have been answered.
     */
    fun formattedFullText(rawText: String): String {
        // Apply the same normalisation used during parsing.
        var normalised = rawText
        if (normalised.endsWith("}")) normalised += " "
        if (normalised.startsWith("{")) normalised = " $normalised"

        val parts = normalised.split("{}")
        val blankCount = parts.size - 1
        check(filledAnswers.size == blankCount) {
            "Not all blanks have been filled: expected $blankCount, got ${filledAnswers.size}"
        }

        return buildString {
            append(parts[0])
            for (i in 0 until blankCount) {
                append('{')
                append(filledAnswers[i])
                append('}')
                append(parts[i + 1])
            }
        }
    }
}

/** Creates and [remember]s a [GapFillState] instance that survives recompositions. */
@Composable
fun rememberGapFillState(): GapFillState = remember { GapFillState() }
