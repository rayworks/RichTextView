package com.rayworks.richtextview.ui;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.rayworks.library.GapFillTextView;
import com.rayworks.richtextview.R;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import timber.log.Timber;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Timber.plant(new Timber.DebugTree());

        GapFillTextView textView = (GapFillTextView) findViewById(R.id.richText);
        String raw = "**My hometown**\n\nBy Kelly Scott\n \nMy {} is Edinburgh, in Scotland. " +
                "It's a really {} city. It's {} the east coast of the country, on the North" +
                " Sea. It's not {} the mountains, but there are mountains nearby. It's around 400 {}" +
                " from London to Edinburgh. By car, that's {} seven hours. Come in the summer; in " +
                "winter, it's really cold!";

        //textView.setInputStyle(GapFillTextView.InputStyle.EDITOR_TEXT);
        textView.setDataSource(Collections.EMPTY_LIST, raw);


        GapFillTextView selector = (GapFillTextView) findViewById(R.id.multiselector);
        String talkMaterial = "Jenny: Hey, this is my desk.\nJohn: Hi, I'm John from IT. Are you " +
                "Jenny?\nJenny: Oh! I'm sorry. Yes, I'm Jenny. {How's my mouse?}\nJohn: You need a " +
                "new monitor. {What's your first name?}\nJenny: It's M87.\nJohn: {How's your phone " +
                "number?}\nJenny: It's 6094 5537.\nJohn: 6094 5537... Ok. {How are you?}\nJenny: {" +
                "Nice to meet you.}\nJohn: {Good bye.} \n" +
                "<b> bold </b>" +
                "<font color= #3ec3d5> highlighted </font>" +
                "<i> italic </i>";

        List<String[]> dataItems = new LinkedList<>();
        int[] resIds = new int[]{R.array.op1, R.array.op2, R.array.op3, R.array.op4, R.array.op5, R.array.op6};
        for (int res : resIds) {
            dataItems.add(getResources().getStringArray(res));
        }

        selector.setDataSource(dataItems, talkMaterial);

    }
}
