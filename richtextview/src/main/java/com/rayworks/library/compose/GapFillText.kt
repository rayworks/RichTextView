package com.rayworks.library.compose

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContentColor
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.rayworks.library.listener.AnswerCorrectionChecker
import com.rayworks.library.listener.AnswerResultObserver
import com.rayworks.library.text.InputStyle
import kotlinx.coroutines.delay

/**
 * Immutable configuration for [GapFillText].
 *
 * @param rawText           gap-fill source string; use `{}` for a blank with no default
 *                          placeholder, or `{placeholder}` to show hint text until answered
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
 * [GapFillConfig.inputStyle] a popup option picker ([GapFillOptionPopup]) or a free-form editor
 * dialog ([GapFillEditorDialog]) appears when the user taps a blank.
 *
 * The outer Box carries the `writing_text_frame_bkg` border (white fill, light-grey 1dp stroke,
 * 8dp corner radius).
 *
 * **State hoisting**: pass your own [GapFillState] to observe or drive the component from
 * outside; otherwise [rememberGapFillState] creates a locally remembered instance.
 *
 * **Callbacks**: [answerChecker] and [resultObserver] are optional. If provided they are called
 * after every answer selection — matching the original observer pattern.
 *
 * @param config          immutable display and behaviour configuration
 * @param state           mutable runtime state; use [rememberGapFillState] when not hoisting
 * @param answerChecker   optional checker used to colour answered blanks and determine correctness
 * @param resultObserver  optional observer for answer-selection lifecycle callbacks
 * @param modifier        applied to the outer [Box]
 * @param textStyle       [TextStyle] passed to the underlying [BasicText]
 */
@Composable
fun GapFillText(
    config: GapFillConfig,
    state: GapFillState = rememberGapFillState(),
    answerChecker: AnswerCorrectionChecker? = null,
    resultObserver: AnswerResultObserver? = null,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle.Default,
) {
    // Parse the raw text once per rawText change; result is structurally stable.
    val parsed = remember(config.rawText) { parseRawText(config.rawText) }

    // Keep the latest observer / checker references available inside lambdas without
    // making them LaunchedEffect / pointerInput restart keys.
    val currentChecker by rememberUpdatedState(answerChecker)
    val currentObserver by rememberUpdatedState(resultObserver)

    // interactionKey increments on every blank tap; drives the idle-detection timer below.
    var interactionKey by remember { mutableIntStateOf(0) }

    // ── Idle-state detection ──────────────────────────────────────────────────
    // LaunchedEffect restarts whenever interactionKey changes, which cancels and reschedules
    // the delay — equivalent to handler.removeCallbacks + postDelayed in the original.
    LaunchedEffect(interactionKey) {
        if (parsed.errorCorrectionEnabled && interactionKey > 0) {
            delay(config.idleTimeoutMs)
            if (state.filledAnswers.size < parsed.totalBlanks) {
                currentObserver?.onIdleState()
            }
        }
    }

    // ── AnnotatedString ───────────────────────────────────────────────────────
    val defaultTextColor = LocalContentColor.current
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

    // Layout result used to map tap-offset → character-offset.
    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }

    // Keep the latest annotatedText visible inside the gesture lambda without restarting
    // the gesture detector on every recomposition.
    val currentAnnotatedText by rememberUpdatedState(annotatedText)

    // ── Answer population ─────────────────────────────────────────────────────
    // Declared as a local function so it closes over the stable `parsed` value.
    fun populateAnswer(blankIndex: Int, answer: String) {
        val wasFirstSelection = !state.filledAnswers.containsKey(blankIndex)
        state.filledAnswers = state.filledAnswers + (blankIndex to answer)

        val answers = orderedAnswers(state.filledAnswers, parsed.totalBlanks)

        if (config.inputStyle == InputStyle.POPUP_WINDOW && currentChecker != null) {
            val correct = currentChecker!!.isAnswerCorrect(blankIndex, answer)
            if (correct && wasFirstSelection) {
                currentObserver?.onAnswerCorrectForFirstTime(blankIndex)
            }
        }

        val correctNum = currentChecker?.retrieveCorrectAnswers(answers) ?: 0
        currentObserver?.onAnswerSelected(
            correctNum,
            state.filledAnswers.size,
            parsed.totalBlanks,
        )

        if (state.filledAnswers.size == parsed.totalBlanks && currentChecker != null) {
            val allCorrect = currentChecker!!.allAnswerCorrect(answers)
            currentObserver?.onHandleAllAnswerCorrect(allCorrect)
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    // Outer Box provides the writing_text_frame_bkg border and acts as the anchor for the popup.
    Box(
        modifier = modifier.border(1.dp, Color(0xFFE7E7E7), RoundedCornerShape(8.dp)),
    ) {
        BasicText(
            text = annotatedText,
            style = textStyle,
            onTextLayout = { layoutResult.value = it },
            inlineContent = inlineContent,
            modifier = Modifier.pointerInput(config.reviewMode) {
                if (!config.reviewMode) {
                    detectTapGestures { tapOffset ->
                        layoutResult.value?.let { layout ->
                            val charOffset = layout.getOffsetForPosition(tapOffset)
                            currentAnnotatedText
                                .getStringAnnotations(
                                    BLANK_ANNOTATION_TAG,
                                    charOffset,
                                    charOffset,
                                )
                                .firstOrNull()
                                ?.let { annotation ->
                                    state.activeBlankIndex = annotation.item.toInt()
                                    interactionKey++
                                }
                        }
                    }
                }
            },
        )

        // ── Input overlay ─────────────────────────────────────────────────────
        val activeIndex = state.activeBlankIndex
        if (activeIndex != null) {
            when (config.inputStyle) {
                InputStyle.POPUP_WINDOW -> {
                    val options = if (activeIndex < config.optionItems.size)
                        config.optionItems[activeIndex]
                    else
                        emptyList()

                    GapFillOptionPopup(
                        expanded = true,
                        options = options,
                        onOptionChosen = { answer ->
                            populateAnswer(activeIndex, answer)
                            state.activeBlankIndex = null
                        },
                        onDismiss = { state.activeBlankIndex = null },
                    )
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
}
