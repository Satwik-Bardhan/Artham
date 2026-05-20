package com.phynix.artham.utils;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import com.phynix.artham.models.TransactionModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ExcelReportGenerator {

    private static final String TAG = "ExcelReportGenerator";

    public static Uri generateReport(Context context, List<TransactionModel> transactions,
                                      String cashbookName, long startDate, long endDate) {
        String sanitizedBookName = cashbookName != null ? cashbookName.replaceAll("[\\\\/:*?\"<>|\\s]+", "_") : "Report";
        if (sanitizedBookName.trim().isEmpty()) {
            sanitizedBookName = "Report";
        }
        String fileName = sanitizedBookName + "_Report_" + System.currentTimeMillis() + ".csv";

        OutputStream outputStream = null;
        Uri uri = null;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Artham");
                uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    outputStream = context.getContentResolver().openOutputStream(uri);
                }
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Artham");
                if (!dir.exists()) dir.mkdirs();
                File file = new File(dir, fileName);
                outputStream = new FileOutputStream(file);
                uri = androidx.core.content.FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
            }

            if (outputStream == null) {
                Toast.makeText(context, "Failed to create file", Toast.LENGTH_SHORT).show();
                return null;
            }

            // Sort transactions chronologically
            Collections.sort(transactions, Comparator.comparingLong(TransactionModel::getTimestamp));

            PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, "UTF-8"));

            // Write BOM for Excel UTF-8 compatibility
            outputStream.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

            SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
            SimpleDateFormat dateTimeFormat = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
            SimpleDateFormat rowDateFormat = new SimpleDateFormat("dd MMM yy", Locale.getDefault());

            // ── Header Section ──
            writer.println("Artham Report");
            writer.println("Cashbook:," + escapeCsv(cashbookName));
            writer.println("Duration:," + dateFormat.format(new Date(startDate)) + " - " + dateFormat.format(new Date(endDate)));
            writer.println("Generated On:," + dateTimeFormat.format(new Date()));
            writer.println(); // blank row separator

            // ── Column Headers ──
            writer.println("Date,Category,Party,Remark,Payment Mode,Cash In,Cash Out,Balance");

            // ── Transaction Rows ──
            double runningBalance = 0;
            double totalIn = 0;
            double totalOut = 0;

            for (TransactionModel t : transactions) {
                boolean isIncome = "IN".equalsIgnoreCase(t.getType());

                if (isIncome) {
                    runningBalance += t.getAmount();
                    totalIn += t.getAmount();
                } else {
                    runningBalance -= t.getAmount();
                    totalOut += t.getAmount();
                }

                String date = rowDateFormat.format(new Date(t.getTimestamp()));
                String category = t.getTransactionCategory() != null ? t.getTransactionCategory() : "";
                String party = t.getPartyName() != null ? t.getPartyName() : "";
                String remark = t.getRemark() != null ? t.getRemark() : "";
                String mode = t.getPaymentMode() != null ? t.getPaymentMode() : "";
                String cashIn = isIncome ? formatAmount(t.getAmount()) : "";
                String cashOut = !isIncome ? formatAmount(t.getAmount()) : "";
                String balance = formatAmount(runningBalance);

                writer.println(
                        escapeCsv(date) + "," +
                        escapeCsv(category) + "," +
                        escapeCsv(party) + "," +
                        escapeCsv(remark) + "," +
                        escapeCsv(mode) + "," +
                        cashIn + "," +
                        cashOut + "," +
                        balance
                );
            }

            // ── Summary Footer ──
            writer.println(); // blank row separator
            writer.println("Grand Total,,,,," +
                    formatAmount(totalIn) + "," +
                    formatAmount(totalOut) + "," +
                    formatAmount(totalIn - totalOut));
            writer.println();
            writer.println("Total No. of entries:," + transactions.size());

            writer.flush();
            writer.close();
            outputStream.close();

            Toast.makeText(context, "Excel (CSV) Saved to Downloads/Artham", Toast.LENGTH_LONG).show();
            return uri;

        } catch (Exception e) {
            Log.e(TAG, "Error creating CSV report", e);
            Toast.makeText(context, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    /**
     * Escapes a CSV field value. If the value contains commas, quotes, or newlines,
     * it is wrapped in double quotes with internal quotes escaped.
     */
    private static String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String formatAmount(double amount) {
        return String.format(Locale.getDefault(), "%.2f", amount);
    }
}
