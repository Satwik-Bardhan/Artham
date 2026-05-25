package com.phynix.artham.views;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.ViewOutlineProvider;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;

public class AspectRatioCardView extends CardView {
    private static final float ASPECT_RATIO = 8.5f / 5.4f; // width / height

    public AspectRatioCardView(@NonNull Context context) {
        super(context);
        killShadow();
    }

    public AspectRatioCardView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        killShadow();
    }

    public AspectRatioCardView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        killShadow();
    }

    private void killShadow() {
        setCardElevation(0f);
        setMaxCardElevation(0f);
        setUseCompatPadding(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            setElevation(0f);
            setTranslationZ(0f);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = Math.round(width / ASPECT_RATIO);
        int newHeightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
        super.onMeasure(widthMeasureSpec, newHeightMeasureSpec);
    }
}
