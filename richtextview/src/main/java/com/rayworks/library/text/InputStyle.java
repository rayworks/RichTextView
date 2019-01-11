package com.rayworks.library.text;

/***
 * Input Data source style
 */
public enum InputStyle {
    /***
     * PopupListWindow input style
     */
    POPUP_WINDOW,
    /***
     * EditorText input style
     */
    EDITOR_TEXT;

    public static InputStyle fromType(int type) {
        for (InputStyle style : values()) {
            if (style.ordinal() == type) {
                return style;
            }
        }

        // as a fallback
        return POPUP_WINDOW;
    }
}
