package com.rayworks.library;

import org.junit.Assert;
import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * To work on unit tests, switch the Test Artifact in the Build Variants view.
 */
public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() throws Exception {
        assertEquals(4, 2 + 2);
    }

    @Test
    public void testStr() {
        String str = "Jenny: Hey, this is my desk.\nJohn: Hi, I'm John from IT. Are you " +
                "Jenny?\nJenny: Oh! I'm sorry. Yes, I'm Jenny. {How's my mouse?}\nJohn: You need a " +
                "new monitor. {What's your first name?}\nJenny: It's M87.\nJohn: {How's your phone " +
                "number?}\nJenny: It's 6094 5537.\nJohn: 6094 5537... Ok. {How are you?}\nJenny: {" +
                "Nice to meet you.}\nJohn: {Good bye.} as {}\n";

        String regex = "\\{(.)*?\\}";
        String[] subs = str.split(regex);
        System.out.println("Text length : " + subs.length);
        for (String sub : subs)
            System.out.println(sub);

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(str);
        int matchCnt = 0;
        while (matcher.find()) {
            ++matchCnt;
            System.out.println(matcher.group());
        }

        System.out.println("Blank length : " + matchCnt);

        Assert.assertEquals(subs.length, matchCnt + 1);
    }
}