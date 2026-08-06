package com.phynix.artham.utils;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfPageEventHelper;
import com.itextpdf.text.pdf.PdfWriter;
import com.phynix.artham.R;
import com.phynix.artham.models.TransactionModel;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PdfReportGenerator {

    private static final String TAG = "PdfReportGenerator";

    // Colors
    private static final BaseColor MODE_TEAL = new BaseColor(1, 136, 159);
    private static final BaseColor TEXT_BLACK = BaseColor.BLACK;
    private static final BaseColor TEXT_GRAY = BaseColor.GRAY;
    private static final BaseColor COLOR_GREEN = new BaseColor(76, 175, 80);
    private static final BaseColor COLOR_RED = new BaseColor(244, 67, 54);
    private static final BaseColor HEADER_GRAY = new BaseColor(240, 240, 240);
    private static final BaseColor TOTAL_ROW_BG = new BaseColor(230, 230, 230);

    // Greyscale equivalents
    private static final BaseColor GS_DARK = new BaseColor(50, 50, 50);
    private static final BaseColor GS_MID = new BaseColor(100, 100, 100);
    private static final BaseColor GS_LIGHT = new BaseColor(160, 160, 160);

    // Fonts
    private static final Font fontTitle = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD, TEXT_BLACK);
    private static final Font fontBookName = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD, BaseColor.DARK_GRAY);
    private static final Font fontHeader = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, TEXT_BLACK);
    private static final Font fontNormal = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL, TEXT_BLACK);
    private static final Font fontMode = new Font(Font.FontFamily.HELVETICA, 8, Font.NORMAL, MODE_TEAL);
    private static final Font fontModeGS = new Font(Font.FontFamily.HELVETICA, 8, Font.NORMAL, GS_MID);
    private static final Font fontFooter = new Font(Font.FontFamily.HELVETICA, 8, Font.NORMAL, TEXT_GRAY);
    private static final Font fontTotal = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, TEXT_BLACK);

    // Backward-compatible overload (used by CashbookSwitchActivity etc.)
    public static Uri generateReport(Context context, List<TransactionModel> transactions, String cashbookName, long startDate, long endDate) {
        return generateReport(context, transactions, cashbookName, startDate, endDate, false, false);
    }

    public static Uri generateReport(Context context, List<TransactionModel> transactions, String cashbookName, long startDate, long endDate, boolean greyscale) {
        return generateReport(context, transactions, cashbookName, startDate, endDate, greyscale, false);
    }

    public static Uri generateReport(Context context, List<TransactionModel> transactions, String cashbookName, long startDate, long endDate, boolean greyscale, boolean categoryReport) {
        Document document = new Document(PageSize.A4, 36, 36, 36, 50);
        String sanitizedBookName = cashbookName != null ? cashbookName.replaceAll("[\\\\/:*?\"<>|\\s]+", "_") : "Report";
        if (sanitizedBookName.trim().isEmpty()) {
            sanitizedBookName = "Report";
        }
        String fileName = sanitizedBookName + "_Report_" + System.currentTimeMillis() + ".pdf";

        OutputStream outputStream = null;
        Uri uri = null;

        try {
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
                uri = androidx.core.content.FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
            }

            if (outputStream == null) {
                Toast.makeText(context, "Failed to create file", Toast.LENGTH_SHORT).show();
                return null;
            }

            PdfWriter writer = PdfWriter.getInstance(document, outputStream);
            writer.setPageEvent(new FooterEvent());

            document.open();

            // 1. Sort Ascending
            Collections.sort(transactions, Comparator.comparingLong(TransactionModel::getTimestamp));

            // 2. Pre-calculate Totals
            double totalIn = 0;
            double totalOut = 0;
            for (TransactionModel t : transactions) {
                if ("IN".equalsIgnoreCase(t.getType())) totalIn += t.getAmount();
                else totalOut += t.getAmount();
            }
            double finalBalance = totalIn - totalOut;

            // 3. Add Header
            addHeader(document, context, cashbookName, startDate, endDate);

            document.add(new Paragraph(" "));

            // 4. Add Transaction Table
            addTransactionTable(document, transactions, greyscale);

            document.add(new Paragraph(" "));

            // 5. Add Detached Summary Table
            addSummaryTable(document, totalIn, totalOut, finalBalance, greyscale);

            document.add(new Paragraph(" "));

            // 6. Add Total Count at the very bottom
            addTotalCount(document, transactions.size());

            if (categoryReport) {
                document.add(new Paragraph(" "));
                addCategoryReportTable(document, transactions, greyscale);
            }

            document.close();
            outputStream.close();
            Toast.makeText(context, "PDF Saved to Downloads/Artham", Toast.LENGTH_LONG).show();
            return uri;

        } catch (Exception e) {
            Log.e(TAG, "Error creating PDF", e);
            Toast.makeText(context, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private static void addHeader(Document document, Context context, String cashbookName, long startDate, long endDate) throws Exception {
        // Logo
        try {
            Drawable d = ContextCompat.getDrawable(context, R.drawable.logo);
            if (d != null) {
                Bitmap bitmap = ((BitmapDrawable) d).getBitmap();
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
                Image img = Image.getInstance(stream.toByteArray());
                img.scaleToFit(50, 50);
                img.setAlignment(Element.ALIGN_CENTER);
                document.add(img);
            }
        } catch (Exception e) { Log.e(TAG, "Logo error", e); }

        Paragraph pTitle = new Paragraph("Artham Report", fontTitle);
        pTitle.setAlignment(Element.ALIGN_CENTER);
        pTitle.setSpacingBefore(5);
        document.add(pTitle);

        Paragraph pBook = new Paragraph(cashbookName, fontBookName);
        pBook.setAlignment(Element.ALIGN_CENTER);
        pBook.setSpacingAfter(2);
        document.add(pBook);

        SimpleDateFormat genFormat = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
        Paragraph pGen = new Paragraph("Generated On: " + genFormat.format(new Date()), fontNormal);
        pGen.setAlignment(Element.ALIGN_CENTER);
        document.add(pGen);

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        String duration = "Duration: " + dateFormat.format(new Date(startDate)) + " - " + dateFormat.format(new Date(endDate));
        Paragraph pDuration = new Paragraph(duration, fontNormal);
        pDuration.setAlignment(Element.ALIGN_CENTER);
        document.add(pDuration);
    }

    private static void addTransactionTable(Document document, List<TransactionModel> transactions, boolean greyscale) throws Exception {
        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2, 3, 2, 2, 2, 2});
        table.setHeaderRows(1);

        String[] headers = {"Date", "Remark", "Mode", "Cash In", "Cash Out", "Balance"};
        for (String h : headers) {
            int align = (h.equals("Mode")) ? Element.ALIGN_CENTER :
                    (h.equals("Cash In") || h.equals("Cash Out") || h.equals("Balance")) ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT;
            addCell(table, h, fontHeader, align, HEADER_GRAY);
        }

        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yy", Locale.getDefault());
        double runningBalance = 0;

        BaseColor inColor = greyscale ? GS_DARK : COLOR_GREEN;
        BaseColor outColor = greyscale ? GS_MID : COLOR_RED;
        Font modeFont = greyscale ? fontModeGS : fontMode;

        for (TransactionModel t : transactions) {
            if ("IN".equalsIgnoreCase(t.getType())) runningBalance += t.getAmount();
            else runningBalance -= t.getAmount();

            addCell(table, sdf.format(new Date(t.getTimestamp())), fontNormal, Element.ALIGN_LEFT, BaseColor.WHITE);

            String remark = (t.getRemark() != null && !t.getRemark().isEmpty()) ? t.getRemark() : t.getTransactionCategory();
            addCell(table, remark, fontNormal, Element.ALIGN_LEFT, BaseColor.WHITE);

            addCell(table, t.getPaymentMode(), modeFont, Element.ALIGN_CENTER, BaseColor.WHITE);

            if ("IN".equalsIgnoreCase(t.getType())) {
                addColoredCell(table, formatCurrency(t.getAmount()), inColor, Element.ALIGN_RIGHT, BaseColor.WHITE);
                addCell(table, "", fontNormal, Element.ALIGN_RIGHT, BaseColor.WHITE);
            } else {
                addCell(table, "", fontNormal, Element.ALIGN_RIGHT, BaseColor.WHITE);
                addColoredCell(table, formatCurrency(t.getAmount()), outColor, Element.ALIGN_RIGHT, BaseColor.WHITE);
            }

            BaseColor balColor = greyscale ? GS_DARK : (runningBalance >= 0 ? COLOR_GREEN : COLOR_RED);
            Font balFont = new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD, balColor);
            PdfPCell balCell = new PdfPCell(new Phrase(formatCurrency(runningBalance), balFont));
            balCell.setPadding(6);
            balCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            balCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            table.addCell(balCell);
        }
        document.add(table);
    }

    private static void addSummaryTable(Document document, double totalIn, double totalOut, double finalBalance, boolean greyscale) throws Exception {
        // Detached table with same column structure
        PdfPTable summaryTable = new PdfPTable(6);
        summaryTable.setWidthPercentage(100);
        summaryTable.setWidths(new float[]{2, 3, 2, 2, 2, 2});

        // "Grand Total" Label (Spans 3 Columns)
        PdfPCell labelCell = new PdfPCell(new Phrase("Grand Total", fontTotal));
        labelCell.setColspan(3);
        labelCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        labelCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        labelCell.setBackgroundColor(TOTAL_ROW_BG);
        labelCell.setPadding(8);
        summaryTable.addCell(labelCell);

        // Values
        addColoredCell(summaryTable, formatCurrency(totalIn), TEXT_BLACK, Element.ALIGN_RIGHT, TOTAL_ROW_BG, true);
        addColoredCell(summaryTable, formatCurrency(totalOut), TEXT_BLACK, Element.ALIGN_RIGHT, TOTAL_ROW_BG, true);

        BaseColor finalBalColor = greyscale ? GS_DARK : (finalBalance >= 0 ? COLOR_GREEN : COLOR_RED);
        Font finalBalFont = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, finalBalColor);
        PdfPCell finalBalCell = new PdfPCell(new Phrase(formatCurrency(finalBalance), finalBalFont));
        finalBalCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        finalBalCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        finalBalCell.setBackgroundColor(TOTAL_ROW_BG);
        finalBalCell.setPadding(8);
        summaryTable.addCell(finalBalCell);

        document.add(summaryTable);
    }

    private static void addTotalCount(Document document, int count) throws Exception {
        Paragraph pCount = new Paragraph("Total No. of entries: " + count, fontNormal);
        pCount.setAlignment(Element.ALIGN_CENTER);
        document.add(pCount);
    }

    private static void addCell(PdfPTable table, String text, Font font, int alignment, BaseColor bgColor) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(6);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBackgroundColor(bgColor);
        table.addCell(cell);
    }

    private static void addColoredCell(PdfPTable table, String text, BaseColor textColor, int alignment, BaseColor bgColor) {
        addColoredCell(table, text, textColor, alignment, bgColor, false);
    }

    private static void addColoredCell(PdfPTable table, String text, BaseColor textColor, int alignment, BaseColor bgColor, boolean isBold) {
        Font font = isBold ? new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, textColor)
                : new Font(Font.FontFamily.HELVETICA, 9, Font.NORMAL, textColor);
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(6);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBackgroundColor(bgColor);
        table.addCell(cell);
    }

    private static String formatCurrency(double amount) {
        return String.format(Locale.getDefault(), "%.2f", amount);
    }

    private static void addCategoryReportTable(Document document, List<TransactionModel> transactions, boolean greyscale) throws Exception {
        java.util.Map<String, CategorySummary> map = new java.util.TreeMap<>();
        for (TransactionModel t : transactions) {
            String category = t.getTransactionCategory();
            if (category == null || category.trim().isEmpty()) {
                category = "Uncategorized";
            }
            CategorySummary summary = map.get(category);
            if (summary == null) {
                summary = new CategorySummary();
                map.put(category, summary);
            }
            if ("IN".equalsIgnoreCase(t.getType())) {
                summary.totalIn += t.getAmount();
            } else {
                summary.totalOut += t.getAmount();
            }
        }

        Paragraph pTitle = new Paragraph("Category-Wise Summary", fontBookName);
        pTitle.setAlignment(Element.ALIGN_CENTER);
        pTitle.setSpacingBefore(15);
        pTitle.setSpacingAfter(8);
        document.add(pTitle);
        document.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{4, 2, 2, 2});
        table.setHeaderRows(1);

        String[] headers = {"Category", "Total Cash In", "Total Cash Out", "Net Balance"};
        for (String h : headers) {
            int align = (h.equals("Category")) ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT;
            addCell(table, h, fontHeader, align, HEADER_GRAY);
        }

        BaseColor inColor = greyscale ? GS_DARK : COLOR_GREEN;
        BaseColor outColor = greyscale ? GS_MID : COLOR_RED;

        for (java.util.Map.Entry<String, CategorySummary> entry : map.entrySet()) {
            CategorySummary sum = entry.getValue();
            double net = sum.totalIn - sum.totalOut;

            addCell(table, entry.getKey(), fontNormal, Element.ALIGN_LEFT, BaseColor.WHITE);

            String inStr = sum.totalIn > 0 ? formatCurrency(sum.totalIn) : "-";
            addColoredCell(table, inStr, TEXT_BLACK, Element.ALIGN_RIGHT, BaseColor.WHITE);

            String outStr = sum.totalOut > 0 ? formatCurrency(sum.totalOut) : "-";
            addColoredCell(table, outStr, TEXT_BLACK, Element.ALIGN_RIGHT, BaseColor.WHITE);

            BaseColor netColor = greyscale ? GS_DARK : (net >= 0 ? COLOR_GREEN : COLOR_RED);
            addColoredCell(table, formatCurrency(net), netColor, Element.ALIGN_RIGHT, BaseColor.WHITE, true);
        }

        document.add(table);
    }

    private static class CategorySummary {
        double totalIn = 0;
        double totalOut = 0;
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