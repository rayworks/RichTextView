package com.rayworks.library.listener;

import java.util.List;

/**
 * Created by seanzhou on 11/12/15.
 */
public interface AnswerCorrectionChecker {
    /***
     * Checks whether it is the correct {@code answer} for question {@code questionIndex}
     *
     * @param questionIndex
     * @param answer
     * @return true if correct, otherwise false.
     */
    boolean isAnswerCorrect(int questionIndex, String answer);

    /***
     * Checks whether the resultList is all the correct answer
     *
     * @param resultList ordered answers
     * @return true if all answers are correct, otherwise false.
     */
    boolean allAnswerCorrect(List<String> resultList);

    /***
     * Retrieves the number of correct answer for this submit
     *
     * @param resultList ordered answers
     * @return the number of correct answer
     */
    int retrieveCorrectAnswers(List<String> resultList);
}
