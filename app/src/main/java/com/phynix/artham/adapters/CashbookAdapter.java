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

import com.phynix.artham.models.CashbookModel;
import com.phynix.artham.R;
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

    private Context context;
    private List<CashbookModel> cashbookList;
    private OnCashbookClickListener listener;
    private NumberFormat currencyFormat;
    private int primaryColor;
    private int successColor;
    private int secondaryColor;
    private int favoriteColor;
    private int expenseColor;

    public CashbookAdapter(Context context, List<CashbookModel> cashbookList,
                           OnCashbookClickListener listener) {
        this.context = context;
        this.cashbookList = new ArrayList<>(cashbookList);
        this.listener = listener;
        this.currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

        // Load theme colors once
        this.primaryColor = ThemeUtil.getThemeAttrColor(context, R.attr.chk_balanceColor);
        this.successColor = ThemeUtil.getThemeAttrColor(context, R.attr.chk_incomeColor);
        this.secondaryColor = ThemeUtil.getThemeAttrColor(context, R.attr.chk_textColorSecondary);
        this.expenseColor = ThemeUtil.getThemeAttrColor(context, R.attr.chk_expenseColor);

        // This color is defined in colors.xml, it is not a theme attribute
        this.favoriteColor = ContextCompat.getColor(context, R.color.category_food);
    }

    @NonNull
    @Override
    public CashbookViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // This line requires you to have a file named 'item_cashbook.xml' in res/layout/
        View view = LayoutInflater.from(context).inflate(
                R.layout.item_cashbook, parent, false);
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
        CashbookDiffCallback diffCallback = new CashbookDiffCallback(
                this.cashbookList, newCashbooks);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);

        this.cashbookList.clear();
        this.cashbookList.addAll(newCashbooks);
        diffResult.dispatchUpdatesTo(this);
    }

    public class CashbookViewHolder extends RecyclerView.ViewHolder {
        private CardView cashbookItemCard, iconCard;
        private ImageView bookIcon, favoriteButton, menuButton;
        private TextView cashbookNameText, statusBadge, lastModifiedText, balanceText, transactionCountText, createdDateText;

        public CashbookViewHolder(@NonNull View itemView) {
            super(itemView);
            // These IDs must match your 'item_cashbook.xml' file
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
            cashbookNameText.setText(cashbook.getName());

            // Set status badge
            if (cashbook.isCurrent()) {
                statusBadge.setText(context.getString(R.string.status_current));
                statusBadge.setTextColor(primaryColor);
                statusBadge.setVisibility(View.VISIBLE);
            } else if (cashbook.isActive()) {
                statusBadge.setText(context.getString(R.string.status_active));
                statusBadge.setTextColor(successColor);
                statusBadge.setVisibility(View.VISIBLE);
            } else {
                statusBadge.setText(context.getString(R.string.status_inactive));
                statusBadge.setTextColor(secondaryColor);
                statusBadge.setVisibility(View.VISIBLE);
            }

            // Set favorite icon
            if (cashbook.isFavorite()) {
                favoriteButton.setImageResource(R.drawable.ic_star_filled); // Use a filled star
                favoriteButton.setColorFilter(favoriteColor);
            } else {
                favoriteButton.setImageResource(R.drawable.ic_star_outline);
                favoriteButton.setColorFilter(secondaryColor);
            }

            // Set last modified text
            if (cashbook.getLastModified() > 0) {
                String lastModified = DateTimeUtils.getRelativeTimeSpan(cashbook.getLastModified());
                lastModifiedText.setText(context.getString(
                        R.string.last_modified_format, lastModified));
                lastModifiedText.setVisibility(View.VISIBLE);
            } else {
                lastModifiedText.setVisibility(View.GONE);
            }

            // Set balance
            double balance = cashbook.getBalance();
            balanceText.setText(currencyFormat.format(balance));
            balanceText.setTextColor(balance >= 0 ? successColor : expenseColor);

            transactionCountText.setText(String.valueOf(cashbook.getTransactionCount()));

            // Set created date
            if (cashbook.getCreatedDate() > 0) {
                createdDateText.setText(DateTimeUtils.formatDate(cashbook.getCreatedDate(), "MMM yyyy"));
            } else {
                createdDateText.setText("-");
            }

            // Set icon color
            iconCard.setCardBackgroundColor(getIconColorForCashbook(cashbook));

            // Listeners
            cashbookItemCard.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCashbookClick(cashbook);
                }
            });

            favoriteButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onFavoriteClick(cashbook);
                }
            });

            menuButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onMenuClick(cashbook, v);
                }
            });
        }

        private int getIconColorForCashbook(CashbookModel cashbook) {
            if (cashbook.isCurrent()) {
                return primaryColor;
            } else if (cashbook.isFavorite()) {
                return favoriteColor;
            } else if (cashbook.isActive()) {
                return successColor;
            } else {
                return secondaryColor;
            }
        }
    }

    /**
     * DiffUtil Callback for efficient list updates
     */
    private static class CashbookDiffCallback extends DiffUtil.Callback {
        private List<CashbookModel> oldList;
        private List<CashbookModel> newList;

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
            return oldList.get(oldItemPosition).getCashbookId()
                    .equals(newList.get(newItemPosition).getCashbookId());
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            CashbookModel oldCashbook = oldList.get(oldItemPosition);
            CashbookModel newCashbook = newList.get(newItemPosition);

            return Objects.equals(oldCashbook.getName(), newCashbook.getName()) &&
                    oldCashbook.getBalance() == newCashbook.getBalance() &&
                    oldCashbook.getTransactionCount() == newCashbook.getTransactionCount() &&
                    oldCashbook.isActive() == newCashbook.isActive() &&
                    oldCashbook.isCurrent() == newCashbook.isCurrent() &&
                    oldCashbook.isFavorite() == newCashbook.isFavorite() &&
                    oldCashbook.getLastModified() == newCashbook.getLastModified();
        }
    }

    // Helper class to resolve theme attributes
    static class ThemeUtil {
        static int getThemeAttrColor(Context context, int attr) {
            TypedValue typedValue = new TypedValue();
            context.getTheme().resolveAttribute(attr, typedValue, true);
            return typedValue.data;
        }
    }
}