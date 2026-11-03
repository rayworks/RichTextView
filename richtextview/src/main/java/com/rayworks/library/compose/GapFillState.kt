package com.rayworks.library.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
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
     * Returns `true` when every blank index from 0 until [totalBlanks] has an answer.
     *
     * Unlike comparing `filledAnswers.size`, this is not fooled by sparse or out-of-range keys
     * written directly to [filledAnswers].
     */
    fun isComplete(totalBlanks: Int): Boolean =
        (0 until totalBlanks).all { filledAnswers.containsKey(it) }

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
     * @throws IllegalStateException if not every blank has an answer, or [filledAnswers]
     *         contains keys that are not blank indices of [rawText].
     */
    fun formattedFullText(rawText: String): String {
        // Apply the same normalisation used during parsing.
        var normalised = rawText
        if (normalised.endsWith("}")) normalised += " "
        if (normalised.startsWith("{")) normalised = " $normalised"

        // Use a regex to match both {} and {placeholder} blank markers so that the
        // method works regardless of whether blanks had default placeholders.
        val blankRegex = Regex("\\{.*?\\}")
        val matches = blankRegex.findAll(normalised).toList()
        val blankCount = matches.size
        check(filledAnswers.keys == (0 until blankCount).toSet()) {
            "Answers must contain exactly one entry per blank: expected keys " +
                "0..${blankCount - 1}, got ${filledAnswers.keys.sorted()}"
        }

        return buildString {
            var lastEnd = 0
            matches.forEachIndexed { i, match ->
                append(normalised.substring(lastEnd, match.range.first))
                append('{')
                append(filledAnswers[i])
                append('}')
                lastEnd = match.range.last + 1
            }
            append(normalised.substring(lastEnd))
        }
    }

    companion object {
        /**
         * [Saver] that keeps the filled answers and the open input overlay across
         * configuration changes and process death (used by [rememberGapFillState]).
         */
        val Saver: Saver<GapFillState, Any> = listSaver(
            save = { state ->
                listOf(
                    ArrayList(state.filledAnswers.keys),
                    ArrayList(state.filledAnswers.values),
                    state.activeBlankIndex ?: NO_ACTIVE_BLANK,
                )
            },
            restore = { saved ->
                @Suppress("UNCHECKED_CAST")
                GapFillState().apply {
                    val keys = saved[0] as List<Int>
                    val values = saved[1] as List<String>
                    filledAnswers = keys.zip(values).toMap()
                    activeBlankIndex = (saved[2] as Int).takeIf { it != NO_ACTIVE_BLANK }
                }
            },
        )

        private const val NO_ACTIVE_BLANK = -1
    }
}

/**
 * Creates and remembers a [GapFillState] that survives recompositions, configuration changes
 * (e.g. rotation) and process death.
 */
@Composable
fun rememberGapFillState(): GapFillState =
    rememberSaveable(saver = GapFillState.Saver) { GapFillState() }
