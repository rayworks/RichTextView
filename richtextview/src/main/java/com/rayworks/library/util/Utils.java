package com.rayworks.library.util;

import android.os.Build;
import android.text.TextUtils;

/**
 * Created by seanzhou on 7/21/16.
 */
public final class Utils {
    private static final int MARKUP_TYPE_BOLD = 201;
    private static final int MARKUP_TYPE_ITALIC = 202;
    private static final int MARKUP_TYPE_GREEN = 203;
    private static final int MARKUP_TYPE_ESCAPING = 204;
    public static final String HIGHTLIGHT_GREEN_COLOR = "#3ec3d5";
    public static final String MARKUP_TYPE_ESCAPINGN = "!@#$";
    public static final String GAP_FILL = "_____";//gap fill contain 5 underscores
    public static final String GAP_FILL_SUBSTITUTION = "&^%$#@";

    /***
     * Generates html formatted string following pre-defined rules.
     *
     * @param text
     * @return
     */
    public static String getHtmlTextWithMarkups(String text) {
        if (TextUtils.isEmpty(text))
            return "";

        text = text.replace(GAP_FILL, GAP_FILL_SUBSTITUTION);

        text = getMarkupStr(text, MARKUP_TYPE_GREEN, "***", "\\*\\*\\*");
        text = getMarkupStr(text, MARKUP_TYPE_GREEN, "___", "___");

        text = getMarkupStr(text, MARKUP_TYPE_BOLD, "**", "\\*\\*");
        text = getMarkupStr(text, MARKUP_TYPE_BOLD, "__", "__");

        text = getMarkupStr(text, MARKUP_TYPE_ESCAPING, "\\*", "\\\\\\*");

        text = getMarkupStr(text, MARKUP_TYPE_ITALIC, "*", "\\*");
        text = getMarkupStr(text, MARKUP_TYPE_ITALIC, "_", "_");

        text = text.replace(MARKUP_TYPE_ESCAPINGN, "*");
        text = text.replace(GAP_FILL_SUBSTITUTION, GAP_FILL);
        text = text.replace("\n", "<br>");

        return text;
    }

    private static String getMarkupStr(String text, int markupType, String tag, String regex) {
        if (text.contains(tag)) {
            String[] array = text.split(regex);
            if (array.length > 1) {
                StringBuffer sb = new StringBuffer();
                for (int i = 0; i < array.length; i++) {
                    sb.append(array[i]);
                    if (++i >= array.length) break;
                    String str = array[i];
                    if (markupType == MARKUP_TYPE_BOLD) {
                        str = "<b>" + array[i] + "</b>";
                    } else if (markupType == MARKUP_TYPE_GREEN) {
                        str = "<font color='" + HIGHTLIGHT_GREEN_COLOR + "'>" + array[i] + "</font>";
                    } else if (markupType == MARKUP_TYPE_ITALIC) {
                        str = "<i>" + array[i] + "</i>";
                    } else if (markupType == MARKUP_TYPE_ESCAPING) {
                        str = MARKUP_TYPE_ESCAPINGN + array[i] + MARKUP_TYPE_ESCAPINGN;
                    }
                    sb.append(str);
                }
                text = sb.toString();
            }
        }
        return text;
    }

    public static boolean hasJellyBean() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
    }

    /***
     * Gets the number of filtered words from the source.
     *
     * @param source input text
     * @return number of word
     */
    public static int countWordsFromInputText(String source) {
        if (!TextUtils.isEmpty(source)) {
            // split on arbitrary strings of whitespace, rather than just space characters.
            String[] words = source.split("\\s+");

            if (words.length > 0 && words[0].equals("")) {
                // if input starts with whitespace, \r\n etc, decrease the count.
                return words.length - 1;
            } else {
                return words.length;
            }
        } else {
            return 0;
        }
    }
}
