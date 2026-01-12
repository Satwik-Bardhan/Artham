package com.phynix.artham.adapters;

import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.phynix.artham.R;
import com.phynix.artham.models.CashbookModel;
import com.phynix.artham.utils.DateTimeUtils;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class CashbookAdapter extends RecyclerView.Adapter<CashbookAdapter.CashbookViewHolder> {

    public interface OnCashbookClickListener {
        void onCashbookClick(CashbookModel cashbook);
        void onFavoriteClick(CashbookModel cashbook);
        void onMenuClick(CashbookModel cashbook, View anchorView);
    }

    private final Context context;
    private final List<CashbookModel> cashbookList;
    private final OnCashbookClickListener listener;
    private final NumberFormat currencyFormat;

    // Theme Colors
    private final int primaryColor;
    private final int successColor;
    private final int secondaryColor;
    private final int favoriteColor;
    private final int expenseColor;

    public CashbookAdapter(Context context, List<CashbookModel> cashbookList, OnCashbookClickListener listener) {
        this.context = context;
        this.cashbookList = new ArrayList<>(cashbookList);
        this.listener = listener;
        this.currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

        // Load colors dynamically from the current theme (Light/Dark/Purple)
        this.primaryColor = ThemeUtil.getThemeAttrColor(context, R.attr.chk_balanceColor);
        this.successColor = ThemeUtil.getThemeAttrColor(context, R.attr.chk_incomeColor);
        this.secondaryColor = ThemeUtil.getThemeAttrColor(context, R.attr.chk_textColorSecondary);
        this.expenseColor = ThemeUtil.getThemeAttrColor(context, R.attr.chk_expenseColor);

        // Fallback or specific color from colors.xml
        this.favoriteColor = ContextCompat.getColor(context, R.color.category_rent); // Using orange/gold for favorites
    }

    @NonNull
    @Override
    public CashbookViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Ensure 'item_cashbook.xml' exists in your layout folder
        View view = LayoutInflater.from(context).inflate(R.layout.item_cashbook, parent, false);
        return new CashbookViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CashbookViewHolder holder, int position) {
        CashbookModel cashbook = cashbookList.get(position);
        holder.bind(cashbook);
    }

    @Override
    public int getItemCount() {
        return cashbookList != null ? cashbookList.size() : 0;
    }

    public void updateCashbooks(List<CashbookModel> newCashbooks) {
        // Use DiffUtil to calculate changes efficiently
        CashbookDiffCallback diffCallback = new CashbookDiffCallback(this.cashbookList, newCashbooks);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);

        this.cashbookList.clear();
        this.cashbookList.addAll(newCashbooks);
        diffResult.dispatchUpdatesTo(this);
    }

    public class CashbookViewHolder extends RecyclerView.ViewHolder {
        // UI Components
        private CardView cashbookItemCard;
        private CardView iconCard;
        private ImageView bookIcon;
        private ImageView favoriteButton;
        private ImageView menuButton;

        private TextView cashbookNameText;
        private TextView statusBadge;
        private TextView lastModifiedText;
        private TextView balanceText;
        private TextView transactionCountText;
        private TextView createdDateText;

        public CashbookViewHolder(@NonNull View itemView) {
            super(itemView);
            // Binding Views - Verify these IDs exist in item_cashbook.xml
            cashbookItemCard = itemView.findViewById(R.id.cashbookItemCard);
            iconCard = itemView.findViewById(R.id.iconCard);
            bookIcon = itemView.findViewById(R.id.bookIcon);

            cashbookNameText = itemView.findViewById(R.id.cashbookNameText);
            statusBadge = itemView.findViewById(R.id.statusBadge);

            favoriteButton = itemView.findViewById(R.id.favoriteButton);
            menuButton = itemView.findViewById(R.id.menuButton);

            lastModifiedText = itemView.findViewById(R.id.lastModifiedText);
            balanceText = itemView.findViewById(R.id.balanceText);
            transactionCountText = itemView.findViewById(R.id.transactionCountText);
            createdDateText = itemView.findViewById(R.id.createdDateText);
        }

        public void bind(CashbookModel cashbook) {
            if (cashbook == null) return;

            // 1. Name
            cashbookNameText.setText(cashbook.getName() != null ? cashbook.getName() : "Unnamed");

            // 2. Status Badge Logic
            setupStatusBadge(cashbook);

            // 3. Favorite Icon Logic
            setupFavoriteIcon(cashbook);

            // 4. Last Modified Text
            setupLastModified(cashbook);

            // 5. Balance Text
            double balance = cashbook.getBalance();
            balanceText.setText(currencyFormat.format(balance));
            balanceText.setTextColor(balance >= 0 ? successColor : expenseColor);

            // 6. Transaction Count
            transactionCountText.setText(String.valueOf(cashbook.getTransactionCount()));

            // 7. Created Date
            if (cashbook.getCreatedDate() > 0) {
                createdDateText.setText(DateTimeUtils.formatDate(cashbook.getCreatedDate(), "MMM yyyy"));
            } else {
                createdDateText.setText("-");
            }

            // 8. Icon Color Background
            if (iconCard != null) {
                iconCard.setCardBackgroundColor(getIconColorForCashbook(cashbook));
            }

            // 9. Click Listeners
            setupListeners(cashbook);
        }

        private void setupStatusBadge(CashbookModel cashbook) {
            if (statusBadge == null) return;

            statusBadge.setVisibility(View.VISIBLE);
            if (cashbook.isCurrent()) {
                statusBadge.setText(context.getString(R.string.status_current)); // Ensure this string exists
                statusBadge.setTextColor(primaryColor);
                statusBadge.setBackgroundResource(R.drawable.bg_badge_current); // Optional: background drawable
            } else if (cashbook.isActive()) {
                statusBadge.setText(context.getString(R.string.status_active));
                statusBadge.setTextColor(successColor);
                statusBadge.setBackgroundResource(R.drawable.bg_badge_active);
            } else {
                statusBadge.setText(context.getString(R.string.status_inactive));
                statusBadge.setTextColor(secondaryColor);
                statusBadge.setBackgroundResource(R.drawable.bg_badge_inactive);
            }
        }

        private void setupFavoriteIcon(CashbookModel cashbook) {
            if (favoriteButton == null) return;

            if (cashbook.isFavorite()) {
                favoriteButton.setImageResource(R.drawable.ic_star_filled);
                favoriteButton.setColorFilter(favoriteColor);
            } else {
                favoriteButton.setImageResource(R.drawable.ic_star_outline);
                favoriteButton.setColorFilter(secondaryColor);
            }
        }

        private void setupLastModified(CashbookModel cashbook) {
            if (lastModifiedText == null) return;

            if (cashbook.getLastModified() > 0) {
                String relativeTime = DateTimeUtils.getRelativeTimeSpan(cashbook.getLastModified());
                lastModifiedText.setText("Updated " + relativeTime);
                lastModifiedText.setVisibility(View.VISIBLE);
            } else {
                lastModifiedText.setVisibility(View.GONE);
            }
        }

        private void setupListeners(CashbookModel cashbook) {
            if (cashbookItemCard != null) {
                cashbookItemCard.setOnClickListener(v -> {
                    if (listener != null) listener.onCashbookClick(cashbook);
                });
            }

            if (favoriteButton != null) {
                favoriteButton.setOnClickListener(v -> {
                    if (listener != null) listener.onFavoriteClick(cashbook);
                });
            }

            if (menuButton != null) {
                menuButton.setOnClickListener(v -> {
                    if (listener != null) listener.onMenuClick(cashbook, v);
                });
            }
        }

        private int getIconColorForCashbook(CashbookModel cashbook) {
            if (cashbook.isCurrent()) return primaryColor;
            if (cashbook.isFavorite()) return favoriteColor;
            if (!cashbook.isActive()) return secondaryColor;
            return successColor;
        }
    }

    // --- DiffUtil Callback Class ---
    private static class CashbookDiffCallback extends DiffUtil.Callback {
        private final List<CashbookModel> oldList;
        private final List<CashbookModel> newList;

        public CashbookDiffCallback(List<CashbookModel> oldList, List<CashbookModel> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList != null ? oldList.size() : 0;
        }

        @Override
        public int getNewListSize() {
            return newList != null ? newList.size() : 0;
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            // Compare unique IDs
            String oldId = oldList.get(oldItemPosition).getCashbookId();
            String newId = newList.get(newItemPosition).getCashbookId();
            return Objects.equals(oldId, newId);
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            CashbookModel oldItem = oldList.get(oldItemPosition);
            CashbookModel newItem = newList.get(newItemPosition);

            // Compare content to check if visual update is needed
            return Objects.equals(oldItem.getName(), newItem.getName()) &&
                    Math.abs(oldItem.getBalance() - newItem.getBalance()) < 0.01 && // Compare double with tolerance
                    oldItem.getTransactionCount() == newItem.getTransactionCount() &&
                    oldItem.isActive() == newItem.isActive() &&
                    oldItem.isCurrent() == newItem.isCurrent() &&
                    oldItem.isFavorite() == newItem.isFavorite() &&
                    oldItem.getLastModified() == newItem.getLastModified();
        }
    }

    // --- Theme Helper Class ---
    static class ThemeUtil {
        static int getThemeAttrColor(Context context, int attr) {
            TypedValue typedValue = new TypedValue();
            if (context.getTheme().resolveAttribute(attr, typedValue, true)) {
                return typedValue.data;
            }
            // Fallback color if attribute not found
            return ContextCompat.getColor(context, android.R.color.darker_gray);
        }
    }
}