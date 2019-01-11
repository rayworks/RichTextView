package com.rayworks.library.text;

import android.text.TextPaint;

public class ErrorCorrectionSpan extends TagClickableSpan {
    private boolean reviewMode;

    public ErrorCorrectionSpan(int index, int textColor, boolean reviewMode) {
        super(index, textColor);
        this.reviewMode = reviewMode;
    }

    @Override
    public void updateDrawState(TextPaint ds) {
        // keep the paint's color and remove the underline if not in the review mode.
        ds.setUnderlineText(reviewMode);
    }
}
