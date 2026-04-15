package com.rayworks.library.compose

import androidx.core.text.HtmlCompat
import com.rayworks.library.util.Utils

/**
 * A text segment in the gap-fill content.
 *
 * The sealed hierarchy is used to represent the interleaved mix of plain text
 * and interactive blanks that make up a gap-fill string.
 */
sealed class Segment {
    /**
     * A non-interactive text run.
     *
     * [html] contains the HTML-formatted string produced by
     * [Utils.getHtmlTextWithMarkups], ready to be parsed by [HtmlCompat.fromHtml].
     */
    data class TextSegment(val html: String) : Segment()

    /**
     * An interactive blank.
     *
     * @param index              zero-based position of this blank among all blanks
     * @param defaultPlaceholder text shown before the user answers;
     *                           equals [BLANK_TAG] when the source `{}` was empty
     */
    data class BlankSegment(
        val index: Int,
        val defaultPlaceholder: String,
    ) : Segment()
}

/**
 * The result of parsing a raw gap-fill string.
 *
 * @param segments             ordered list of [Segment]s
 * @param errorCorrectionEnabled `true` when every `{…}` block contained non-empty content,
 *                               enabling the error-correction (underline) rendering mode
 * @param totalBlanks          number of [Segment.BlankSegment]s in [segments]
 */
data class GapFillParsed(
    val segments: List<Segment>,
    val errorCorrectionEnabled: Boolean,
    val totalBlanks: Int,
)

/** Placeholder text shown for empty `{}` blanks (matches `BLANK_TAG` in the Java View). */
internal const val BLANK_TAG = "____"

/**
 * Parses [rawText] into a [GapFillParsed] structure.
 *
 * Processing steps:
 * 1. Normalise: prepend/append a space when the string starts or ends with `{}`.
 * 2. Apply markdown → HTML via [Utils.getHtmlTextWithMarkups] (same first step as the
 *    original [com.rayworks.library.GapFillTextView]).
 * 3. Split the HTML string on `{…}` patterns to produce alternating [Segment.TextSegment]
 *    and [Segment.BlankSegment] entries.
 *
 * @throws IllegalArgumentException if [rawText] is empty or contains no blank markers.
 */
fun parseRawText(rawText: String): GapFillParsed {
    require(rawText.isNotEmpty()) { "rawText cannot be empty" }

    // Normalise: ensure text never starts or ends directly with a blank block.
    var text = rawText
    if (text.endsWith("}")) text += " "
    if (text.startsWith("{")) text = " $text"

    // Apply markdown → HTML so TextSegment stores ready-to-render HTML strings.
    val htmlText = Utils.getHtmlTextWithMarkups(text)

    val blankRegex = Regex("\\{(.*?)\\}")
    val segments = mutableListOf<Segment>()
    var lastEnd = 0
    var blankIndex = 0
    var allBlanksHaveContent = true

    for (match in blankRegex.findAll(htmlText)) {
        // Capture the text that precedes this blank.
        if (match.range.first > lastEnd) {
            segments += Segment.TextSegment(htmlText.substring(lastEnd, match.range.first))
        }

        val inner = match.groupValues[1]
        val defaultPlaceholder: String
        if (inner.isEmpty()) {
            defaultPlaceholder = BLANK_TAG
            allBlanksHaveContent = false
        } else {
            // Strip any HTML entities from the placeholder for plain-text display.
            defaultPlaceholder =
                HtmlCompat.fromHtml(inner, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        }

        segments += Segment.BlankSegment(blankIndex, defaultPlaceholder)
        blankIndex++
        lastEnd = match.range.last + 1
    }

    // Capture any trailing text after the last blank.
    if (lastEnd < htmlText.length) {
        segments += Segment.TextSegment(htmlText.substring(lastEnd))
    }

    require(blankIndex > 0) { "Target String has illegal format: no blank markers '{}' found." }

    return GapFillParsed(
        segments = segments,
        errorCorrectionEnabled = allBlanksHaveContent,
        totalBlanks = blankIndex,
    )
}
