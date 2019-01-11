package com.rayworks.library.text;

import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.View;

import com.rayworks.library.listener.ClickableSpanListener;

public class TagClickableSpan extends ClickableSpan {

    private final int index;
    private final int color;

    private ClickableSpanListener clickListener;

    public TagClickableSpan(int index, int textColor) {
        this.index = index;
        color = textColor;
    }

    public TagClickableSpan setClickListener(ClickableSpanListener clickListener) {
        this.clickListener = clickListener;
        return this;
    }

    @Override
    public void onClick(View widget) {
        if (clickListener != null) {
            clickListener.onClick(index);
        }
    }

    @Override
    public void updateDrawState(TextPaint ds) {
        // get rid of the underline
        ds.setUnderlineText(false);

        // align the color of original text
        ds.setColor(color);
    }
}
