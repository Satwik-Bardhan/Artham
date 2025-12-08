package com.phynix.artham.dialogs;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.DialogFragment;

import com.phynix.artham.R;
import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.utils.CategoryColorUtil;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TransactionDetailsDialog extends DialogFragment {

    private static final String ARG_TRANSACTION = "transaction";
    private TransactionModel transaction;
    private TransactionDialogListener listener;

    // Interface to communicate actions back to the Activity/Fragment
    public interface TransactionDialogListener {
        void onEditTransaction(TransactionModel transaction);
        void onDeleteTransaction(TransactionModel transaction);
    }

    public static TransactionDetailsDialog newInstance(TransactionModel transaction) {
        TransactionDetailsDialog fragment = new TransactionDetailsDialog();
        Bundle args = new Bundle();
        args.putSerializable(ARG_TRANSACTION, transaction);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            // Check if parent fragment implements listener first, then activity
            if (getParentFragment() instanceof TransactionDialogListener) {
                listener = (TransactionDialogListener) getParentFragment();
            } else if (context instanceof TransactionDialogListener) {
                listener = (TransactionDialogListener) context;
            }
        } catch (ClassCastException e) {
            // It's okay if listener isn't attached for simple viewing, but ideally it should be.
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            transaction = (TransactionModel) getArguments().getSerializable(ARG_TRANSACTION);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.layout_transaction_details_dialog, container, false);

        // Make the dialog background transparent so the CardView corners show nicely
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        }

        initializeViews(view);
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        // Ensure the dialog takes up appropriate width (e.g., 90% of screen)
        if (getDialog() != null && getDialog().getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90);
            getDialog().getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void initializeViews(View view) {
        // --- Header & Close ---
        ImageView closeButton = view.findViewById(R.id.closeButton);
        closeButton.setOnClickListener(v -> dismiss());

        // --- Main Section ---
        TextView detailCategoryName = view.findViewById(R.id.detailCategoryName);
        TextView detailAmount = view.findViewById(R.id.detailAmount);
        TextView detailType = view.findViewById(R.id.detailType);
        ImageView detailCategoryIcon = view.findViewById(R.id.detailCategoryIcon);
        CardView categoryIconCard = view.findViewById(R.id.categoryIconCard);

        // Set Data
        detailCategoryName.setText(transaction.getTransactionCategory());

        // Format Amount
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        String amountText = currencyFormat.format(transaction.getAmount());
        detailAmount.setText(amountText);

        // Type Styling (IN/OUT)
        if ("IN".equals(transaction.getType())) {
            detailType.setText("Income");
            // [FIX] Use getThemeColor to resolve the attribute
            detailAmount.setTextColor(getThemeColor(R.attr.incomeColor));
            detailType.setBackgroundResource(R.drawable.chip_active_background);
        } else {
            detailType.setText("Expense");
            // [FIX] Use getThemeColor to resolve the attribute
            detailAmount.setTextColor(getThemeColor(R.attr.expenseColor));
            detailType.setBackgroundResource(R.drawable.chip_inactive_background);
        }

        // Category Icon & Color
        // [FIX] Correct method name: getCategoryColor(context, categoryName)
        int categoryColor = CategoryColorUtil.getCategoryColor(requireContext(), transaction.getTransactionCategory());
        categoryIconCard.setCardBackgroundColor(categoryColor);

        // --- Details Grid ---
        TextView detailDateTime = view.findViewById(R.id.detailDateTime);
        TextView detailPaymentMode = view.findViewById(R.id.detailPaymentMode);
        TextView detailParty = view.findViewById(R.id.detailParty);
        LinearLayout detailPartyLayout = view.findViewById(R.id.detailPartyLayout);

        LinearLayout detailTaxLayout = view.findViewById(R.id.detailTaxLayout);
        LinearLayout detailTagsLayout = view.findViewById(R.id.detailTagsLayout);
        LinearLayout detailLocationLayout = view.findViewById(R.id.detailLocationLayout);

        TextView detailRemark = view.findViewById(R.id.detailRemark);

        // Date & Time
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault());
        detailDateTime.setText(dateFormat.format(new Date(transaction.getTimestamp())));

        // Payment Mode
        detailPaymentMode.setText(transaction.getPaymentMode());

        // Party (Hide if empty)
        if (TextUtils.isEmpty(transaction.getPartyName())) {
            detailPartyLayout.setVisibility(View.GONE);
        } else {
            detailPartyLayout.setVisibility(View.VISIBLE);
            detailParty.setText(transaction.getPartyName());
        }

        // Hidden fields (Tax, Tags, Location) - Uncomment when you add these to TransactionModel
        detailTaxLayout.setVisibility(View.GONE);
        detailTagsLayout.setVisibility(View.GONE);
        detailLocationLayout.setVisibility(View.GONE);

        // Remark
        if (TextUtils.isEmpty(transaction.getRemark())) {
            detailRemark.setText("No remark added");
            detailRemark.setTextColor(getThemeColor(R.attr.textColorHint));
        } else {
            detailRemark.setText(transaction.getRemark());
            detailRemark.setTextColor(getThemeColor(R.attr.textColorSecondary));
        }

        // --- Action Buttons ---
        Button btnEdit = view.findViewById(R.id.btnEditTransaction);
        Button btnDelete = view.findViewById(R.id.btnDeleteTransaction);
        Button btnShare = view.findViewById(R.id.btnShareTransaction);

        btnEdit.setOnClickListener(v -> {
            if (listener != null) listener.onEditTransaction(transaction);
            dismiss();
        });

        btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDeleteTransaction(transaction);
            dismiss();
        });

        btnShare.setOnClickListener(v -> {
            shareTransactionDetails();
        });
    }

    // [FIX] Helper method to resolve theme attributes like ?attr/incomeColor
    private int getThemeColor(int attrResId) {
        TypedValue typedValue = new TypedValue();
        requireContext().getTheme().resolveAttribute(attrResId, typedValue, true);
        return typedValue.data;
    }

    private void shareTransactionDetails() {
        String shareBody = "Transaction Details:\n" +
                "Category: " + transaction.getTransactionCategory() + "\n" +
                "Amount: " + transaction.getAmount() + "\n" +
                "Type: " + transaction.getType() + "\n" +
                "Date: " + new Date(transaction.getTimestamp()).toString();

        Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
        sharingIntent.setType("text/plain");
        sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Transaction Details");
        sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareBody);
        startActivity(Intent.createChooser(sharingIntent, "Share via"));
    }
}