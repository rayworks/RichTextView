package com.rayworks.library;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.appcompat.widget.ListPopupWindow;
import android.text.Editable;
import android.text.Html;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.StyleSpan;
import android.transition.Transition;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.rayworks.library.adapter.OptionAdapter;
import com.rayworks.library.listener.AnswerCorrectionChecker;
import com.rayworks.library.listener.AnswerResultObserver;
import com.rayworks.library.listener.ClickableSpanListener;
import com.rayworks.library.listener.TextClickListener;
import com.rayworks.library.text.ErrorCorrectionSpan;
import com.rayworks.library.text.InputStyle;
import com.rayworks.library.text.LinkTouchMovementMethod;
import com.rayworks.library.text.TagClickableSpan;
import com.rayworks.library.util.Utils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import timber.log.Timber;

/**
 * Created by seanzhou on 11/5/15.
 * <p>
 * The customized textview provides a way to select text from input window to fill up inner blanks.
 * </p>
 */
public class GapFillTextView extends AppCompatTextView implements ClickableSpanListener {
    public static final String DOUBLE_BRACE = "{}";
    public static final String DELETION_SYMBOL = "-";
    public static final int INTERVAL_IDLE = 1000 * 20;
    public static final String BLANK_CHAR = " ";
    private static final String BLANK_TAG = "____";
    private static String deletionString;
    private ListPopupWindow popupWindow;
    private String[] texts;
    /**
     * index of clicked span
     **/
    private int clickedSpanIndex;
    private List<String[]> optionItems;
    //private ArrayAdapter<String> adapter;
    private OptionAdapter optionAdapter;
    /**
     * the line index corresponding to the specified vertical position
     **/
    private int lineIndexHit;
    private int offsetXClickedSpan;
    private String clickedSpanString;
    private EditText editBox;
    private PopupWindow window;
    private Dialog alertDialog;
    private HashMap<Integer, String> filledTexts = new HashMap<>();
    private int popwndBkgColor = 0x0078FF;
    private int colorCorrectAnswer = Color.GREEN;
    private int colorWrongAnswer = Color.RED;
    private String rawStr;
    private ImageSpan imageSpan;
    /**
     * Word max limit for free form style
     */
    private int wordCountMaxLimitation = 128;
    /***
     * whether it's the error-correction style of multiple selection
     */
    private boolean errorCorrectionEnabled;
    private Runnable interactionCheckerTask;
    /***
     * Review mode. The textview is not touchable.
     */
    private boolean reviewMode;
    private InputStyle inputStyle = InputStyle.POPUP_WINDOW;
    private AnswerCorrectionChecker answerCorrectionChecker;
    private AnswerResultObserver answerResultObserver;
    private Handler handler;
    private long lastInteractionTime;
    private String editorHint;
    private String editorWarningMsg;
    private String editorConfirmText = "OK";

    private int dropDownAnimStyle = -1;
    private int bottomUpAnimStyle = -1;

    /**
     * Set the animation styles to use when the popup window is shown or dismissed.
     *
     * @param dropDownAnimationStyle Animation style to use for dropdown case.
     * @param bottomUpAnimationStyle Animation style to use for bottom-up case.
     */
    void setPopupWindowAnimationStyles(@StyleRes int dropDownAnimationStyle, @StyleRes int bottomUpAnimationStyle) {
        dropDownAnimStyle = dropDownAnimationStyle;
        bottomUpAnimStyle = bottomUpAnimationStyle;
    }

    public GapFillTextView(Context context) {
        this(context, null);
    }

