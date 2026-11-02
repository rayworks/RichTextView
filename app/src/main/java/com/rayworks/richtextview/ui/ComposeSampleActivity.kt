package com.rayworks.richtextview.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rayworks.library.compose.GapFillConfig
import com.rayworks.library.compose.GapFillState
import com.rayworks.library.compose.GapFillText
import com.rayworks.library.compose.rememberGapFillState
import com.rayworks.library.listener.AnswerCorrectionChecker
import com.rayworks.library.listener.AnswerResultObserver
import com.rayworks.library.text.InputStyle
import com.rayworks.richtextview.R

/**
 * Sample screen for the Compose [GapFillText], showing the same two exercises as the
 * legacy [MainActivity] (free-form editor + multiple-choice popup) plus a live log of the
 * [AnswerResultObserver] callbacks.
 */
class ComposeSampleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
                    GapFillSampleScreen()
                }
            }
        }
    }
}

private const val HOMETOWN_TEXT = "**My hometown**\n\nBy Kelly Scott\n \nMy {} is Edinburgh, in Scotland. " +
    "It's a really {} city. It's {} the east coast of the country, on the North" +
    " Sea. It's not {} the mountains, but there are mountains nearby. It's around 400 {}" +
    " from London to Edinburgh. By car, that's {} seven hours. Come in the summer; in " +
    "winter, it's really cold!"

private const val DIALOGUE_TEXT = "Jenny: Hey, this is my desk.\nJohn: Hi, I'm John from IT. Are you " +
    "Jenny?\nJenny: Oh! I'm sorry. Yes, I'm Jenny. {How's my mouse?}\nJohn: You need a " +
    "new monitor. {What's your first name?}\nJenny: It's M87.\nJohn: {How's your phone " +
    "number?}\nJenny: It's 6094 5537.\nJohn: 6094 5537... Ok. {How are you?}\nJenny: {" +
    "Nice to meet you.}\nJohn: {Good bye.} \n" +
    "<b> bold </b>" +
    "<font color= #3ec3d5> highlighted </font>" +
    "<i> italic </i>"

/** Answer key for [DIALOGUE_TEXT]; "" is the deletion option. */
private val DIALOGUE_ANSWERS = listOf(
    "How's my monitor?",
    "What's your employee number?",
    "What's your phone number?",
    "",
    "Nice to meet you too.",
    "See you later.",
)

private class AnswerKeyChecker(private val key: List<String>) : AnswerCorrectionChecker {
    override fun isAnswerCorrect(questionIndex: Int, answer: String?): Boolean =
        key.getOrNull(questionIndex) == normalize(answer)

    override fun allAnswerCorrect(resultList: List<String>): Boolean =
        retrieveCorrectAnswers(resultList) == key.size

    override fun retrieveCorrectAnswers(resultList: List<String>): Int =
        resultList.withIndex().count { (i, a) -> isAnswerCorrect(i, a) }

    // The component reports the deletion option as "-" per blank and "" in result lists.
    private fun normalize(answer: String?) = if (answer == "-") "" else answer.orEmpty()
}

@Composable
private fun GapFillSampleScreen() {
    val log = remember { mutableStateListOf<String>() }
    var reviewMode by remember { mutableStateOf(false) }

    val hometownState = rememberGapFillState()
    val dialogueState = rememberGapFillState()

    val options = listOf(
        R.array.op1, R.array.op2, R.array.op3, R.array.op4, R.array.op5, R.array.op6,
    ).map { stringArrayResource(it).toList() }

    val checker = remember { AnswerKeyChecker(DIALOGUE_ANSWERS) }
    val observer = remember {
        object : AnswerResultObserver {
            override fun onHandleAllAnswerCorrect(allCorrect: Boolean) {
                log += "onHandleAllAnswerCorrect(allCorrect=$allCorrect)"
            }

            override fun onAnswerCorrectForFirstTime(questionIndex: Int) {
                log += "onAnswerCorrectForFirstTime(#$questionIndex)"
            }

            override fun onAnswerSelected(correctNum: Int, finishedNum: Int, total: Int) {
                log += "onAnswerSelected(correct=$correctNum, finished=$finishedNum/$total)"
            }

            override fun onIdleState() {
                log += "onIdleState()"
            }
        }
    }

    val textStyle = TextStyle(
        color = Color.Black,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontFamily = FontFamily.SansSerif,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Review mode")
            Spacer(Modifier.width(8.dp))
            Switch(checked = reviewMode, onCheckedChange = { reviewMode = it })
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = {
                hometownState.reset()
                dialogueState.reset()
                log.clear()
            }) { Text("Reset") }
        }

        Text("Free-form editor (EDITOR_TEXT)", style = MaterialTheme.typography.titleSmall)
        GapFillText(
            config = GapFillConfig(
                rawText = HOMETOWN_TEXT,
                inputStyle = InputStyle.EDITOR_TEXT,
                wrongColor = colorResource(R.color.gapfill_wrong),
                reviewMode = reviewMode,
                wordCountMaxLimit = 3,
                editorHint = "Type your answer (max 3 words)",
                editorWarningMsg = "Too many words!",
            ),
            state = hometownState,
            textStyle = textStyle,
            modifier = Modifier.fillMaxWidth(),
        )
        FilledText(hometownState, HOMETOWN_TEXT)

        Text("Multiple choice (POPUP_WINDOW)", style = MaterialTheme.typography.titleSmall)
        GapFillText(
            config = GapFillConfig(
                rawText = DIALOGUE_TEXT,
                optionItems = options,
                inputStyle = InputStyle.POPUP_WINDOW,
                correctColor = colorResource(R.color.gapfill_correct),
                wrongColor = colorResource(R.color.gapfill_wrong),
                reviewMode = reviewMode,
                idleTimeoutMs = 15_000L,
            ),
            state = dialogueState,
            answerChecker = checker,
            resultObserver = observer,
            textStyle = textStyle,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Observer callbacks", style = MaterialTheme.typography.titleSmall)
        Text(
            text = if (log.isEmpty()) "(none yet)" else log.takeLast(8).joinToString("\n"),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun FilledText(state: GapFillState, rawText: String) {
    val full = runCatching { state.formattedFullText(rawText) }.getOrNull()
    Text(
        text = full?.let { "formattedFullText: $it" }
            ?: "Filled ${state.filledAnswers.size} blank(s) so far",
        fontSize = 12.sp,
        color = Color.Gray,
    )
}
