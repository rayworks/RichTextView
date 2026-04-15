package com.rayworks.library.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.rayworks.library.R

/** Background colour of the option-picker popup — matches `popupwnd_bkg.xml`. */
private val PopupBackground = Color(0xFF177CFB)

/** Text/icon tint colour used for all items in the popup. */
private val PopupContentColor = Color.White

/** Label shown alongside the deletion icon in the popup list. */
private const val DELETION_LABEL = "Delete"

/** Maximum number of items visible before the popup list becomes scrollable. */
private const val MAX_VISIBLE_ITEMS = 4

/** Approximate height of a single popup item. */
private val ItemHeight = 48.dp

/**
 * Popup option picker used in [com.rayworks.library.text.InputStyle.POPUP_WINDOW] mode.
 *
 * Displays a styled [DropdownMenu] that lists the answer options for the active blank.
 * Up to [MAX_VISIBLE_ITEMS] items are shown before the list becomes scrollable, mirroring
 * the `getFirstItemHeight() * min(4, count)` height calculation in the original Java code.
 *
 * An empty string in [options] represents the deletion option: a trash-icon row is rendered
 * instead of a plain text label (mirrors [com.rayworks.library.adapter.OptionAdapter]).
 *
 * @param expanded       whether the popup is currently visible
 * @param options        answer options for the active blank;
 *                       an empty string indicates the deletion option
 * @param onOptionChosen called with the canonical answer string when the user selects an item;
 *                       an empty option is translated to [DELETION_SYMBOL]
 * @param onDismiss      called when the popup is dismissed without a selection
 */
@Composable
fun GapFillOptionPopup(
    expanded: Boolean,
    options: List<String>,
    onOptionChosen: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = DpOffset(0.dp, 0.dp),
        modifier = Modifier
            .background(PopupBackground, RoundedCornerShape(8.dp))
            .widthIn(min = 120.dp)
            .heightIn(max = ItemHeight * MAX_VISIBLE_ITEMS),
    ) {
        options.forEach { option ->
            DropdownMenuItem(
                text = {
                    if (option.isEmpty()) {
                        // Deletion option: icon + label (mirrors OptionAdapter view_text_center).
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(R.drawable.settings_delete),
                                contentDescription = DELETION_LABEL,
                                tint = PopupContentColor,
                            )
                            Text(
                                text = DELETION_LABEL,
                                color = PopupContentColor,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    } else {
                        Text(text = option, color = PopupContentColor)
                    }
                },
                onClick = {
                    // Empty option string → canonical deletion symbol (matches original).
                    onOptionChosen(if (option.isEmpty()) DELETION_SYMBOL else option)
                },
            )
        }
    }
}