    public GapFillTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GapFillTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.GapFillTextView);
        if (ta != null) {
            colorCorrectAnswer = ta.getColor(R.styleable.GapFillTextView_correct_color, Color.GREEN);
            colorWrongAnswer = ta.getColor(R.styleable.GapFillTextView_wrong_color, Color.RED);

            int style = ta.getInt(R.styleable.GapFillTextView_gapFillInputStyle, 0);
            inputStyle = InputStyle.fromType(style);
            Timber.d(">>> Input Style value: " + style);

            ta.recycle();
        }

        init();
    }

    public static void setDeletionString(String deletion) {
        if (!TextUtils.isEmpty(deletion)) {
            OptionAdapter.setDeletionMsg(deletion);
        }
    }

    public void setWordCountMaxLimitation(int limitation) {
        this.wordCountMaxLimitation = limitation;
    }

    public boolean isErrorCorrectionEnabled() {
        return errorCorrectionEnabled && inputStyle.equals(InputStyle.POPUP_WINDOW);
    }

    public boolean isReviewMode() {
        return reviewMode;
    }

    public void setReviewMode(boolean reviewMode) {
        this.reviewMode = reviewMode;
    }

    public void setInputStyle(InputStyle inputStyle) {
        this.inputStyle = inputStyle;
    }

    public void setAnswerCorrectionChecker(AnswerCorrectionChecker answerCorrectionChecker) {
        this.answerCorrectionChecker = answerCorrectionChecker;
    }

    public void setAnswerResultObserver(AnswerResultObserver answerResultObserver) {
        this.answerResultObserver = answerResultObserver;
    }

    public void setEditorHint(String editorHint) {
        this.editorHint = editorHint;
    }

    public void setEditorWarningMsg(String editorWarningMsg) {
        this.editorWarningMsg = editorWarningMsg;
    }

    public void setEditorConfirmText(String editorConfirmText) {
        this.editorConfirmText = editorConfirmText;
    }

    private void init() {
        setBackgroundResource(R.drawable.writing_text_frame_bkg);
        setMovementMethod(new LinkTouchMovementMethod().setTextClickListener(
                new TextClickListener() {
                    @Override
                    public void onTextClicked(int lineVerticalIndex, String clickedString, int offsetX) {
                        lineIndexHit = lineVerticalIndex;
                        clickedSpanString = clickedString;
                        offsetXClickedSpan = offsetX;
                    }
                }
        ));

        handler = new Handler();
        interactionCheckerTask = new Runnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                boolean allBlanksComplete = filledTexts.size() == texts.length - 1;

                if (now - lastInteractionTime >= INTERVAL_IDLE && !allBlanksComplete) {
                    if (answerResultObserver != null) {
                        answerResultObserver.onIdleState();
                    }

                    // continue with the next task
                    lastInteractionTime = now;
                    handler.postDelayed(interactionCheckerTask, INTERVAL_IDLE);
                }
            }
        };
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        boolean eventConsumed = super.onTouchEvent(event);
        if (eventConsumed) {
            rescheduleHintTask();
        }
        return eventConsumed;
    }

    /***
     * Cancels the pending task and start a new one from this moment.
     */
    public void rescheduleHintTask() {
        lastInteractionTime = System.currentTimeMillis();

        //Timber.d("interaction at time: %d, next checking at %d", lastInteractionTime, lastInteractionTime + INTERVAL_IDLE);
        handler.removeCallbacks(interactionCheckerTask);
        handler.postDelayed(interactionCheckerTask, INTERVAL_IDLE);
    }

    /***
     * Updates the data source.
     *
     * @param optionItems options for filling blanks. If one of the options equals to empty string, it indicates the Deletion option is here.
     *                    [Trash Icon] + option text
     * @param rawText     original formatted text which contains special characters indicating the blank.
     */
    public void setDataSource(List<String[]> optionItems, String rawText) throws IllegalArgumentException {
        if ((optionItems == null || optionItems.size() == 0) && InputStyle.POPUP_WINDOW.equals(inputStyle)) {

            throw new IllegalArgumentException("Option item in Wring Material can't be empty for Multiple Selection type.");
        }

        if (TextUtils.isEmpty(rawText)) {
            throw new IllegalArgumentException("Main text in Wring Material can't be empty.");
        }

        this.optionItems = new ArrayList<>(optionItems);

        normalizeInput(rawText);

        setFormattedText(false, null);

        if (optionAdapter == null) {
            optionAdapter = new OptionAdapter(this.getContext(), new ArrayList<String>());
        }

        // schedule the task for error correction only.
        if (errorCorrectionEnabled) {
            handler.postDelayed(interactionCheckerTask, INTERVAL_IDLE);
        }
    }

    /***
     * Refines the input text, adds blanks as extra segment to make sure there will be N {*} blocks
     * and N+1 filtered segments
     *
     * @param text
     */
    private void normalizeInput(String text) {
        if (text.endsWith("}")) {
            text += BLANK_CHAR;
            Timber.d(">>> normalizing Input in the end with #%s#", BLANK_CHAR);
        }

        if (text.startsWith("{")) {
            text = BLANK_CHAR + text;
            Timber.d(">>> normalizing Input in the beginning with #%s#", BLANK_CHAR);
        }

        this.rawStr = text;
    }

    /***
     * Gets the formatted and all blanks filled text.
     *
     * @return Formatted text like 'TEXT {ANS1} TEXT {ANS2} TEXT {ANS3} TEXT {ANS4} TEXT'
     */
    public String getFormattedFullText() {
        String originalTxt = this.rawStr;
        String[] strs = originalTxt.split("\\{\\}");

        if (strs.length > 1 && filledTexts.size() == strs.length - 1) {
            StringBuilder builder = new StringBuilder(strs[0]);
            int blanks = filledTexts.size();
            for (int i = 0; i < blanks; i++) {
                builder.append('{');
                builder.append(filledTexts.get(i));
                builder.append('}');

                builder.append(strs[i + 1]);
            }

            return builder.toString();
        }

        throw new IllegalStateException("Text is not filled up completely");
    }

    /***
     * Clears the state and unregister the listeners if any
     */
    public void resetState() {
        filledTexts.clear();
        inputStyle = InputStyle.POPUP_WINDOW;

        answerCorrectionChecker = null;
        answerResultObserver = null;
    }

    /***
     * Retrieves the number of correct answers.
     *
     * @return
     */
    public int retrieveCorrectAnswers() {
        LinkedList<String> resultList = getAllAnswers();
        if (answerCorrectionChecker == null || resultList.isEmpty())
            return 0;

        return answerCorrectionChecker.retrieveCorrectAnswers(resultList);
    }

    /***
     * Applies the formatted text
     *
     * @param hitDetected whether a span gets hit.
     * @param source      new source string for clicked span. It's valid when {@code hitDetected} is true.
     */
    private void setFormattedText(boolean hitDetected, String source) {
        String formattedStr = Utils.getHtmlTextWithMarkups(this.rawStr);

        SpannableStringBuilder stringBuilder = setupSpansForMultipleSelection(hitDetected, source, formattedStr);

        setText(stringBuilder);

        // trigger the further checking for all the texts
        if (hitDetected) {
            int count = texts.length - 1;

            int correctNum = retrieveCorrectAnswers();
            if (answerResultObserver != null) {
                answerResultObserver.onAnswerSelected(correctNum, filledTexts.size(), count);

                if (filledTexts.size() == texts.length - 1) {
                    LinkedList<String> resultList = getAllAnswers();
                    boolean result = answerCorrectionChecker.allAnswerCorrect(resultList);
                    answerResultObserver.onHandleAllAnswerCorrect(result);
                }
            }
        }
    }

    @NonNull
    private LinkedList<String> getAllAnswers() {
        LinkedList<String> resultList = new LinkedList<>();

        if (texts != null && texts.length > 0) {
            int count = texts.length - 1;
            for (int j = 0; j < count; j++) {
                String answer = filledTexts.get(j);
                if (DELETION_SYMBOL.equals(answer)) { // translation for deletion answer
                    answer = "";
                }
                resultList.add(answer);
            }
        }
        return resultList;
    }

    @NonNull
    private SpannableStringBuilder setupSpansForMultipleSelection(boolean hitDetected, String source,
                                                                  String formattedStr) {
        String regex = "\\{(.*?)\\}"; // filter by all the {}, {texts} style strings
        texts = formattedStr.split(regex);

        if (texts.length <= 1) {
            throw new IllegalArgumentException("Target String has illegal format.");
        }

        // We only support Multiple-Selection style && Free form style
        if (texts.length - 1 > optionItems.size() && optionItems.size() > 0) {

            // more blanks to be filled than answer option items ?
            throw new IllegalArgumentException(
                    String.format(Locale.ENGLISH,
                            "%d blanks to be filled but %d answer option items found.",
                            texts.length - 1, optionItems.size()));
        }

        SpannableStringBuilder stringBuilder = new SpannableStringBuilder();

        LinkedList<String> spanTexts = new LinkedList<>();
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(formattedStr);
        int matchCnt = 0;
        while (matcher.find()) {
            ++matchCnt;
            spanTexts.add(matcher.group());
        }

        stringBuilder.append(Html.fromHtml(texts[0]));

        // matchCnt + 1 == texts.length
        for (int i = 0; i < matchCnt; i++) {
            boolean spanFirstSelection = false;
            boolean hitIndex = false;

            String placeholder = "";

            // the hit area detection
            int spanIndex = i;
            if (hitDetected) {
                if (spanIndex == clickedSpanIndex) {
                    spanFirstSelection = !filledTexts.containsKey(spanIndex);
                    hitIndex = true;

                    String selectedSource = source;
                    if (TextUtils.isEmpty(source)) {
                        selectedSource = DELETION_SYMBOL;
                    }
                    placeholder = selectedSource;

                    filledTexts.put(clickedSpanIndex, placeholder);
                } else if (filledTexts.containsKey(spanIndex)) {
                    hitIndex = true;
                    placeholder = filledTexts.get(spanIndex); // concat the historic texts
                }
            }

            String str = texts[i + 1]; // TXT | { ... } TXT { ... } TXT { ... } TXT
            // check the initial values
            if (TextUtils.isEmpty(placeholder)) {
                String spanTxt = spanTexts.get(i);
                placeholder = spanTxt.substring(1, spanTxt.length() - 1); // {placeholder}

                if (TextUtils.isEmpty(placeholder)) {
                    // only the "{}" found, replaced with the underline
                    placeholder = BLANK_TAG;
                    errorCorrectionEnabled = false;
                } else {
                    errorCorrectionEnabled = true;

                    // apply the html format for input content
                    placeholder = Html.fromHtml(placeholder).toString();
                }
            }

            addClickableSpanArea(stringBuilder, placeholder, spanIndex, spanFirstSelection, hitIndex);
            stringBuilder.append(Html.fromHtml(str));
        }

        return stringBuilder;
    }

    @Override
    public void onClick(int index) {
        clickedSpanIndex = index;

        if (!isReviewMode())
            showInputSourceView();
    }

    /***
     * Appends the clickable span with formatted text
     *
     * @param stringBuilder        span string builder [IN/OUT param]
     * @param placeholder          content for the span area
     * @param indexOfClickableSpan clickable span index
     * @param spanFirstSelection   whether it's the first time to select the answer
     * @param hitIndex             whether current span is clicked
     */
    private void addClickableSpanArea(SpannableStringBuilder stringBuilder, String placeholder,
                                      int indexOfClickableSpan, boolean spanFirstSelection, boolean hitIndex) {
        boolean deleteSelected = placeholder.equals(DELETION_SYMBOL);

        SpannableString spanUnderString;
        if (!deleteSelected) {
            spanUnderString = new SpannableString(placeholder);
        } else {
            spanUnderString = new SpannableString(" ");

            if (imageSpan == null) {
                imageSpan = new ImageSpan(getContext(), R.drawable.settings_delete, ImageSpan.ALIGN_BASELINE);
            }
            spanUnderString.setSpan(imageSpan, 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        TagClickableSpan urlSpan;
        int textColor = getTextColors().getDefaultColor();
        if (errorCorrectionEnabled) {
            urlSpan = new ErrorCorrectionSpan(indexOfClickableSpan, textColor, reviewMode);
        } else {
            urlSpan = new TagClickableSpan(indexOfClickableSpan, textColor);
        }
        urlSpan.setClickListener(this);

        spanUnderString.setSpan(urlSpan, 0, placeholder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        if (hitIndex) {
            ForegroundColorSpan colorSpan;
            if (InputStyle.POPUP_WINDOW.equals(inputStyle)) {
                // check answer for all the filled texts
                boolean correctAnswer = false;
                if (answerCorrectionChecker != null) {
                    correctAnswer = answerCorrectionChecker.isAnswerCorrect(indexOfClickableSpan,
                            filledTexts.get(indexOfClickableSpan));
                }
                if (answerResultObserver != null && correctAnswer && spanFirstSelection) {
                    answerResultObserver.onAnswerCorrectForFirstTime(indexOfClickableSpan);
                }

                colorSpan = new ForegroundColorSpan(correctAnswer ? colorCorrectAnswer : colorWrongAnswer);
            } else {
                colorSpan = new ForegroundColorSpan(Color.BLACK);
            }
            spanUnderString.setSpan(colorSpan, 0, placeholder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            StyleSpan span = new StyleSpan(Typeface.BOLD);
            spanUnderString.setSpan(span, 0, placeholder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        }
        stringBuilder.append(spanUnderString);

        // append horizental space
        stringBuilder.append(" ");
    }

    @Override
    protected void onDetachedFromWindow() {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
        }

        if (alertDialog != null && alertDialog.isShowing()) {
            alertDialog.dismiss();
        }

        answerCorrectionChecker = null;
        answerResultObserver = null;

        handler.removeCallbacks(interactionCheckerTask);

        super.onDetachedFromWindow();
    }

    private void showInputSourceView() {
        int singleLineHeight = getLineHeight();
        int lineNum = lineIndexHit + 1;
        int scrolledTxtVerticalOffset = getScrollY();
        Timber.d(">>> Scrolled Y " + scrolledTxtVerticalOffset + " top: " + getTop() + " bottom: " + getBottom()
                + " height: " + getHeight());

        String clickedSpanStr = clickedSpanString;
        float clickedSpanStrWidth = getPaint().measureText(clickedSpanStr);

        /**
         * negative vertical offset relative to TextView to show the popupwindow
         */
        int verticalOffset = (getTop() + getPaddingTop() + lineNum * singleLineHeight - scrolledTxtVerticalOffset) - getBottom();
        Timber.d(">>> verticalOffset: " + verticalOffset);

        switch (inputStyle) {
            case EDITOR_TEXT:
                showEditorView();
                break;

            case POPUP_WINDOW:
            default:
                showPopupWnd(singleLineHeight, clickedSpanStrWidth, verticalOffset);
                break;
        }

    }

    private void showEditorView() {
        if (alertDialog == null) {
            final View view = LayoutInflater.from(getContext()).inflate(R.layout.view_dialog_gapfill_form, null);
            final TextView wordCountMessage = (TextView) view.findViewById(R.id.word_limit_message);
            final Button confirmBtn = (Button) view.findViewById(R.id.button_ok);

            wordCountMessage.setText(editorWarningMsg);
            confirmBtn.setText(editorConfirmText);

            editBox = (EditText) view.findViewById(R.id.editor_box);
            editBox.setHint(editorHint);
            editBox.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (s.length() > 0) {
                        int length = Utils.countWordsFromInputText(s.toString());

                        if (length > wordCountMaxLimitation) {
                            view.setBackgroundResource(R.drawable.rectangle_gapfill_form_bkg_red);
                            wordCountMessage.setVisibility(VISIBLE);
                            confirmBtn.setVisibility(INVISIBLE);
                        } else {
                            view.setBackgroundResource(R.drawable.rectangle_gapfill_form_bkg);
                            wordCountMessage.setVisibility(INVISIBLE);
                            confirmBtn.setVisibility(VISIBLE);
                        }
                    } else {
                        view.setBackgroundResource(R.drawable.rectangle_gapfill_form_bkg);
                        wordCountMessage.setVisibility(INVISIBLE);
                        confirmBtn.setVisibility(VISIBLE);
                    }
                }
            });

            confirmBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String newTxt = editBox.getText().toString();
                    if (!TextUtils.isEmpty(newTxt)) {
                        populateTextView(newTxt);
                    }
                    alertDialog.dismiss();
                }
            });

            if (alertDialog == null) {
                alertDialog = new AlertDialog.Builder(getContext()).setView(view).create();

                // disable dim background
                alertDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

                alertDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        // Let the view gain the focus again and hide the keyboard when the dialog dismissed.
                        GapFillTextView.this.requestFocus();

                        // There is a window switch between dismissing current Dialog and displaying
                        // previous Activity. In order to get the valid window token, the runnable
                        // is used here.
                        GapFillTextView.this.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                                imm.hideSoftInputFromWindow(GapFillTextView.this.getWindowToken(), 0);
                            }
                        }, 100);

                    }
                });
            }
        }

        editBox.setText("");
        alertDialog.show();
    }

    private void showPopupWnd(int singleLineHeight, float clickedSpanStrWidth, int verticalOffset) {
        if (popupWindow == null) {
            popupWindow = new ListPopupWindow(this.getContext());
            Drawable drawable = getResources().getDrawable(R.drawable.popupwnd_bkg);
            popupWindow.setBackgroundDrawable(drawable);
            popupWindow.setForceIgnoreOutsideTouch(false);

            popupWindow.setAdapter(optionAdapter);
            popupWindow.setAnchorView(this);

            popupWindow.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    String source = optionItems.get(clickedSpanIndex)[position];
                    populateTextView(source);

                    popupWindow.dismiss();
                }
            });

            // Fix an Android 23 SDK specific issue, which adds extra EnterTransition and ExitTransition
            // on PopupWindow. Nedd remove it.
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
                Class<?> classListPopupWindow = ListPopupWindow.class;
                try {
                    Field fieldMPopup = classListPopupWindow.getDeclaredField("mPopup");
                    fieldMPopup.setAccessible(true);
                    Object innerPopupImpl = fieldMPopup.get(popupWindow);

                    Method methodSetEnterTransition = PopupWindow.class.getMethod("setEnterTransition", Transition.class);
                    Method methodSetExitTransition = PopupWindow.class.getMethod("setExitTransition", Transition.class);

                    Object arg = null;
                    methodSetEnterTransition.invoke(innerPopupImpl, arg);
                    methodSetExitTransition.invoke(innerPopupImpl, arg);
                } catch (NoSuchFieldException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                } catch (NoSuchMethodException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }

        }

        optionAdapter.clear();
        optionAdapter.updateItems(Arrays.asList(optionItems.get(clickedSpanIndex)));

        // set the boundaries for the width
        int listItemMaxWidth = optionAdapter.measureContentWidth();
        listItemMaxWidth = Math.min(Math.max(listItemMaxWidth, 300), getWidth());
        popupWindow.setContentWidth(listItemMaxWidth);
        popupWindow.setWidth(listItemMaxWidth);

        if (listItemMaxWidth == getWidth()) {
            // in this case, a item may have multiple lines
            popupWindow.setHeight(getHeight() / 2);
        } else {
            // set height for maximum 4 items which contain a single line text
            popupWindow.setHeight(optionAdapter.getFirstItemHeight() * Math.min(4, optionAdapter.getCount()));
        }

        int offsetX = (int) (offsetXClickedSpan - (popupWindow.getWidth() - clickedSpanStrWidth) / 2);
        /***
         * Notice:
         * It seems the PopupWindow position goes wrong if its bound width is over the anchor TextView.
         */
        if (offsetXClickedSpan + popupWindow.getWidth() > getWidth()) {
            offsetX = getWidth() - popupWindow.getWidth();
            Timber.d(">>> PopupWindow position X adjusted to : " + offsetX);
        }

        int horizontalOffset = Math.max(0, offsetX);
        popupWindow.setHorizontalOffset(horizontalOffset);// touchedX

        /***
         * If the vertical distance is not enough to show the option list,
         * we show the ListPopupWindow by the UP direction
         */
        if (Math.abs(verticalOffset) < popupWindow.getHeight()) {
            if (bottomUpAnimStyle != -1)
                popupWindow.setAnimationStyle(bottomUpAnimStyle);
            else
                popupWindow.setAnimationStyle(R.style.PopupBottomUp);

            verticalOffset -= (singleLineHeight + popupWindow.getHeight());
            Timber.d(">>> verticalOffset post: " + verticalOffset + " | popwnd height: " + popupWindow.getHeight());
        } else {
            // make a slightly adjustment for vertical position when window is displaying top-down.
            if (Utils.hasJellyBean()) {
                verticalOffset -= Math.max(getLineSpacingExtra() - 2, 0);
            }

            if (dropDownAnimStyle != -1)
                popupWindow.setAnimationStyle(dropDownAnimStyle);
            else
                popupWindow.setAnimationStyle(R.style.PopupDropDown);
        }

        popupWindow.setAnchorView(this);
        popupWindow.setVerticalOffset(verticalOffset);//
        popupWindow.show();

        // always show the scrollbar if the its height is not enough for displaying all the items.
        if (popupWindow.isShowing()) {
            popupWindow.getListView().setScrollbarFadingEnabled(false);
        }
    }

    private void populateTextView(String source) {
        // Regenerate the spanString to fix overlapped texts
        setFormattedText(true, source);
    }

    public int getLastVisibleLineNumber() {
        int scrollY = getScrollY();
        Layout layout = getLayout();
        return layout.getLineForVertical(scrollY + getHeight() - getPaddingTop() - getPaddingBottom());
    }

    public int getFirstVisibleLineNumber() {
        int scrollY = getScrollY();
        Layout layout = getLayout();
        return layout.getLineForVertical(scrollY - getPaddingTop());
    }

    /***
     * Gets the hint line index
     *
     * @return index if found, otherwise -1;
     */
    public int getHintLineIndex() {
        SpannableString spannableString = new SpannableString(getText());
        ClickableSpan[] correctionSpans = spannableString.getSpans(0, spannableString.length(), ClickableSpan.class);

        Layout layout = getLayout();

        int lineCount = layout.getLineCount();

        int targetLineIndex = -1;
        if (correctionSpans.length > 0) {
            for (int j = 0; j < correctionSpans.length; j++) {
                ClickableSpan span = correctionSpans[j];

                int lineStartPos, lineEndPos;

                for (int i = 0; i < lineCount; i++) {
                    lineStartPos = layout.getLineStart(i);
                    lineEndPos = layout.getLineEnd(i);

                    int spanTxtIndex = spannableString.getSpanStart(span);

                    boolean atLineRange = lineStartPos <= spanTxtIndex && lineEndPos > spanTxtIndex;
                    boolean notFilled = filledTexts.get(j) == null;
                    if (atLineRange && notFilled) {
                        targetLineIndex = i;
                        Timber.d(">>> target tip line index: %d, span index: %d", targetLineIndex, j);
                        break;
                    }
                }
                if (targetLineIndex >= 0) {
                    break;
                }
            }
        }
        return targetLineIndex;
    }

}
