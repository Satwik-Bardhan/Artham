package com.satvik.artham.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfPageEventHelper;
import com.itextpdf.text.pdf.PdfWriter;
import com.satvik.artham.R;
import com.satvik.artham.TransactionModel;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PdfReportGenerator {

    private static final String TAG = "PdfReportGenerator";

    // Custom Colors
    private static final BaseColor MODE_BLUE_COLOR = new BaseColor(94, 197, 232);
    private static final BaseColor HEADER_GRAY = new BaseColor(240, 240, 240);

    // Fonts
    private static Font titleFont = new Font(Font.FontFamily.HELVETICA, 22, Font.BOLD);
    private static Font bookNameFont = new Font(Font.FontFamily.HELVETICA, 16, Font.BOLD, BaseColor.DARK_GRAY);
    private static Font subTitleFont = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL, BaseColor.GRAY);
    private static Font headerFont = new Font(Font.FontFamily.HELVETICA, 12, Font.NORMAL, BaseColor.BLACK);
    private static Font normalFont = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL);
    private static Font footerFont = new Font(Font.FontFamily.HELVETICA, 8, Font.NORMAL, BaseColor.GRAY);
    private static Font tableHeaderFont = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, BaseColor.BLACK);
    private static Font modeFont = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL, MODE_BLUE_COLOR);

    public static void generateReport(Context context, List<TransactionModel> transactions, String cashbookName, long startDate, long endDate) {
        Document document = new Document(PageSize.A4, 36, 36, 36, 50); // Increased bottom margin for footer
        String fileName = "Artham_Report_" + System.currentTimeMillis() + ".pdf";
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName);

        try {
            PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(file));

            // [FIX] Register the Footer Event to draw page numbers
            FooterEvent event = new FooterEvent();
            writer.setPageEvent(event);

            document.open();

            // 1. Add Header (Logo, Artham Title, Book Name)
            addHeader(document, context, cashbookName, startDate, endDate);

            // 2. Add Summary Box
            addSummary(document, transactions);

            // 3. Add Transaction Table
            addTransactionTable(document, transactions);

            document.close();
            Toast.makeText(context, "PDF Saved to Downloads: " + fileName, Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Log.e(TAG, "Error creating PDF", e);
            Toast.makeText(context, "Error creating PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private static void addHeader(Document document, Context context, String cashbookName, long startDate, long endDate) throws Exception {
        // --- LOGO ---
        Drawable d = ContextCompat.getDrawable(context, R.drawable.logo);
        if (d != null) {
            Bitmap bitmap = ((BitmapDrawable) d).getBitmap();
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            Image img = Image.getInstance(stream.toByteArray());
            img.scaleToFit(60, 60);
            img.setAlignment(Element.ALIGN_CENTER);
            document.add(img);
        }

        // --- MAIN TITLE ("Artham Report") ---
        Paragraph mainTitle = new Paragraph("Artham Report", titleFont);
        mainTitle.setAlignment(Element.ALIGN_CENTER);
        mainTitle.setSpacingBefore(10);
        document.add(mainTitle);

        // --- BOOK NAME (Subtitle) ---
        // [FIX] Added Book Name explicitly below the title
        Paragraph bookTitle = new Paragraph(cashbookName, bookNameFont);
        bookTitle.setAlignment(Element.ALIGN_CENTER);
        bookTitle.setSpacingAfter(5);
        document.add(bookTitle);

        // --- GENERATED ON ---
        SimpleDateFormat genSdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
        String generatedText = "Generated On: " + genSdf.format(new Date());
        Paragraph generatedInfo = new Paragraph(generatedText, subTitleFont);
        generatedInfo.setAlignment(Element.ALIGN_CENTER);
        generatedInfo.setSpacingAfter(5);
        document.add(generatedInfo);

        // --- DURATION ---
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        String dateRange = "Duration: " + sdf.format(new Date(startDate)) + " - " + sdf.format(new Date(endDate));
        Paragraph duration = new Paragraph(dateRange, headerFont);
        duration.setAlignment(Element.ALIGN_CENTER);
        duration.setSpacingAfter(20);
        document.add(duration);
    }

    private static void addSummary(Document document, List<TransactionModel> transactions) throws DocumentException {
        double totalIn = 0;
        double totalOut = 0;

        for (TransactionModel t : transactions) {
            if ("IN".equalsIgnoreCase(t.getType())) {
                totalIn += t.getAmount();
            } else {
                totalOut += t.getAmount();
            }
        }
        double balance = totalIn - totalOut;

        PdfPTable summaryTable = new PdfPTable(3);
        summaryTable.setWidthPercentage(100);
        summaryTable.setSpacingAfter(15);

        // Headers
        addSummaryCell(summaryTable, "Total Cash In", BaseColor.LIGHT_GRAY);
        addSummaryCell(summaryTable, "Total Cash Out", BaseColor.LIGHT_GRAY);
        addSummaryCell(summaryTable, "Final Balance", BaseColor.LIGHT_GRAY);

        // Values
        addSummaryCell(summaryTable, formatCurrency(totalIn), BaseColor.WHITE);
        addSummaryCell(summaryTable, formatCurrency(totalOut), BaseColor.WHITE);

        BaseColor balanceColor = balance >= 0 ? new BaseColor(0, 128, 0) : BaseColor.RED;
        PdfPCell balanceCell = new PdfPCell(new Phrase(formatCurrency(balance), new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD, balanceColor)));
        balanceCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        balanceCell.setPadding(10);
        balanceCell.setBorder(PdfPCell.NO_BORDER);
        summaryTable.addCell(balanceCell);

        document.add(summaryTable);

        Paragraph count = new Paragraph("Total No. of entries: " + transactions.size(), normalFont);
        count.setSpacingAfter(10);
        document.add(count);
    }

    private static void addSummaryCell(PdfPTable table, String text, BaseColor bgColor) {
        PdfPCell cell = new PdfPCell(new Phrase(text, normalFont));
        cell.setBackgroundColor(bgColor);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(8);
        cell.setBorder(PdfPCell.NO_BORDER);
        table.addCell(cell);
    }

    private static void addTransactionTable(Document document, List<TransactionModel> transactions) throws DocumentException {
        float[] columnWidths = {2, 4, 2, 2, 2, 2};
        PdfPTable table = new PdfPTable(columnWidths);
        table.setWidthPercentage(100);
        table.setHeaderRows(1); // Repeats header on new pages

        String[] headers = {"Date", "Remark", "Mode", "Cash In", "Cash Out", "Balance"};
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, tableHeaderFont));
            cell.setBackgroundColor(HEADER_GRAY);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(8);
            table.addCell(cell);
        }

        double runningBalance = 0;
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yy", Locale.getDefault());

        for (TransactionModel t : transactions) {
            if ("IN".equalsIgnoreCase(t.getType())) {
                runningBalance += t.getAmount();
            } else {
                runningBalance -= t.getAmount();
            }

            table.addCell(createCell(sdf.format(new Date(t.getTimestamp())), Element.ALIGN_LEFT));
            table.addCell(createCell(t.getRemark() != null ? t.getRemark() : t.getTransactionCategory(), Element.ALIGN_LEFT));
            table.addCell(createCell(t.getPaymentMode(), Element.ALIGN_CENTER, MODE_BLUE_COLOR));

            if ("IN".equalsIgnoreCase(t.getType())) {
                table.addCell(createCell(formatCurrency(t.getAmount()), Element.ALIGN_RIGHT, new BaseColor(0, 128, 0)));
                table.addCell(createCell("", Element.ALIGN_RIGHT));
            } else {
                table.addCell(createCell("", Element.ALIGN_RIGHT));
                table.addCell(createCell(formatCurrency(t.getAmount()), Element.ALIGN_RIGHT, BaseColor.RED));
            }

            BaseColor balColor = runningBalance >= 0 ? new BaseColor(0, 128, 0) : BaseColor.RED;
            table.addCell(createCell(formatCurrency(runningBalance), Element.ALIGN_RIGHT, balColor));
        }

        document.add(table);
    }

    private static PdfPCell createCell(String text, int alignment) {
        return createCell(text, alignment, BaseColor.BLACK);
    }

    private static PdfPCell createCell(String text, int alignment, BaseColor color) {
        Font font = new Font(Font.FontFamily.HELVETICA, 9, Font.NORMAL, color);
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(6);
        return cell;
    }

    private static String formatCurrency(double amount) {
        return String.format(Locale.getDefault(), "%.0f", amount);
    }

    // [FIX] Inner Class for Footer (Page Numbers & Credentials)
    static class FooterEvent extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();

            // 1. Credentials (Left)
            Phrase credentials = new Phrase("Generated by Artham", footerFont);
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, credentials,
                    document.left(), document.bottom() - 10, 0);

            // 2. Page Number (Center)
            Phrase pageNumber = new Phrase("Page " + writer.getPageNumber(), footerFont);
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, pageNumber,
                    (document.right() - document.left()) / 2 + document.leftMargin(),
                    document.bottom() - 10, 0);
        }
    }
}