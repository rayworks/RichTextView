package com.rayworks.library.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.rayworks.library.listener.AnswerCorrectionChecker
import com.rayworks.library.listener.AnswerResultObserver
import com.rayworks.library.text.InputStyle
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Default label of the deletion option (matches `OptionAdapter.deletionMsg`). */
const val DEFAULT_DELETION_LABEL = "delete this word"

/**
 * Immutable configuration for [GapFillText].
 *
 * @param rawText           gap-fill source string; use `{}` for a blank with no default
 *                          placeholder, or `{placeholder}` to show hint text until answered
 *                          (error-correction style)
 * @param optionItems       per-blank option lists, required when
 *                          [inputStyle] is [InputStyle.POPUP_WINDOW];
 *                          an empty string inside a list represents the deletion option
 * @param inputStyle        [InputStyle.POPUP_WINDOW] (multiple-choice) or
 *                          [InputStyle.EDITOR_TEXT] (free-form typing)
 * @param correctColor      foreground colour applied to correctly answered blanks
 * @param wrongColor        foreground colour applied to incorrectly answered blanks
 * @param reviewMode        when `true` blanks are rendered read-only (not tappable)
 * @param wordCountMaxLimit maximum word count for [InputStyle.EDITOR_TEXT] mode;
 *                          the editor background turns orange when exceeded
 * @param editorHint        hint text shown in the editor text field
 * @param editorWarningMsg  message shown when the word count limit is exceeded
 * @param editorConfirmText label of the editor confirm button (default `"OK"`)
 * @param deletionLabel     text shown next to the trash icon for the deletion option
 * @param idleTimeoutMs     milliseconds of user inactivity before
 *                          [AnswerResultObserver.onIdleState] is fired
 *                          (only active when [GapFillParsed.errorCorrectionEnabled] is true)
 */
data class GapFillConfig(
    val rawText: String,
    val optionItems: List<List<String>> = emptyList(),
    val inputStyle: InputStyle = InputStyle.POPUP_WINDOW,
    val correctColor: Color = Color.Green,
    val wrongColor: Color = Color.Red,
    val reviewMode: Boolean = false,
    val wordCountMaxLimit: Int = 128,
    val editorHint: String? = null,
    val editorWarningMsg: String? = null,
    val editorConfirmText: String = "OK",
    val deletionLabel: String = DEFAULT_DELETION_LABEL,
    val idleTimeoutMs: Long = 20_000L,
) {
    init {
        require(rawText.isNotEmpty()) { "rawText cannot be empty" }
        if (inputStyle == InputStyle.POPUP_WINDOW) {
            require(optionItems.isNotEmpty()) {
                "optionItems cannot be empty for POPUP_WINDOW style"
            }
        }
    }
}

/**
 * Compose reimplementation of `GapFillTextView`.
 *
 * Renders a gap-fill text where each blank is an interactive tap target. Depending on
 * [GapFillConfig.inputStyle] a popup option picker ([GapFillOptionPopup]) anchored to the tapped
 * blank, or a free-form editor dialog ([GapFillEditorDialog]) appears when the user taps a blank.
 *
 * The container mirrors `writing_text_frame_bkg` (white fill, light-grey 1dp stroke,
 * 8dp corner radius).
 *
 * **State hoisting**: pass your own [GapFillState] to observe or drive the component from
 * outside; otherwise [rememberGapFillState] creates a locally remembered instance.
 *
 * **Callbacks**: [answerChecker] and [resultObserver] are optional. If provided they are called
 * after every answer selection — matching the original observer pattern.
 *
 * @param config          immutable display and behaviour configuration
 * @param modifier        applied to the outer container
 * @param state           mutable runtime state; use [rememberGapFillState] when not hoisting
 * @param answerChecker   optional checker used to colour answered blanks and determine correctness
 * @param resultObserver  optional observer for answer-selection lifecycle callbacks
 * @param textStyle       [TextStyle] passed to the underlying [BasicText]
 * @param contentPadding  padding between the frame and the text
 */
