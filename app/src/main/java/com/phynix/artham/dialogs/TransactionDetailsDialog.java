package com.phynix.artham.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.button.MaterialButton;
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
            if (getParentFragment() instanceof TransactionDialogListener) {
                listener = (TransactionDialogListener) getParentFragment();
            } else if (context instanceof TransactionDialogListener) {
                listener = (TransactionDialogListener) context;
            }
        } catch (ClassCastException e) {
            // Listener is optional
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // [FIX] Apply Custom Dialog Theme to ensure colors work and avoid crashes
        setStyle(DialogFragment.STYLE_NORMAL, R.style.Theme_Artham_Dialog);

        if (getArguments() != null) {
            transaction = (TransactionModel) getArguments().getSerializable(ARG_TRANSACTION);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // [FIX] Use local inflater to apply the theme set in onCreate()
        LayoutInflater themeInflater = LayoutInflater.from(requireContext());
        View view = themeInflater.inflate(R.layout.activity_transaction_details, container, false);

        // Make background transparent for rounded corners
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
        // [UI] Set Dialog Width to 90% of screen
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void initializeViews(View view) {
        // --- Header ---
        ImageButton closeButton = view.findViewById(R.id.closeButton);
        closeButton.setOnClickListener(v -> dismiss());

        // --- Hero Section ---
        TextView detailRemark = view.findViewById(R.id.detailRemark);
        TextView detailAmount = view.findViewById(R.id.detailAmount);
        TextView detailTypeChip = view.findViewById(R.id.detailTypeChip);

        TextView detailCategoryName = view.findViewById(R.id.detailCategoryName);
        View iconCard = view.findViewById(R.id.iconCard);

        // 1. Remark Logic
        if (TextUtils.isEmpty(transaction.getRemark())) {
            detailRemark.setText(transaction.getTransactionCategory());
        } else {
            detailRemark.setText(transaction.getRemark());
        }

        // 2. Amount & Type Styling
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        detailAmount.setText(currencyFormat.format(transaction.getAmount()));

        if ("IN".equalsIgnoreCase(transaction.getType())) {
            detailTypeChip.setText("Income");
            int incomeColor = getThemeColor(R.attr.incomeColor);
            detailAmount.setTextColor(incomeColor);
            detailTypeChip.setTextColor(incomeColor);
        } else {
            detailTypeChip.setText("Expense");
            int expenseColor = getThemeColor(R.attr.expenseColor);
            detailAmount.setTextColor(expenseColor);
            detailTypeChip.setTextColor(expenseColor);
        }

        // 3. Category Logic
        detailCategoryName.setText(transaction.getTransactionCategory());
        int categoryColor = CategoryColorUtil.getCategoryColor(requireContext(), transaction.getTransactionCategory());

        // Apply color to Icon Card safely
        if (iconCard instanceof CardView) {
            ((CardView) iconCard).setCardBackgroundColor(categoryColor);
        } else if (iconCard instanceof com.google.android.material.card.MaterialCardView) {
            ((com.google.android.material.card.MaterialCardView) iconCard).setCardBackgroundColor(categoryColor);
        }

        // --- Metadata Section ---
        TextView detailDateTime = view.findViewById(R.id.detailDateTime);
        TextView detailPaymentMode = view.findViewById(R.id.detailPaymentMode);

        TextView detailParty = view.findViewById(R.id.detailParty);
        View detailPartyLayout = view.findViewById(R.id.detailPartyLayout);

        TextView detailTags = view.findViewById(R.id.detailTags);
        View detailTagsLayout = view.findViewById(R.id.detailTagsLayout);

        // Date
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault());
        detailDateTime.setText(dateFormat.format(new Date(transaction.getTimestamp())));

        // Mode
        detailPaymentMode.setText(transaction.getPaymentMode());

        // Party Visibility
        if (TextUtils.isEmpty(transaction.getPartyName())) {
            detailPartyLayout.setVisibility(View.GONE);
        } else {
            detailPartyLayout.setVisibility(View.VISIBLE);
            detailParty.setText(transaction.getPartyName());
        }

        // Tags Visibility
        if (transaction.getTags() == null || transaction.getTags().isEmpty()) {
            detailTagsLayout.setVisibility(View.GONE);
        } else {
            detailTagsLayout.setVisibility(View.VISIBLE);
            detailTags.setText(transaction.getTags().toString().replace("[", "").replace("]", ""));
        }

        // Hide unused layout sections safely
        safeHide(view, R.id.detailTaxLayout);
        safeHide(view, R.id.detailLocationLayout);
        safeHide(view, R.id.attachmentsLabel);
        safeHide(view, R.id.attachmentPreview);

        // --- Action Buttons ---
        MaterialButton btnEdit = view.findViewById(R.id.btnEditTransaction);
        MaterialButton btnDelete = view.findViewById(R.id.btnDeleteTransaction);
        MaterialButton btnShare = view.findViewById(R.id.btnShareTransaction);

        // [OPTIMIZED CLICK LISTENERS]
        // Dismiss dialog immediately, then trigger action after 150ms.
        // This removes the "laggy button" feeling.
        btnEdit.setOnClickListener(v -> {
            dismiss();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (listener != null) listener.onEditTransaction(transaction);
            }, 150);
        });

        btnDelete.setOnClickListener(v -> {
            dismiss();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (listener != null) listener.onDeleteTransaction(transaction);
            }, 150);
        });

        btnShare.setOnClickListener(v -> shareTransactionDetails());
    }

    private void safeHide(View parent, int id) {
        View v = parent.findViewById(id);
        if (v != null) v.setVisibility(View.GONE);
    }

    private int getThemeColor(int attrResId) {
        TypedValue typedValue = new TypedValue();
        if(getContext() != null) {
            getContext().getTheme().resolveAttribute(attrResId, typedValue, true);
            return typedValue.data;
        }
        return Color.BLACK;
    }

    private void shareTransactionDetails() {
        String shareBody = "Transaction Details:\n" +
                "Remark: " + (TextUtils.isEmpty(transaction.getRemark()) ? transaction.getTransactionCategory() : transaction.getRemark()) + "\n" +
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