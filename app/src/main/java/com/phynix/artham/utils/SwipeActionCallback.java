package com.phynix.artham.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.phynix.artham.R;
import com.phynix.artham.models.TransactionModel;

/**
 * SwipeActionCallback — Swipe-to-delete and swipe-to-edit for transaction lists.
 * LEFT swipe → Delete (red background)
 * RIGHT swipe → Edit (blue background)
 * Automatically skips date header items.
 */
public class SwipeActionCallback extends ItemTouchHelper.SimpleCallback {

    public interface SwipeListener {
        void onSwipeDelete(TransactionModel transaction, int position);
        void onSwipeEdit(TransactionModel transaction, int position);
    }

    private final SwipeListener listener;
    private final Paint paintDelete = new Paint();
    private final Paint paintEdit = new Paint();
    private final float cornerRadius;

    // View type constants matching TransactionAdapter
    private static final int VIEW_TYPE_HEADER = 0;

    public SwipeActionCallback(Context context, SwipeListener listener) {
        super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        this.listener = listener;

        // Delete = Red
        paintDelete.setColor(0xFFE53935);
        paintDelete.setAntiAlias(true);

        // Edit = Blue
        paintEdit.setColor(0xFF1E88E5);
        paintEdit.setAntiAlias(true);

        cornerRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12,
                context.getResources().getDisplayMetrics());
    }

    @Override
    public int getSwipeDirs(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        // Don't allow swiping on date headers
        if (viewHolder.getItemViewType() == VIEW_TYPE_HEADER) {
            return 0;
        }
        return super.getSwipeDirs(recyclerView, viewHolder);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView,
                          @NonNull RecyclerView.ViewHolder viewHolder,
                          @NonNull RecyclerView.ViewHolder target) {
        return false; // We don't support drag-and-drop
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        // We need to get the transaction from the adapter
        // The adapter is accessed through the tag set during binding
        Object tag = viewHolder.itemView.getTag(R.id.transactionTag);
        if (!(tag instanceof TransactionModel)) return;

        TransactionModel transaction = (TransactionModel) tag;
        int position = viewHolder.getAdapterPosition();

        if (direction == ItemTouchHelper.LEFT) {
            listener.onSwipeDelete(transaction, position);
        } else if (direction == ItemTouchHelper.RIGHT) {
            listener.onSwipeEdit(transaction, position);
        }
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                            @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY,
                            int actionState, boolean isCurrentlyActive) {

        View itemView = viewHolder.itemView;
        float top = itemView.getTop();
        float bottom = itemView.getBottom();

        if (dX < 0) {
            // Swiping LEFT → Delete
            float left = itemView.getRight() + dX;
            RectF rect = new RectF(left, top, itemView.getRight(), bottom);
            c.drawRoundRect(rect, cornerRadius, cornerRadius, paintDelete);

            // Draw delete icon
            Drawable icon = ContextCompat.getDrawable(recyclerView.getContext(), R.drawable.ic_delete);
            if (icon != null) {
                int iconSize = (int) (24 * recyclerView.getContext().getResources().getDisplayMetrics().density);
                int iconMargin = (int) ((bottom - top - iconSize) / 2);
                int iconLeft = itemView.getRight() - iconMargin - iconSize;
                int iconTop = (int) top + iconMargin;
                icon.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize);
                icon.setTint(0xFFFFFFFF);
                icon.draw(c);
            }

        } else if (dX > 0) {
            // Swiping RIGHT → Edit
            float right = itemView.getLeft() + dX;
            RectF rect = new RectF(itemView.getLeft(), top, right, bottom);
            c.drawRoundRect(rect, cornerRadius, cornerRadius, paintEdit);

            // Draw edit icon
            Drawable icon = ContextCompat.getDrawable(recyclerView.getContext(), R.drawable.ic_edit);
            if (icon != null) {
                int iconSize = (int) (24 * recyclerView.getContext().getResources().getDisplayMetrics().density);
                int iconMargin = (int) ((bottom - top - iconSize) / 2);
                int iconLeft = itemView.getLeft() + iconMargin;
                int iconTop = (int) top + iconMargin;
                icon.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize);
                icon.setTint(0xFFFFFFFF);
                icon.draw(c);
            }
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
    }

    @Override
    public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
        return 0.35f; // 35% swipe to trigger
    }
}
