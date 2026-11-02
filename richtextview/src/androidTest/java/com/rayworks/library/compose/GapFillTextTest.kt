package com.rayworks.library.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rayworks.library.listener.AnswerCorrectionChecker
import com.rayworks.library.listener.AnswerResultObserver
import com.rayworks.library.text.InputStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GapFillTextTest {

    @get:Rule
    val rule = createComposeRule()

    // ── Fixtures ──────────────────────────────────────────────────────────────

    /** Error-correction style text: each blank shows a default placeholder until answered. */
    private val dialogueText = "Jenny: Hi. {How's my mouse?}\nJohn: {Good bye.} The end."

    private val dialogueOptions = listOf(
        listOf("How's my monitor?", "How's my keyboard?"),
        listOf("", "See you later."), // "" = deletion option
    )

    /** Answer key; "" means the blank should be deleted. */
    private val dialogueKey = listOf("How's my monitor?", "")

    private val correctColor = Color(0xFF8ACA01)
    private val wrongColor = Color(0xFFFF6000)

    private class KeyChecker(private val key: List<String>) : AnswerCorrectionChecker {
        override fun isAnswerCorrect(questionIndex: Int, answer: String?): Boolean =
            key.getOrNull(questionIndex) == if (answer == DELETION_SYMBOL) "" else answer

        override fun allAnswerCorrect(resultList: List<String?>): Boolean =
            retrieveCorrectAnswers(resultList) == key.size

        override fun retrieveCorrectAnswers(resultList: List<String?>): Int =
            resultList.withIndex().count { (i, a) -> isAnswerCorrect(i, a) }
    }

    private class RecordingObserver : AnswerResultObserver {
        val events = mutableListOf<String>()

        override fun onHandleAllAnswerCorrect(allCorrect: Boolean) {
            events += "allCorrect($allCorrect)"
        }

        override fun onAnswerCorrectForFirstTime(questionIndex: Int) {
            events += "firstCorrect($questionIndex)"
        }

        override fun onAnswerSelected(correctNum: Int, finishedNum: Int, total: Int) {
            events += "selected($correctNum,$finishedNum,$total)"
        }

        override fun onIdleState() {
            events += "idle"
        }
    }

    /** Sets content directly, or through [tester] to emulate save/restore. */
    private fun setContent(tester: StateRestorationTester?, content: @Composable () -> Unit) {
        if (tester != null) tester.setContent(content) else rule.setContent(content)
    }

    /**
     * @param state hoisted state; `null` uses the default [rememberGapFillState]
     *              (which is what gets saved and restored).
     */
    private fun setPopupContent(
        state: GapFillState?,
        observer: RecordingObserver = RecordingObserver(),
        reviewMode: Boolean = false,
        idleTimeoutMs: Long = 20_000L,
        tester: StateRestorationTester? = null,
    ) {
        setContent(tester) {
            GapFillText(
                config = GapFillConfig(
                    rawText = dialogueText,
                    optionItems = dialogueOptions,
                    inputStyle = InputStyle.POPUP_WINDOW,
                    correctColor = correctColor,
                    wrongColor = wrongColor,
                    reviewMode = reviewMode,
                    idleTimeoutMs = idleTimeoutMs,
                ),
                state = state ?: rememberGapFillState(),
                answerChecker = KeyChecker(dialogueKey),
                resultObserver = observer,
                modifier = Modifier.testTag(TAG),
            )
        }
    }

    private fun setEditorContent(
        state: GapFillState?,
        wordLimit: Int = 128,
        tester: StateRestorationTester? = null,
    ) {
        setContent(tester) {
            GapFillText(
                config = GapFillConfig(
                    rawText = "**My hometown**\nMy {} is Edinburgh. It's a really {} city.",
                    inputStyle = InputStyle.EDITOR_TEXT,
                    wordCountMaxLimit = wordLimit,
                    editorHint = "Type here",
                    editorWarningMsg = "Too many words",
                    editorConfirmText = "OK",
                ),
                state = state ?: rememberGapFillState(),
                modifier = Modifier.testTag(TAG),
            )
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** The gap-fill text node (excludes popup / dialog windows). */
    private fun gapFillTextNode(containing: String): SemanticsNodeInteraction =
        rule.onNode(
            hasText(containing, substring = true) and hasAnyAncestor(hasTestTag(TAG)),
            useUnmergedTree = true,
        )

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)
            ?.action?.invoke(results)
        return results.single()
    }

    /** Taps the middle of the [occurrence]-th appearance of [blankText] in the gap-fill text. */
    private fun clickBlank(blankText: String, occurrence: Int = 0) {
        val node = gapFillTextNode(blankText)
        val layout = node.textLayout()
        val text = layout.layoutInput.text.text
        var index = -1
        repeat(occurrence + 1) { index = text.indexOf(blankText, index + 1) }
        check(index >= 0) { "'$blankText' #$occurrence not found in: $text" }
        val center = layout.getBoundingBox(index + blankText.length / 2).center
        node.performTouchInput { click(center) }
        rule.waitForIdle()
    }

    /** Foreground colour applied to the first character of [substring] in the gap-fill text. */
    private fun colorOf(substring: String): Color? {
        val annotated = gapFillTextNode(substring).textLayout().layoutInput.text
        val start = annotated.text.indexOf(substring)
        return annotated.spanStyles
            .lastOrNull { it.start <= start && start < it.end && it.item.color.isSpecified }
            ?.item?.color
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Test
    fun rendersMarkupAndEmptyBlankPlaceholders() {
        setEditorContent(rememberlessState())

        gapFillTextNode("My hometown").assertIsDisplayed()
        // "**…**" markup is consumed and "{}" blanks render as the underline placeholder.
        val text = gapFillTextNode("My ____").textLayout().layoutInput.text.text
        assertTrue(text, text.startsWith("My hometown\nMy ____ "))
        assertTrue(text, !text.contains("**") && !text.contains("{"))
    }

    @Test
    fun rendersDefaultPlaceholdersForErrorCorrectionBlanks() {
        setPopupContent(rememberlessState())

        val text = gapFillTextNode("How's my mouse?").textLayout().layoutInput.text.text
        // Like the legacy view, each text segment goes through Html.fromHtml, which drops its
        // leading whitespace; the blank itself is followed by a single space.
        assertEquals("Jenny: Hi. How's my mouse? \nJohn: Good bye. The end.", text)
    }

    // ── Popup (multiple choice) ───────────────────────────────────────────────

    @Test
    fun tappingBlank_showsItsOptions() {
        setPopupContent(rememberlessState())

        clickBlank("How's my mouse?")

        rule.onNodeWithText("How's my monitor?").assertIsDisplayed()
        rule.onNodeWithText("How's my keyboard?").assertIsDisplayed()
        // Options of the other blank are not shown.
        rule.onNodeWithText("See you later.").assertDoesNotExist()
    }

    @Test
    fun selectingCorrectOption_fillsBlankInCorrectColor_andNotifiesObserver() {
        val state = GapFillState()
        val observer = RecordingObserver()
        setPopupContent(state, observer)

        clickBlank("How's my mouse?")
        rule.onNodeWithText("How's my monitor?").performClick()
        rule.waitForIdle()

        assertEquals(mapOf(0 to "How's my monitor?"), state.filledAnswers)
        assertEquals(null, state.activeBlankIndex)
        rule.onNodeWithText("How's my keyboard?").assertDoesNotExist() // popup closed
        assertEquals(correctColor, colorOf("How's my monitor?"))
        assertEquals(listOf("firstCorrect(0)", "selected(1,1,2)"), observer.events)
    }

    @Test
    fun selectingWrongOption_usesWrongColor_andCorrectingLaterIsNotFirstTime() {
        val state = GapFillState()
        val observer = RecordingObserver()
        setPopupContent(state, observer)

        clickBlank("How's my mouse?")
        rule.onNodeWithText("How's my keyboard?").performClick()
        rule.waitForIdle()
        assertEquals(wrongColor, colorOf("How's my keyboard?"))

        clickBlank("How's my keyboard?")
        rule.onNodeWithText("How's my monitor?").performClick()
        rule.waitForIdle()
        assertEquals(correctColor, colorOf("How's my monitor?"))

        // onAnswerCorrectForFirstTime only fires on a blank's first selection.
        assertEquals(listOf("selected(0,1,2)", "selected(1,1,2)"), observer.events)
    }

    @Test
    fun deletionOption_storesDeletionSymbol_andRendersIcon() {
        val state = GapFillState()
        setPopupContent(state)

        clickBlank("Good bye.")
        rule.onNodeWithText(DEFAULT_DELETION_LABEL).performClick()
        rule.waitForIdle()

        assertEquals(mapOf(1 to DELETION_SYMBOL), state.filledAnswers)
        val text = gapFillTextNode("John:").textLayout().layoutInput.text.text
        assertTrue(text, !text.contains("Good bye."))
        // The deletion icon is an inline-content placeholder in place of the answer.
        assertTrue(text, text.contains("John: [del] The end."))
    }

    @Test
    fun fillingAllBlanks_reportsAllCorrect_andBuildsFullText() {
        val state = GapFillState()
        val observer = RecordingObserver()
        setPopupContent(state, observer)

        clickBlank("How's my mouse?")
        rule.onNodeWithText("How's my monitor?").performClick()
        rule.waitForIdle()
        clickBlank("Good bye.")
        rule.onNodeWithText(DEFAULT_DELETION_LABEL).performClick()
        rule.waitForIdle()

        assertEquals(
            listOf(
                "firstCorrect(0)", "selected(1,1,2)",
                "firstCorrect(1)", "selected(2,2,2)", "allCorrect(true)",
            ),
            observer.events,
        )
        assertEquals(
            "Jenny: Hi. {How's my monitor?}\nJohn: {-} The end.",
            state.formattedFullText(dialogueText),
        )
        assertEquals(listOf("How's my monitor?", ""), orderedAnswers(state.filledAnswers, 2))
    }

    @Test
    fun unansweredBlanks_arePassedToCheckerAsNull_notAsDeletion() {
        // Blank #1's correct answer is the deletion option (""); while it is still unanswered
        // it must not count as correct.
        val observer = RecordingObserver()
        setPopupContent(GapFillState(), observer)

        clickBlank("How's my mouse?")
        rule.onNodeWithText("How's my monitor?").performClick()
        rule.waitForIdle()

        assertEquals(listOf("firstCorrect(0)", "selected(1,1,2)"), observer.events)
        assertEquals(listOf("x", null), orderedAnswers(mapOf(0 to "x"), 2))
    }

    @Test
    fun reviewMode_ignoresTaps() {
        val state = GapFillState()
        setPopupContent(state, reviewMode = true)

        clickBlank("How's my mouse?")

        assertEquals(null, state.activeBlankIndex)
        rule.onNodeWithText("How's my monitor?").assertDoesNotExist()
    }

    @Test
    fun reset_clearsAnswers() {
        val state = GapFillState()
        setPopupContent(state)
        clickBlank("How's my mouse?")
        rule.onNodeWithText("How's my monitor?").performClick()
        rule.waitForIdle()

        rule.runOnIdle { state.reset() }

        assertEquals(emptyMap<Int, String>(), state.filledAnswers)
        gapFillTextNode("How's my mouse?").assertExists()
    }

    // ── Idle detection ────────────────────────────────────────────────────────

    @Test
    fun idleCallback_firesEveryTimeoutWhileBlanksRemainUnfilled() {
        val observer = RecordingObserver()
        rule.mainClock.autoAdvance = false
        setPopupContent(GapFillState(), observer, idleTimeoutMs = 1_000L)

        rule.mainClock.advanceTimeBy(900L)
        assertEquals(emptyList<String>(), observer.events)

        rule.mainClock.advanceTimeBy(200L)
        assertEquals(listOf("idle"), observer.events)

        rule.mainClock.advanceTimeBy(1_000L)
        assertEquals(listOf("idle", "idle"), observer.events)
    }

    // ── Editor (free form) ────────────────────────────────────────────────────

    @Test
    fun editor_confirmFillsTappedBlank() {
        val state = GapFillState()
        setEditorContent(state)

        clickBlank("____", occurrence = 1)
        rule.onNode(hasSetTextAction()).performTextInput("  lovely  ")
        rule.onNodeWithText("OK").performClick()
        rule.waitForIdle()

        assertEquals(mapOf(1 to "lovely"), state.filledAnswers) // trimmed
        assertEquals(null, state.activeBlankIndex)
        rule.onNode(hasSetTextAction()).assertDoesNotExist() // dialog closed
        gapFillTextNode("really lovely city").assertExists()
    }

    @Test
    fun editor_blankInputDoesNotFill() {
        val state = GapFillState()
        setEditorContent(state)

        clickBlank("____")
        rule.onNodeWithText("OK").performClick()
        rule.waitForIdle()

        assertEquals(emptyMap<Int, String>(), state.filledAnswers)
        rule.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    @Test
    fun editor_overWordLimit_showsWarningAndHidesConfirm() {
        setEditorContent(GapFillState(), wordLimit = 2)

        clickBlank("____")
        rule.onNode(hasSetTextAction()).performTextInput("one two")
        rule.onNodeWithText("OK").assertIsDisplayed()
        rule.onNodeWithText("Too many words").assertDoesNotExist()

        rule.onNode(hasSetTextAction()).performTextInput(" three")
        rule.onNodeWithText("Too many words").assertIsDisplayed()
        rule.onNodeWithText("OK").assertDoesNotExist()
    }

    // ── State restoration (rotation / process death) ─────────────────────────

    @Test
    fun answersAndOpenPopup_surviveSavedStateRestore() {
        val tester = StateRestorationTester(rule)
        setPopupContent(state = null, tester = tester)

        clickBlank("How's my mouse?")
        rule.onNodeWithText("How's my monitor?").performClick()
        rule.waitForIdle()
        clickBlank("Good bye.") // leave blank #1's popup open

        tester.emulateSavedInstanceStateRestore()

        gapFillTextNode("How's my monitor?").assertExists()
        assertEquals(correctColor, colorOf("How's my monitor?"))
        rule.onNodeWithText(DEFAULT_DELETION_LABEL).assertIsDisplayed()
        rule.onNodeWithText("See you later.").assertIsDisplayed()
    }

    @Test
    fun editorDraft_survivesSavedStateRestore() {
        val tester = StateRestorationTester(rule)
        setEditorContent(state = null, tester = tester)

        clickBlank("____", occurrence = 1)
        rule.onNode(hasSetTextAction()).performTextInput("lovely")

        tester.emulateSavedInstanceStateRestore()

        rule.onNode(hasSetTextAction()).assertTextEquals("lovely")
        rule.onNodeWithText("OK").performClick()
        rule.waitForIdle()
        gapFillTextNode("really lovely city").assertExists()
    }

    // ── Configuration validation ──────────────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun popupConfig_requiresOptions() {
        GapFillConfig(rawText = dialogueText, inputStyle = InputStyle.POPUP_WINDOW)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parse_requiresAtLeastOneBlank() {
        parseRawText("No blanks here.")
    }

    private fun rememberlessState() = GapFillState()

    private companion object {
        const val TAG = "gapfill"
    }
}
