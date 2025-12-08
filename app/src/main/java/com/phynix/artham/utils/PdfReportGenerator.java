package com.phynix.artham.utils;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfPageEventHelper;
import com.itextpdf.text.pdf.PdfWriter;
import com.phynix.artham.models.TransactionModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PdfReportGenerator {

    private static final String TAG = "PdfReportGenerator";

    // Colors
    private static final BaseColor MODE_TEAL = new BaseColor(1, 136, 159); // #01889F
    private static final BaseColor TEXT_BLACK = BaseColor.BLACK;
    private static final BaseColor TEXT_GRAY = BaseColor.GRAY;
    private static final BaseColor COLOR_GREEN = new BaseColor(76, 175, 80);
    private static final BaseColor COLOR_RED = new BaseColor(244, 67, 54);
    private static final BaseColor HEADER_GRAY = new BaseColor(240, 240, 240);

    // Fonts
    private static final Font fontTitle = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD, TEXT_BLACK);
    private static final Font fontSubtitle = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD, TEXT_BLACK);
    private static final Font fontHeader = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, TEXT_BLACK);
    private static final Font fontNormal = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL, TEXT_BLACK);
    private static final Font fontMode = new Font(Font.FontFamily.HELVETICA, 9, Font.NORMAL, MODE_TEAL);
    private static final Font fontFooter = new Font(Font.FontFamily.HELVETICA, 8, Font.NORMAL, TEXT_GRAY);

    public static void generateReport(Context context, List<TransactionModel> transactions, long startDate, long endDate) {
        Document document = new Document(PageSize.A4, 36, 36, 36, 50);
        String fileName = "Artham_Report_" + System.currentTimeMillis() + ".pdf";

        OutputStream outputStream = null;
        Uri uri = null;

        try {
            // Save to Downloads/Artham folder logic
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
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
            }

            if (outputStream == null) {
                Toast.makeText(context, "Failed to create file", Toast.LENGTH_SHORT).show();
                return;
            }

            PdfWriter writer = PdfWriter.getInstance(document, outputStream);
            writer.setPageEvent(new FooterEvent());

            document.open();

            // 1. Calculate Balances Chronologically (Oldest -> Newest)
            Collections.sort(transactions, Comparator.comparingLong(TransactionModel::getTimestamp));

            Map<String, Double> balanceMap = new HashMap<>();
            double runningBalance = 0;
            double totalIn = 0;
            double totalOut = 0;

            for (TransactionModel t : transactions) {
                if ("IN".equalsIgnoreCase(t.getType())) {
                    runningBalance += t.getAmount();
                    totalIn += t.getAmount();
                } else {
                    runningBalance -= t.getAmount();
                    totalOut += t.getAmount();
                }
                balanceMap.put(t.getTransactionId(), runningBalance);
            }
            double finalBalance = totalIn - totalOut;

            // 2. Sort Descending for Display (Newest -> Oldest)
            Collections.reverse(transactions);

            // 3. Add Header Section
            addHeaderSection(document, startDate, endDate, totalIn, totalOut, finalBalance, transactions.size());

            document.add(new Paragraph(" "));
            document.add(new Paragraph(" "));

            // 4. Add Transaction Table
            addTransactionTable(document, transactions, balanceMap);

            document.close();
            outputStream.close();
            Toast.makeText(context, "PDF Saved to Downloads/Artham", Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Log.e(TAG, "Error creating PDF", e);
            Toast.makeText(context, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private static void addHeaderSection(Document document, long startDate, long endDate, double totalIn, double totalOut, double finalBalance, int count) throws Exception {
        PdfPTable masterTable = new PdfPTable(2);
        masterTable.setWidthPercentage(100);
        masterTable.setWidths(new float[]{1, 1}); // Split 50% - 50%

        // --- LEFT: Summary Table ---
        PdfPTable summaryTable = new PdfPTable(3);
        summaryTable.setWidthPercentage(100);

        // Headers
        addCell(summaryTable, "Total Cash In", fontHeader, Element.ALIGN_LEFT, HEADER_GRAY);
        addCell(summaryTable, "Total Cash Out", fontHeader, Element.ALIGN_LEFT, HEADER_GRAY);
        addCell(summaryTable, "Final Balance", fontHeader, Element.ALIGN_LEFT, HEADER_GRAY);

        // Values
        addCell(summaryTable, formatCurrency(totalIn), fontNormal, Element.ALIGN_LEFT, BaseColor.WHITE);
        addCell(summaryTable, formatCurrency(totalOut), fontNormal, Element.ALIGN_LEFT, BaseColor.WHITE);
        addCell(summaryTable, formatCurrency(finalBalance), fontNormal, Element.ALIGN_LEFT, BaseColor.WHITE);

        PdfPCell leftCell = new PdfPCell(summaryTable);
        leftCell.setBorder(PdfPCell.NO_BORDER);
        leftCell.setVerticalAlignment(Element.ALIGN_TOP);
        masterTable.addCell(leftCell);

        // --- RIGHT: Details Section (Center Aligned) ---
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(PdfPCell.NO_BORDER);
        rightCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        rightCell.setVerticalAlignment(Element.ALIGN_TOP);

        Paragraph pTitle = new Paragraph("Artham Report", fontTitle);
        pTitle.setAlignment(Element.ALIGN_CENTER);
        rightCell.addElement(pTitle);

        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM", Locale.getDefault());
        Paragraph pSubTitle = new Paragraph(monthFormat.format(new Date(startDate)) + " Expenses", fontSubtitle);
        pSubTitle.setAlignment(Element.ALIGN_CENTER);
        pSubTitle.setSpacingAfter(8);
        rightCell.addElement(pSubTitle);

        SimpleDateFormat genFormat = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
        Paragraph pGen = new Paragraph("Generated On: " + genFormat.format(new Date()), fontNormal);
        pGen.setAlignment(Element.ALIGN_CENTER);
        rightCell.addElement(pGen);

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        String duration = "Duration: " + dateFormat.format(new Date(startDate)) + " - " + dateFormat.format(new Date(endDate));
        Paragraph pDuration = new Paragraph(duration, fontNormal);
        pDuration.setAlignment(Element.ALIGN_CENTER);
        rightCell.addElement(pDuration);

        Paragraph pCount = new Paragraph("Total No. of entries: " + count, fontNormal);
        pCount.setAlignment(Element.ALIGN_CENTER);
        rightCell.addElement(pCount);

        masterTable.addCell(rightCell);
        document.add(masterTable);
    }

    private static void addTransactionTable(Document document, List<TransactionModel> transactions, Map<String, Double> balanceMap) throws Exception {
        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2, 3, 2, 2, 2, 2});

        // Headers
        String[] headers = {"Date", "Remark", "Mode", "Cash In", "Cash Out", "Balance"};
        for (String h : headers) {
            int align = h.equals("Mode") ? Element.ALIGN_CENTER : Element.ALIGN_LEFT;
            addCell(table, h, fontHeader, align, HEADER_GRAY);
        }

        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yy", Locale.getDefault());

        for (TransactionModel t : transactions) {
            // Date
            addCell(table, sdf.format(new Date(t.getTimestamp())), fontNormal, Element.ALIGN_LEFT, BaseColor.WHITE);

            // Remark
            String remark = (t.getRemark() != null && !t.getRemark().isEmpty()) ? t.getRemark() : t.getTransactionCategory();
            addCell(table, remark, fontNormal, Element.ALIGN_LEFT, BaseColor.WHITE);

            // Mode (Teal, Center)
            addCell(table, t.getPaymentMode(), fontMode, Element.ALIGN_CENTER, BaseColor.WHITE);

            // Cash In / Out (Left, Colored)
            if ("IN".equalsIgnoreCase(t.getType())) {
                addColoredCell(table, formatCurrency(t.getAmount()), COLOR_GREEN, Element.ALIGN_LEFT);
                addCell(table, "", fontNormal, Element.ALIGN_LEFT, BaseColor.WHITE);
            } else {
                addCell(table, "", fontNormal, Element.ALIGN_LEFT, BaseColor.WHITE);
                addColoredCell(table, formatCurrency(t.getAmount()), COLOR_RED, Element.ALIGN_LEFT);
            }

            // Balance
            Double bal = balanceMap.get(t.getTransactionId());
            if (bal == null) bal = 0.0;
            BaseColor balColor = bal >= 0 ? COLOR_GREEN : COLOR_RED;
            Font balFont = new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD, balColor);

            PdfPCell balCell = new PdfPCell(new Phrase(formatCurrency(bal), balFont));
            balCell.setPadding(5);
            balCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            balCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            table.addCell(balCell);
        }

        document.add(table);
    }

    private static void addCell(PdfPTable table, String text, Font font, int alignment, BaseColor bgColor) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(5);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBackgroundColor(bgColor);
        table.addCell(cell);
    }

    private static void addColoredCell(PdfPTable table, String text, BaseColor color, int alignment) {
        Font font = new Font(Font.FontFamily.HELVETICA, 9, Font.NORMAL, color);
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(5);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(cell);
    }

    private static String formatCurrency(double amount) {
        return String.format(Locale.getDefault(), "%.0f", amount);
    }

    static class FooterEvent extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            Phrase footer = new Phrase("Page " + writer.getPageNumber() + " | Generated by Artham", fontFooter);
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, footer,
                    (document.right() - document.left()) / 2 + document.leftMargin(),
                    document.bottom() - 10, 0);
        }
    }
}