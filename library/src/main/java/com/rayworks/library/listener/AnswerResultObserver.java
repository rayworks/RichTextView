package com.rayworks.library.listener;

/**
 * Created by seanzhou on 11/12/15.
 */
public interface AnswerResultObserver {
    /***
     * Callback when all the answers are correct.
     *
     * @param allCorrect
     */
    void onHandleAllAnswerCorrect(boolean allCorrect);

    /***
     * Callback when getting the right answer for the first time
     *
     * @param questionIndex index of the question
     */
    void onAnswerCorrectForFirstTime(int questionIndex);

    /***
     * Callback when one answer is filled up
     * @param correctNum
     * @param finishedNum
     * @param total
     */
    void onAnswerSelected(int correctNum, int finishedNum, int total);

    /***
     * Callback when no interaction occurred for some time.
     */
    void onIdleState();
}
