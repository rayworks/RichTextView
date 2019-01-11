package com.rayworks.library.text;

import android.text.Layout;
import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.view.MotionEvent;
import android.widget.TextView;

import com.rayworks.library.listener.TextClickListener;

import timber.log.Timber;

// http://stackoverflow.com/questions/20856105/change-the-text-color-of-a-single-clickablespan-when-pressed-without-affecting-o#
public class LinkTouchMovementMethod extends LinkMovementMethod {
    private TextClickListener textClickListener;

    public LinkTouchMovementMethod setTextClickListener(TextClickListener textClickListener) {
        this.textClickListener = textClickListener;
        return this;
    }

    @Override
    public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
        int action = event.getAction();
        if (action == MotionEvent.ACTION_UP) {
            int x = (int) event.getX();
            int y = (int) event.getY();

            x -= widget.getTotalPaddingLeft();
            y -= widget.getTotalPaddingTop();

            x += widget.getScrollX();
            y += widget.getScrollY();

            Layout layout = widget.getLayout();
            int lineIndexHit = layout.getLineForVertical(y);
            int off = layout.getOffsetForHorizontal(lineIndexHit, x);

            TagClickableSpan clickedSpan = null;
            TagClickableSpan[] spans = buffer.getSpans(off, off, TagClickableSpan.class);

            int xLineStart = layout.getLineStart(lineIndexHit);
            Timber.d(">>> text index Line : " + xLineStart);

            if (spans.length > 0) {
                clickedSpan = spans[0];
                int spanTxtIndex = buffer.getSpanStart(clickedSpan);
                Timber.d(">>> text index Span : " + spanTxtIndex);

                // measure the text from the start of current line to the span start pos
                String fullText = widget.getText().toString();

                String linePreTxt = "";
                int spanEndIndex = buffer.getSpanEnd(clickedSpan);
                if (xLineStart > spanTxtIndex) {
                    // already filled in with a long text ?!
                    linePreTxt = fullText.substring(spanTxtIndex, spanEndIndex);
                } else {
                    linePreTxt = fullText.substring(xLineStart, spanTxtIndex);
                }
                String clickedSpanString = fullText.substring(spanTxtIndex, spanEndIndex);

                int offsetXClickedSpan = (int) widget.getPaint().measureText(linePreTxt) + widget.getPaddingLeft();

                if(textClickListener != null) {
                    textClickListener.onTextClicked(lineIndexHit, clickedSpanString, offsetXClickedSpan);
                }
            }

            Timber.d(">>> Line location LineNo: " + lineIndexHit + " offset Hor: " + off);
        }
        return super.onTouchEvent(widget, buffer, event);
    }
}
