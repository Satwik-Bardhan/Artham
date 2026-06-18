package com.phynix.artham.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.phynix.artham.R;
import com.phynix.artham.adapters.TransactionAdapter;

/**
 * ItemDecoration that draws a sticky date header at the top of the RecyclerView.
 * When the user scrolls, the current day's header sticks at the top and gets
 * pushed up when the next day's header arrives.
 */
public class StickyHeaderDecoration extends RecyclerView.ItemDecoration {

    private final TransactionAdapter adapter;
    private final Paint bgPaint = new Paint();
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private final int headerHeight;
    private final int paddingHorizontal;
    private final float textSize;
    private final float letterSpacing;

    public StickyHeaderDecoration(Context context, TransactionAdapter adapter) {
        this.adapter = adapter;

        // Match item_date_header.xml: paddingTop=8dp, paddingBottom=8dp, textSize=13sp
        float density = context.getResources().getDisplayMetrics().density;
        float scaledDensity = context.getResources().getDisplayMetrics().scaledDensity;

        int paddingVertical = (int) (8 * density); // 8dp top + 8dp bottom
        textSize = 13 * scaledDensity; // 13sp
        paddingHorizontal = (int) (12 * density); // 12dp
        letterSpacing = 0.05f;

        textPaint.setTextSize(textSize);
        textPaint.setFakeBoldText(true);
        textPaint.setLetterSpacing(letterSpacing);

        // Resolve secondary text color for default
        int secondaryColor = ThemeUtil.getThemeAttrColor(context, R.attr.chk_textColorSecondary);
        textPaint.setColor(secondaryColor);

        // Resolve background color
        int bgColor = ThemeUtil.getThemeAttrColor(context, R.attr.chk_backgroundColor);
        bgPaint.setColor(bgColor);

        // Calculate header height: paddingTop + text height + paddingBottom
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        int textHeight = (int) (fm.descent - fm.ascent);
        headerHeight = paddingVertical + textHeight + paddingVertical;
    }

    @Override
    public void onDrawOver(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        if (adapter.getItemCount() == 0) return;

        int topChildPos = getTopVisibleItemPosition(parent);
        if (topChildPos == RecyclerView.NO_POSITION) return;

        // Find the header position for the top visible item
        int currentHeaderPos = getHeaderPositionForItem(topChildPos);
        if (currentHeaderPos == -1) return;

        TransactionAdapter.DateHeaderInfo currentHeader = adapter.getHeaderAtPosition(currentHeaderPos);
        if (currentHeader == null) return;

        // Check if the next header is pushing this one up
        int contactPoint = headerHeight + parent.getPaddingTop();
        float translateY = 0;

        // Find the next header
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int adapterPos = parent.getChildAdapterPosition(child);
            if (adapterPos != RecyclerView.NO_POSITION && adapterPos > currentHeaderPos && adapter.isHeader(adapterPos)) {
                int childTop = child.getTop();
                if (childTop <= contactPoint) {
                    translateY = childTop - contactPoint;
                }
                break;
            }
        }

        // Draw the sticky header
        canvas.save();
        canvas.translate(0, translateY);
        drawHeader(canvas, parent, currentHeader);
        canvas.restore();
    }

    private void drawHeader(Canvas canvas, RecyclerView parent, TransactionAdapter.DateHeaderInfo info) {
        Context context = parent.getContext();
        int left = parent.getPaddingLeft();
        int right = parent.getWidth() - parent.getPaddingRight();
        int top = parent.getPaddingTop();
        int bottom = top + headerHeight;

        // Draw background
        canvas.drawRect(left, top, right, bottom, bgPaint);

        // Build the spannable text with colored counts
        int inColor = ThemeUtil.getThemeAttrColor(context, R.attr.chk_incomeColor);
        int outColor = ThemeUtil.getThemeAttrColor(context, R.attr.chk_expenseColor);
        int secondaryColor = ThemeUtil.getThemeAttrColor(context, R.attr.chk_textColorSecondary);

        String dateUpper = info.dateText.toUpperCase();
        String inStr = String.valueOf(info.inCount);
        String outStr = String.valueOf(info.outCount);
        String full = dateUpper + " (" + inStr + "," + outStr + ")";

        SpannableStringBuilder spannable = new SpannableStringBuilder(full);

        // Default color for whole text
        spannable.setSpan(new ForegroundColorSpan(secondaryColor), 0, full.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        // Color the in-count green
        int inStart = dateUpper.length() + 2; // after " ("
        int inEnd = inStart + inStr.length();
        spannable.setSpan(new ForegroundColorSpan(inColor), inStart, inEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        // Color the out-count red
        int outStart = inEnd + 1; // after ","
        int outEnd = outStart + outStr.length();
        spannable.setSpan(new ForegroundColorSpan(outColor), outStart, outEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        // Draw text centered horizontally
        float textWidth = textPaint.measureText(full);
        float x = (left + right - textWidth) / 2f;

        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textY = top + (headerHeight - fm.descent + fm.ascent) / 2f - fm.ascent;

        // Draw with spans using StaticLayout approach (draw char by char with colors)
        drawSpannableText(canvas, spannable, x, textY, secondaryColor, inColor, outColor,
                dateUpper.length(), inStart, inEnd, outStart, outEnd);
    }

    private void drawSpannableText(Canvas canvas, SpannableStringBuilder spannable,
                                    float x, float y, int defaultColor,
                                    int inColor, int outColor,
                                    int dateLen, int inStart, int inEnd,
                                    int outStart, int outEnd) {
        String fullText = spannable.toString();
        float currentX = x;

        for (int i = 0; i < fullText.length(); i++) {
            if (i >= inStart && i < inEnd) {
                textPaint.setColor(inColor);
            } else if (i >= outStart && i < outEnd) {
                textPaint.setColor(outColor);
            } else {
                textPaint.setColor(defaultColor);
            }
            String ch = fullText.substring(i, i + 1);
            canvas.drawText(ch, currentX, y, textPaint);
            currentX += textPaint.measureText(ch);
        }
    }

    private int getTopVisibleItemPosition(RecyclerView parent) {
        View topChild = parent.getChildAt(0);
        if (topChild == null) return RecyclerView.NO_POSITION;
        return parent.getChildAdapterPosition(topChild);
    }

    private int getHeaderPositionForItem(int itemPos) {
        for (int i = itemPos; i >= 0; i--) {
            if (adapter.isHeader(i)) {
                return i;
            }
        }
        return -1;
    }
}
