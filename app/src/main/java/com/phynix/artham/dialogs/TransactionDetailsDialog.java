package com.phynix.artham.dialogs;

import android.app.Dialog;
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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
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
            // Optional listener
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
        // [FIX] Ensure the XML file name matches this: layout_transaction_details_dialog
        View view = inflater.inflate(R.layout.layout_transaction_details_dialog, container, false);

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
        Chip detailTypeChip = view.findViewById(R.id.detailTypeChip);

        TextView detailCategoryName = view.findViewById(R.id.detailCategoryName);
        View iconCard = view.findViewById(R.id.iconCard);
        ImageView detailCategoryIcon = view.findViewById(R.id.detailCategoryIcon);

        // 1. Remark
        if (TextUtils.isEmpty(transaction.getRemark())) {
            detailRemark.setText(transaction.getTransactionCategory());
        } else {
            detailRemark.setText(transaction.getRemark());
        }

        // 2. Amount & Type
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        detailAmount.setText(currencyFormat.format(transaction.getAmount()));

        if ("IN".equalsIgnoreCase(transaction.getType())) {
            detailTypeChip.setText("Income");
            int incomeColor = getThemeColor(R.attr.incomeColor);
            detailAmount.setTextColor(incomeColor);
            detailTypeChip.setTextColor(incomeColor);
        } else {
            detailTypeChip.setText("Expense");
            // [FIX] Changed colorError to expenseColor based on your previous logs
            int expenseColor = getThemeColor(R.attr.expenseColor);
            detailAmount.setTextColor(expenseColor);
            detailTypeChip.setTextColor(expenseColor);
        }

        // 3. Category
        detailCategoryName.setText(transaction.getTransactionCategory());
        int categoryColor = CategoryColorUtil.getCategoryColor(requireContext(), transaction.getTransactionCategory());
        if (iconCard instanceof CardView) {
            ((CardView) iconCard).setCardBackgroundColor(categoryColor);
        }

        // --- Details Grid ---
        TextView detailDateTime = view.findViewById(R.id.detailDateTime);
        TextView detailPaymentMode = view.findViewById(R.id.detailPaymentMode);

        TextView detailParty = view.findViewById(R.id.detailParty);
        View detailPartyLayout = view.findViewById(R.id.detailPartyLayout); // Now exists in XML

        TextView detailTags = view.findViewById(R.id.detailTags);
        View detailTagsLayout = view.findViewById(R.id.detailTagsLayout); // Now exists in XML

        // Date
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault());
        detailDateTime.setText(dateFormat.format(new Date(transaction.getTimestamp())));

        // Mode
        detailPaymentMode.setText(transaction.getPaymentMode());

        // Party
        if (TextUtils.isEmpty(transaction.getPartyName())) {
            detailPartyLayout.setVisibility(View.GONE);
        } else {
            detailPartyLayout.setVisibility(View.VISIBLE);
            detailParty.setText(transaction.getPartyName());
        }

        // Tags
        if (transaction.getTags() == null || transaction.getTags().isEmpty()) {
            detailTagsLayout.setVisibility(View.GONE);
        } else {
            detailTagsLayout.setVisibility(View.VISIBLE);
            detailTags.setText(transaction.getTags().toString().replace("[", "").replace("]", ""));
        }

        // [FIX] Hide unused fields safely
        View taxLayout = view.findViewById(R.id.detailTaxLayout);
        if(taxLayout != null) taxLayout.setVisibility(View.GONE);

        View locLayout = view.findViewById(R.id.detailLocationLayout);
        if(locLayout != null) locLayout.setVisibility(View.GONE);

        View attachLabel = view.findViewById(R.id.attachmentsLabel);
        if(attachLabel != null) attachLabel.setVisibility(View.GONE);

        View attachPreview = view.findViewById(R.id.attachmentPreview);
        if(attachPreview != null) attachPreview.setVisibility(View.GONE);

        // --- Actions ---
        MaterialButton btnEdit = view.findViewById(R.id.btnEditTransaction);
        MaterialButton btnDelete = view.findViewById(R.id.btnDeleteTransaction);
        MaterialButton btnShare = view.findViewById(R.id.btnShareTransaction);

        btnEdit.setOnClickListener(v -> {
            if (listener != null) listener.onEditTransaction(transaction);
            dismiss();
        });

        btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDeleteTransaction(transaction);
            dismiss();
        });

        btnShare.setOnClickListener(v -> shareTransactionDetails());
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