@Composable
fun GapFillText(
    config: GapFillConfig,
    modifier: Modifier = Modifier,
    state: GapFillState = rememberGapFillState(),
    answerChecker: AnswerCorrectionChecker? = null,
    resultObserver: AnswerResultObserver? = null,
    textStyle: TextStyle = TextStyle.Default,
    contentPadding: PaddingValues = PaddingValues(8.dp),
) {
    // Parse the raw text once per rawText change; result is structurally stable.
    val parsed = remember(config.rawText) { parseRawText(config.rawText) }

    if (config.inputStyle == InputStyle.POPUP_WINDOW) {
        require(parsed.totalBlanks <= config.optionItems.size) {
            "${parsed.totalBlanks} blanks to be filled but ${config.optionItems.size} answer option items found."
        }
    }

    // Keep the latest observer / checker references available inside lambdas without
    // making them LaunchedEffect / pointerInput restart keys.
    val currentChecker by rememberUpdatedState(answerChecker)
    val currentObserver by rememberUpdatedState(resultObserver)

    // interactionKey increments on every tap; restarting the idle timer below.
    var interactionKey by remember { mutableIntStateOf(0) }

    // ── Idle-state detection ──────────────────────────────────────────────────
    // Equivalent to handler.removeCallbacks + postDelayed in the original: every interaction
    // restarts the effect; while blanks remain unfilled, onIdleState() fires once per timeout.
    LaunchedEffect(interactionKey, parsed, config.idleTimeoutMs, config.reviewMode) {
        if (!parsed.errorCorrectionEnabled || config.reviewMode) return@LaunchedEffect
        while (true) {
            delay(config.idleTimeoutMs)
            if (state.filledAnswers.size >= parsed.totalBlanks) break
            currentObserver?.onIdleState()
        }
    }

    // ── AnnotatedString ───────────────────────────────────────────────────────
    val defaultTextColor = textStyle.color.takeOrElse { Color.Black }
    val annotatedText = remember(
        parsed,
        state.filledAnswers,
        config,
        defaultTextColor,
        answerChecker,
    ) {
        buildGapFillAnnotatedString(
            parsed = parsed,
            filledAnswers = state.filledAnswers,
            config = config,
            defaultTextColor = defaultTextColor,
            answerChecker = answerChecker,
        )
    }

    // InlineTextContent for deletion-icon blanks — rebuilt when filledAnswers changes.
    val inlineContent = buildInlineContent(parsed, state.filledAnswers)

    // Layout result used to map tap-offset → character-offset and to anchor the popup.
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    // Keep the latest annotatedText visible inside the gesture lambda without restarting
    // the gesture detector on every recomposition.
    val currentAnnotatedText by rememberUpdatedState(annotatedText)

    // ── Answer population ─────────────────────────────────────────────────────
    fun populateAnswer(blankIndex: Int, answer: String) {
        val wasFirstSelection = !state.filledAnswers.containsKey(blankIndex)
        state.filledAnswers = state.filledAnswers + (blankIndex to answer)

        val checker = currentChecker
        val observer = currentObserver ?: return
        val answers = orderedAnswers(state.filledAnswers, parsed.totalBlanks)

        if (config.inputStyle == InputStyle.POPUP_WINDOW && checker != null &&
            wasFirstSelection && checker.isAnswerCorrect(blankIndex, answer)
        ) {
            observer.onAnswerCorrectForFirstTime(blankIndex)
        }

        val correctNum = checker?.retrieveCorrectAnswers(answers) ?: 0
        observer.onAnswerSelected(correctNum, state.filledAnswers.size, parsed.totalBlanks)

        if (state.filledAnswers.size == parsed.totalBlanks && checker != null) {
            observer.onHandleAllAnswerCorrect(checker.allAnswerCorrect(answers))
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    val frameShape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .background(Color.White, frameShape)
            .border(1.dp, Color(0xFFE7E7E7), frameShape)
            .padding(contentPadding),
    ) {
        BasicText(
            text = annotatedText,
            style = textStyle,
            onTextLayout = { layoutResult = it },
            inlineContent = inlineContent,
            modifier = Modifier.pointerInput(config.reviewMode) {
                detectTapGestures { tapOffset ->
                    interactionKey++
                    if (config.reviewMode) return@detectTapGestures
                    val layout = layoutResult ?: return@detectTapGestures
                    val charOffset = layout.getOffsetForPosition(tapOffset)
                    currentAnnotatedText
                        .getStringAnnotations(BLANK_ANNOTATION_TAG, charOffset, charOffset)
                        .firstOrNull()
                        ?.let { state.activeBlankIndex = it.item.toInt() }
                }
            },
        )

        // ── Input overlay ─────────────────────────────────────────────────────
        val activeIndex = state.activeBlankIndex ?: return@Box
        when (config.inputStyle) {
            InputStyle.POPUP_WINDOW -> {
                // Anchor the dropdown to the tapped blank so it opens right below it
                // (or above it when there is not enough room), like the ListPopupWindow did.
                val anchor = layoutResult?.let { blankBounds(it, annotatedText, activeIndex) }
                    ?: Rect.Zero
                val density = LocalDensity.current
                Box(
                    modifier = Modifier
                        .offset { IntOffset(anchor.left.roundToInt(), anchor.top.roundToInt()) }
                        .size(
                            width = with(density) { anchor.width.toDp() },
                            height = with(density) { anchor.height.toDp() },
                        ),
                ) {
                    GapFillOptionPopup(
                        expanded = true,
                        options = config.optionItems.getOrElse(activeIndex) { emptyList() },
                        deletionLabel = config.deletionLabel,
                        onOptionChosen = { answer ->
                            state.activeBlankIndex = null
                            populateAnswer(activeIndex, answer)
                        },
                        onDismiss = { state.activeBlankIndex = null },
                    )
                }
            }

            InputStyle.EDITOR_TEXT -> {
                GapFillEditorDialog(
                    hint = config.editorHint,
                    warningMsg = config.editorWarningMsg,
                    confirmText = config.editorConfirmText,
                    wordCountMaxLimit = config.wordCountMaxLimit,
                    onConfirm = { answer -> populateAnswer(activeIndex, answer) },
                    onDismiss = { state.activeBlankIndex = null },
                )
            }
        }
    }
}

/**
 * Returns the bounds (in text-layout coordinates) of the blank at [blankIndex].
 * For a blank that wraps across lines the rect spans from its first to its last line.
 */
private fun blankBounds(
    layout: TextLayoutResult,
    text: AnnotatedString,
    blankIndex: Int,
): Rect? {
    val range = text.getStringAnnotations(BLANK_ANNOTATION_TAG, 0, text.length)
        .firstOrNull { it.item == blankIndex.toString() }
        ?: return null
    if (range.end <= range.start) return null

    val firstLine = layout.getLineForOffset(range.start)
    val lastLine = layout.getLineForOffset(range.end - 1)
    val startBox = layout.getBoundingBox(range.start)
    val right = if (firstLine == lastLine) {
        layout.getBoundingBox(range.end - 1).right
    } else {
        layout.getLineRight(firstLine)
    }
    return Rect(
        left = startBox.left,
        top = layout.getLineTop(firstLine),
        right = maxOf(right, startBox.right),
        bottom = layout.getLineBottom(lastLine),
    )
}
