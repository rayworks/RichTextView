package com.rayworks.library.listener;

public interface TextClickListener {
    /***
     * Callback when TextView clicked
     *
     * @param lineVerticalIndex The clicked row index
     * @param clickedString Clicked String
     * @param offsetX The offset of the click position on the x axis
     */
    void onTextClicked(int lineVerticalIndex, String clickedString, int offsetX);
}
