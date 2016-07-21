package com.rayworks.library.adapter;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.rayworks.library.R;

import java.util.List;

/**
 * Created by seanzhou on 11/17/15.
 */
public class OptionAdapter extends BaseAdapter {
    private Context context;
    private List<String> optionItems;
    private final LayoutInflater inflater;
    private static String deletionMsg = "delete this word";

    public OptionAdapter(Context context, List<String> items) {

        this.context = context;
        this.optionItems = items;
        inflater = LayoutInflater.from(context);
    }

    public void clear(){
        optionItems.clear();
        notifyDataSetChanged();
    }

    public static void setDeletionMsg(String deletionMsg){
        OptionAdapter.deletionMsg = deletionMsg;
    }

    public void updateItems(List<String> newItems){
        if(optionItems == null){
            optionItems = newItems;
        }else{
            optionItems.clear();
            optionItems.addAll(newItems);
        }

        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return optionItems.size();
    }

    @Override
    public Object getItem(int position) {
        return optionItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = null;
        if(convertView == null){
            convertView = inflater.inflate(R.layout.view_text_center, null);
        }
        view = convertView;

        TextView textView = (TextView)view.findViewById(R.id.text);
        ImageView imageDelete = (ImageView)view.findViewById(R.id.image_delete);

        String txt = optionItems.get(position);
        boolean isEmptyString = TextUtils.isEmpty(txt);
        if(isEmptyString) {
            textView.setText(deletionMsg);
            imageDelete.setVisibility(View.VISIBLE);
        }else{
            textView.setText(txt);
            imageDelete.setVisibility(View.GONE);
        }

        return view;
    }

    public int measureContentWidth() {
        // http://stackoverflow.com/questions/14200724/listpopupwindow-not-obeying-wrap-content-width-spec

        ViewGroup mMeasureParent = null;
        int maxWidth = 0;
        View itemView = null;
        int itemType = 0;

        final ListAdapter adapter = this;
        final int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        final int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        final int count = adapter.getCount();
        for (int i = 0; i < count; i++) {
            final int positionType = adapter.getItemViewType(i);
            if (positionType != itemType) {
                itemType = positionType;
                itemView = null;
            }

            if (mMeasureParent == null) {
                mMeasureParent = new FrameLayout(context);
            }

            itemView = adapter.getView(i, itemView, mMeasureParent);
            itemView.measure(widthMeasureSpec, heightMeasureSpec);

            final int itemWidth = itemView.getMeasuredWidth();

            if (itemWidth > maxWidth) {
                maxWidth = itemWidth;
            }
        }

        return maxWidth;
    }

    public int getFirstItemHeight() {
        if (getCount() == 0) {
            return 0;
        }

        View view = getView(0, null, null);
        view.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int measuredHeight = view.getMeasuredHeight();
        //Timber.d(">>> Item height: %d", measuredHeight);
        return measuredHeight;
    }
}
