package com.phynix.artham.adapters;

import android.content.Context;
import android.graphics.Color;
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

import com.google.android.material.card.MaterialCardView;
import com.phynix.artham.R;
import com.phynix.artham.models.CashbookModel;
import com.phynix.artham.utils.AmountFormatter;
import com.phynix.artham.utils.DateTimeUtils;
import com.phynix.artham.utils.ThemeUtil;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.phynix.artham.utils.ThemeUtil;
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
    private final int successColor;
    private final int secondaryColor;
    private final int favoriteColor;
    private final int expenseColor;

    public CashbookAdapter(Context context, List<CashbookModel> cashbookList, OnCashbookClickListener listener) {
        this.context = context;
        this.cashbookList = new ArrayList<>(cashbookList);
        this.listener = listener;
        this.currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

        this.successColor = ThemeUtil.getThemeAttrColor(context, R.attr.chk_incomeColor);
        this.secondaryColor = ThemeUtil.getThemeAttrColor(context, R.attr.chk_textColorSecondary);
        this.expenseColor = ThemeUtil.getThemeAttrColor(context, R.attr.chk_expenseColor);
        this.favoriteColor = ContextCompat.getColor(context, R.color.category_rent);
    }

    @NonNull
    @Override
    public CashbookViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
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
        CashbookDiffCallback diffCallback = new CashbookDiffCallback(this.cashbookList, newCashbooks);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);

        this.cashbookList.clear();
        this.cashbookList.addAll(newCashbooks);
        diffResult.dispatchUpdatesTo(this);
    }

    public class CashbookViewHolder extends RecyclerView.ViewHolder {
        private MaterialCardView cashbookItemCard; // Updated to MaterialCardView
        private CardView iconCard;
        private ImageView bookIcon;
        private ImageView favoriteButton;
        private ImageView menuButton;

        private TextView cashbookNameText;
        private TextView cashbookDescriptionText;
        private TextView statusBadge;
        private TextView lastModifiedText;
        private TextView categoryBadge;
        private TextView balanceText;
        private TextView transactionCountText;
        private TextView createdDateText;

        public CashbookViewHolder(@NonNull View itemView) {
            super(itemView);
            cashbookItemCard = itemView.findViewById(R.id.cashbookItemCard);
            iconCard = itemView.findViewById(R.id.iconCard);
            bookIcon = itemView.findViewById(R.id.cashbookIcon);
            cashbookNameText = itemView.findViewById(R.id.cashbookName);
            cashbookDescriptionText = itemView.findViewById(R.id.cashbookDescription);
            statusBadge = itemView.findViewById(R.id.activeStatus);
            favoriteButton = itemView.findViewById(R.id.favoriteButton);
            menuButton = itemView.findViewById(R.id.menuButton);
            lastModifiedText = itemView.findViewById(R.id.lastModifiedText);
            balanceText = itemView.findViewById(R.id.balanceAmount);
            categoryBadge = itemView.findViewById(R.id.categoryBadge);
            transactionCountText = itemView.findViewById(R.id.transactionCountText);
            createdDateText = itemView.findViewById(R.id.createdDateText);
        }

        public void bind(CashbookModel cashbook) {
            if (cashbook == null) return;

            cashbookNameText.setText(cashbook.getName() != null ? cashbook.getName() : "Unnamed");

            if (cashbookDescriptionText != null) {
                if (cashbook.getDescription() != null && !cashbook.getDescription().trim().isEmpty()) {
                    cashbookDescriptionText.setText(cashbook.getDescription());
                    cashbookDescriptionText.setVisibility(View.VISIBLE);
                } else {
                    cashbookDescriptionText.setVisibility(View.GONE);
                }
            }

            // Handles assigning "Current" badge dynamically based on the passed ID
            setupStatusBadge(cashbook);

            // Dynamically add a border if the book is the Current Book
            if (cashbookItemCard != null) {
                if (cashbook.isCurrent()) {
                    // Set stroke to 2dp
                    int strokeWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, context.getResources().getDisplayMetrics());
                    cashbookItemCard.setStrokeWidth(strokeWidth);
                    cashbookItemCard.setStrokeColor(ThemeUtil.getThemeAttrColor(context, R.attr.chk_primary_blue));
                } else {
                    // Remove stroke for regular books
                    cashbookItemCard.setStrokeWidth(0);
                }
            }

            setupFavoriteIcon(cashbook);
            setupLastModified(cashbook);

            double balance = cashbook.getBalance();
            AmountFormatter.setAdaptiveAmount(balanceText, balance, 16f, 10f);
            balanceText.setTextColor(balance >= 0 ? successColor : expenseColor);

            transactionCountText.setText(String.valueOf(cashbook.getTransactionCount()));

            if (cashbook.getCreatedDate() > 0) {
                createdDateText.setText(DateTimeUtils.formatDate(cashbook.getCreatedDate(), "MMM yyyy"));
            } else {
                createdDateText.setText("-");
            }

            if (iconCard != null) {
                iconCard.setCardBackgroundColor(getIconBackgroundColor(cashbook));
                if (bookIcon != null) {
                    bookIcon.setColorFilter(Color.WHITE);
                    if (cashbook.getThemeIcon() != null && !cashbook.getThemeIcon().isEmpty()) {
                        int resId = context.getResources().getIdentifier(cashbook.getThemeIcon(), "drawable", context.getPackageName());
                        if (resId != 0) {
                            bookIcon.setImageResource(resId);
                        } else {
                            bookIcon.setImageResource(R.drawable.ic_book);
                        }
                    } else {
                        bookIcon.setImageResource(R.drawable.ic_book);
                    }
                }
            }

            if (categoryBadge != null) {
                String cat = cashbook.getCategory();
                if (cat != null && !cat.trim().isEmpty()) {
                    categoryBadge.setText(cat);
                    categoryBadge.setVisibility(View.VISIBLE);
                    int catColor = getCategoryColor(cat);

                    // Create dynamic outline border drawable
                    android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                    gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                    
                    float density = categoryBadge.getResources().getDisplayMetrics().density;
                    gd.setCornerRadius(6 * density); // 6dp corner radius for premium pill badge
                    gd.setStroke((int)(1 * density), catColor); // 1dp stroke width of dynamic label color
                    
                    // Set background transparent
                    gd.setColor(android.graphics.Color.TRANSPARENT);
                    
                    categoryBadge.setBackground(gd);
                    categoryBadge.setTextColor(catColor); // Set text color to solid dynamic label color
                } else {
                    categoryBadge.setVisibility(View.GONE);
                }
            }

            setupListeners(cashbook);
        }

        private int getCategoryColor(String category) {
            if (category == null || category.trim().isEmpty()) {
                return Color.GRAY;
            }
            int hash = category.hashCode();
            String[] colors = {
                "#3F51B5", "#009688", "#FF9800", "#E91E63", 
                "#9C27B0", "#03A9F4", "#4CAF50", "#FF5722",
                "#607D8B", "#8BC34A", "#00BCD4"
            };
            int index = Math.abs(hash) % colors.length;
            return Color.parseColor(colors[index]);
        }

        private void setupStatusBadge(CashbookModel cashbook) {
            if (statusBadge == null) return;

            statusBadge.setVisibility(View.VISIBLE);

            // Evaluates text and explicit color override
            if (cashbook.isCurrent()) {
                statusBadge.setText("Current");
                statusBadge.setTextColor(ThemeUtil.getThemeAttrColor(context, R.attr.chk_primary_blue));
            } else if (cashbook.isActive()) {
                statusBadge.setText("Active");
                statusBadge.setTextColor(successColor);
            } else {
                statusBadge.setText("Inactive");
                statusBadge.setTextColor(secondaryColor);
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

            long openedAt = cashbook.getLastOpenedAt();
            if (openedAt > 0) {
                String relativeTime = DateTimeUtils.getRelativeTimeSpan(openedAt);
                lastModifiedText.setText("Last opened " + relativeTime);
                lastModifiedText.setVisibility(View.VISIBLE);
            } else if (cashbook.getLastModified() > 0) {
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

        private int getIconBackgroundColor(CashbookModel cashbook) {
            if (cashbook.getThemeColor() != null && !cashbook.getThemeColor().isEmpty()) {
                try {
                    return Color.parseColor(cashbook.getThemeColor());
                } catch (Exception e) {
                    // Fallback
                }
            }
            if (cashbook.isFavorite()) return Color.parseColor("#FFD700");
            if (cashbook.isCurrent()) return ThemeUtil.getThemeAttrColor(context, R.attr.chk_primary_blue);
            if (cashbook.isActive()) return ThemeUtil.getThemeAttrColor(context, R.attr.chk_incomeColor);
            return ThemeUtil.getThemeAttrColor(context, R.attr.chk_dividerHorizontal);
        }
    }

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
            return Objects.equals(oldList.get(oldItemPosition).getCashbookId(), newList.get(newItemPosition).getCashbookId());
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            CashbookModel oldItem = oldList.get(oldItemPosition);
            CashbookModel newItem = newList.get(newItemPosition);

            return Objects.equals(oldItem.getName(), newItem.getName()) &&
                    Objects.equals(oldItem.getDescription(), newItem.getDescription()) &&
                    Objects.equals(oldItem.getCategory(), newItem.getCategory()) &&
                    Math.abs(oldItem.getBalance() - newItem.getBalance()) < 0.01 &&
                    oldItem.getTransactionCount() == newItem.getTransactionCount() &&
                    oldItem.isActive() == newItem.isActive() &&
                    oldItem.isCurrent() == newItem.isCurrent() &&
                    oldItem.isFavorite() == newItem.isFavorite() &&
                    oldItem.getLastModified() == newItem.getLastModified() &&
                    oldItem.getLastOpenedAt() == newItem.getLastOpenedAt();
        }
    }
}