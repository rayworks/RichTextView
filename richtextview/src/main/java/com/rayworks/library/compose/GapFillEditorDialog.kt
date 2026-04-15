package com.rayworks.library.compose

import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.rayworks.library.util.Utils

/**
 * Background colour in the normal state — matches `rectangle_gapfill_form_bkg.xml` (`#0078FF`).
 */
private val EditorNormalColor = Color(0xFF0078FF)

/**
 * Background colour when the word limit is exceeded — matches
 * `rectangle_gapfill_form_bkg_red.xml` (`#FF6000`).
 */
private val EditorWarningColor = Color(0xFFFF6000)

/**
 * Free-form text editor dialog used in [com.rayworks.library.text.InputStyle.EDITOR_TEXT] mode.
 *
 * Behaviour mirrors the original `showEditorView()` / `AlertDialog`:
 * - The background transitions from blue to orange-red when [wordCountMaxLimit] is exceeded.
 * - The confirm button is hidden and a [warningMsg] is shown while over the limit.
 * - The soft keyboard is hidden when the dialog is dismissed.
 * - The window dim scrim is removed (mirrors `clearFlags(FLAG_DIM_BEHIND)`).
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
    val wordCount = remember(text) { Utils.countWordsFromInputText(text) }
    val isOverLimit = wordCount > wordCountMaxLimit

    val backgroundColor by animateColorAsState(
        targetValue = if (isOverLimit) EditorWarningColor else EditorNormalColor,
        label = "editorBackground",
    )

    val keyboardController = LocalSoftwareKeyboardController.current

    Dialog(
        onDismissRequest = {
            keyboardController?.hide()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Remove the dim scrim behind the dialog — mirrors FLAG_DIM_BEHIND removal.
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        LaunchedEffect(Unit) {
            dialogWindow?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }

        // Ensure the keyboard is hidden when this composable leaves composition.
        DisposableEffect(Unit) {
            onDispose { keyboardController?.hide() }
        }

        Surface(
            shape = RoundedCornerShape(3.dp),
            color = backgroundColor,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = hint?.let { hintText ->
                        { Text(hintText, color = Color.White.copy(alpha = 0.7f)) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                    ),
                    maxLines = 4,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isOverLimit) {
                        Text(
                            text = warningMsg ?: "",
                            color = Color.White,
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = {
                                val trimmed = text.trim()
                                if (trimmed.isNotEmpty()) {
                                    onConfirm(trimmed)
                                }
                                keyboardController?.hide()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = EditorNormalColor,
                            ),
                        ) {
                            Text(confirmText)
                        }
                    }
                }
            }
        }
    }
}
