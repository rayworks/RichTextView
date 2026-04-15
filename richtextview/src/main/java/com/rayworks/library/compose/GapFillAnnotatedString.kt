package com.rayworks.library.compose

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import com.rayworks.library.R
import com.rayworks.library.listener.AnswerCorrectionChecker
import com.rayworks.library.text.InputStyle

/** Annotation tag attached to every blank span so tap handlers can identify them. */
const val BLANK_ANNOTATION_TAG = "BLANK"

/**
 * Answer string that indicates the user selected the "delete" option.
 * Matches [com.rayworks.library.GapFillTextView.DELETION_SYMBOL].
 */
internal const val DELETION_SYMBOL = "-"

/** Prefix for [InlineTextContent] keys of blanks showing the deletion icon. */
private const val DELETION_KEY_PREFIX = "blank_del_"

// ── Public API ────────────────────────────────────────────────────────────────

/**
 * Builds the [AnnotatedString] that represents the complete gap-fill text.
 *
 * For every [Segment.BlankSegment] the function:
 * - Appends an inline-content placeholder (key `"blank_del_$index"`) when the answer is
 *   the deletion symbol — the matching [InlineTextContent] entry is built by [buildInlineContent].
 * - Renders the answered text in [GapFillConfig.correctColor] or [GapFillConfig.wrongColor]
 *   (POPUP_WINDOW mode) or [Color.Black] (EDITOR_TEXT mode) when the blank is answered.
 * - Renders [Segment.BlankSegment.defaultPlaceholder] with optional underline decoration
 *   when the blank has not yet been answered.
 * - Appends a trailing space after each blank span (mirrors the original
 *   `stringBuilder.append(" ")`).
 *
 * Each blank span carries a [BLANK_ANNOTATION_TAG] annotation whose value is the blank index
 * as a decimal string, enabling tap handlers to resolve which blank was tapped.
 *
 * @param parsed          result from [parseRawText]
 * @param filledAnswers   current map of blank-index → answer
 * @param config          immutable gap-fill configuration
 * @param defaultTextColor colour used for unanswered placeholders
 * @param answerChecker   optional checker; used to colour answered blanks in POPUP_WINDOW mode
 */
fun buildGapFillAnnotatedString(
    parsed: GapFillParsed,
    filledAnswers: Map<Int, String>,
    config: GapFillConfig,
    defaultTextColor: Color,
    answerChecker: AnswerCorrectionChecker?,
): AnnotatedString {
    // Unanswered blanks get an underline only in error-correction + review mode.
    val blankDecoration =
        if (parsed.errorCorrectionEnabled && config.reviewMode)
            TextDecoration.Underline
        else
            TextDecoration.None

    return buildAnnotatedString {
        for (segment in parsed.segments) {
            when (segment) {
                is Segment.TextSegment -> {
                    // TextSegment.html already holds the output of Utils.getHtmlTextWithMarkups.
                    val spanned = HtmlCompat.fromHtml(
                        segment.html,
                        HtmlCompat.FROM_HTML_MODE_LEGACY,
                    )
                    append(spannedToAnnotatedString(spanned))
                }

                is Segment.BlankSegment -> {
                    val answer = filledAnswers[segment.index]
                    when {
                        answer == DELETION_SYMBOL -> {
                            // Use inline content so the deletion icon image is rendered.
                            val key = "$DELETION_KEY_PREFIX${segment.index}"
                            pushStringAnnotation(BLANK_ANNOTATION_TAG, segment.index.toString())
                            appendInlineContent(key, "[del]")
                            pop()
                        }

                        answer != null -> {
                            val color = if (
                                config.inputStyle == InputStyle.POPUP_WINDOW &&
                                answerChecker != null
                            ) {
                                if (answerChecker.isAnswerCorrect(segment.index, answer))
                                    config.correctColor
                                else
                                    config.wrongColor
                            } else {
                                Color.Black
                            }
                            pushStringAnnotation(BLANK_ANNOTATION_TAG, segment.index.toString())
                            pushStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold))
                            append(answer)
                            pop() // SpanStyle
                            pop() // annotation
                        }

                        else -> {
                            // Unanswered: show default placeholder with optional underline.
                            pushStringAnnotation(BLANK_ANNOTATION_TAG, segment.index.toString())
                            pushStyle(
                                SpanStyle(
                                    color = defaultTextColor,
                                    textDecoration = blankDecoration,
                                ),
                            )
                            append(segment.defaultPlaceholder)
                            pop() // SpanStyle
                            pop() // annotation
                        }
                    }
                    // Mirror original: append a space after each blank span.
                    append(" ")
                }
            }
        }
    }
}

/**
 * Builds the [InlineTextContent] map for blanks whose current answer is the deletion symbol.
 *
 * Must be called from a Composable context because it calls [painterResource].
 * Returns an empty map when no blank currently displays the deletion icon.
 */
@Composable
fun buildInlineContent(
    parsed: GapFillParsed,
    filledAnswers: Map<Int, String>,
): Map<String, InlineTextContent> {
    val deletionBlanks = parsed.segments
        .filterIsInstance<Segment.BlankSegment>()
        .filter { filledAnswers[it.index] == DELETION_SYMBOL }

    if (deletionBlanks.isEmpty()) return emptyMap()

    val painter = painterResource(id = R.drawable.settings_delete)
    return deletionBlanks.associate { segment ->
        "$DELETION_KEY_PREFIX${segment.index}" to InlineTextContent(
            placeholder = Placeholder(
                width = 20.sp,
                height = 20.sp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
            ),
        ) {
            Image(painter = painter, contentDescription = "Delete")
        }
    }
}

/**
 * Returns an ordered list of answers for indices 0 until [totalBlanks].
 *
 * The deletion symbol is translated to an empty string, matching the original
 * `getAllAnswers()` behaviour.
 */
fun orderedAnswers(filledAnswers: Map<Int, String>, totalBlanks: Int): List<String> =
    (0 until totalBlanks).map { i ->
        val answer = filledAnswers[i]
        if (answer == DELETION_SYMBOL) "" else answer ?: ""
    }

// ── Spanned → AnnotatedString ─────────────────────────────────────────────────

/**
 * Converts a [Spanned] (as returned by [HtmlCompat.fromHtml]) to an [AnnotatedString],
 * mapping [StyleSpan] and [ForegroundColorSpan] to their Compose equivalents.
 */
private fun spannedToAnnotatedString(spanned: Spanned): AnnotatedString =
    buildAnnotatedString {
        append(spanned.toString())
        for (span in spanned.getSpans(0, spanned.length, Any::class.java)) {
            val start = spanned.getSpanStart(span)
            val end = spanned.getSpanEnd(span)
            when (span) {
                is StyleSpan -> when (span.style) {
                    Typeface.BOLD ->
                        addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)

                    Typeface.ITALIC ->
                        addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)

                    Typeface.BOLD_ITALIC ->
                        addStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic,
                            ),
                            start,
                            end,
                        )
                }

                is ForegroundColorSpan ->
                    addStyle(SpanStyle(color = Color(span.foregroundColor)), start, end)
            }
        }
    }
