package com.rayworks.library.compose

import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.rayworks.library.util.Utils

/** Background colour in the normal state — matches `rectangle_gapfill_form_bkg.xml`. */
private val EditorNormalColor = Color(0xFF0078FF)

/** Background colour when the word limit is exceeded — `rectangle_gapfill_form_bkg_red.xml`. */
private val EditorWarningColor = Color(0xFFFF6000)

/**
 * Free-form text editor dialog used in [com.rayworks.library.text.InputStyle.EDITOR_TEXT] mode.
 *
 * Layout and behaviour mirror the original `view_dialog_gapfill_form.xml` + `AlertDialog`:
 * - A blue frame with a white 100dp-high text box and a transparent, white-text confirm button.
 * - The frame turns orange-red, the confirm button is hidden and [warningMsg] is shown while
 *   the word count exceeds [wordCountMaxLimit].
 * - No dim scrim behind the dialog (mirrors `clearFlags(FLAG_DIM_BEHIND)`).
 * - The soft keyboard is hidden when the dialog is dismissed.
 *
 * @param hint              optional placeholder hint shown inside the text field
 * @param warningMsg        message displayed when the word count limit is exceeded
 * @param confirmText       label of the confirm button
 * @param wordCountMaxLimit maximum allowed word count (inclusive)
 * @param onConfirm         invoked with the trimmed answer text when the user confirms;
 *                          only called when the text field is non-blank
 * @param onDismiss         invoked when the dialog is dismissed (back-press, outside tap, or
 *                          after [onConfirm])
 */
@Composable
fun GapFillEditorDialog(
    hint: String?,
    warningMsg: String?,
    confirmText: String,
    wordCountMaxLimit: Int,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val isOverLimit = remember(text, wordCountMaxLimit) {
        Utils.countWordsFromInputText(text) > wordCountMaxLimit
    }

    val backgroundColor by animateColorAsState(
        targetValue = if (isOverLimit) EditorWarningColor else EditorNormalColor,
        label = "editorBackground",
    )

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Remove the dim scrim behind the dialog — mirrors FLAG_DIM_BEHIND removal.
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { dialogWindow?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND) }

        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        // Ensure the keyboard is hidden when the dialog leaves composition.
        DisposableEffect(Unit) {
            onDispose { keyboardController?.hide() }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(backgroundColor, RoundedCornerShape(3.dp))
                .padding(10.dp),
        ) {
            val fieldTextStyle = TextStyle(color = Color.Black, fontSize = 14.sp)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = fieldTextStyle,
                cursorBrush = SolidColor(EditorNormalColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .focusRequester(focusRequester),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .background(Color.White)
                            .padding(5.dp),
                    ) {
                        if (text.isEmpty() && !hint.isNullOrEmpty()) {
                            Text(hint, style = fieldTextStyle.copy(color = Color.DarkGray))
                        }
                        innerTextField()
                    }
                },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isOverLimit) {
                    Text(
                        text = warningMsg.orEmpty(),
                        color = Color.White,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(5.dp),
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            val trimmed = text.trim()
                            if (trimmed.isNotEmpty()) onConfirm(trimmed)
                            onDismiss()
                        },
                    ) {
                        Text(confirmText, color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
