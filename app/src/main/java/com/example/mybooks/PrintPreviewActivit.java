package com.example.mybooks;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;

public class PrintPreviewActivit extends AppCompatActivity {

    private BillData billData;
    private LinearLayout layoutItems;
    private EditText etPhone;
    private FirebaseFirestore db;

    private static final int PAGE_WIDTH_POINTS = 298; // ~105mm
    private static final int PAGE_HEIGHT_POINTS = 842; // A4 height

    // PDF Table Column Layout
    private static final int MARGIN = 10;
    private static final int SNO_WIDTH = 30;
    private static final int QTY_WIDTH = 45;
    private static final int RATE_WIDTH = 55;
    private static final int TOTAL_WIDTH = 60;
    private static final int ITEM_WIDTH = PAGE_WIDTH_POINTS - (2 * MARGIN) - SNO_WIDTH - QTY_WIDTH - RATE_WIDTH - TOTAL_WIDTH;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_print_preview);

        db = FirebaseFirestore.getInstance();
        billData = (BillData) getIntent().getSerializableExtra("billData");
        if (billData == null) {
            Toast.makeText(this, "Bill data not found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        etPhone = findViewById(R.id.etPhone);
        layoutItems = findViewById(R.id.layoutItems);
        Button btnPrint = findViewById(R.id.btnPrint);
        Button btnWhatsApp = findViewById(R.id.btnWhatsApp);
        Button btnSave = findViewById(R.id.btnSave);

        populateBillDetails();
        populateItemsForPreview();
        fetchPartyPhoneNumber();

        btnPrint.setOnClickListener(v -> generateAndPrintBill());
        btnWhatsApp.setOnClickListener(v -> generateAndShareBill());
        btnSave.setOnClickListener(v -> savePdfToDownloads());
    }

    private void fetchPartyPhoneNumber() {
        if (billData.party != null && !billData.party.isEmpty()) {
            db.collection("Party").document(billData.party).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String pno = documentSnapshot.getString("phone");
                        if (pno != null && !pno.isEmpty()) {
                            etPhone.setText(pno);
                        }
                    }
                });
        }
    }

    private void populateBillDetails() {
        ((TextView) findViewById(R.id.tvInvoiceNo)).setText(String.format("Invoice No: %s", billData.invoiceNo));
        ((TextView) findViewById(R.id.tvDate)).setText(String.format("Date: %s", billData.date));
        ((TextView) findViewById(R.id.tvParty)).setText(String.format("To: %s", billData.party));

        double subTotal = billData.grandTotal - billData.tax + billData.discount;
        ((TextView) findViewById(R.id.tvTotal)).setText(String.format(Locale.getDefault(), "Sub-Total: ₹ %.2f", subTotal));
        ((TextView) findViewById(R.id.tvDiscount)).setText(String.format(Locale.getDefault(), "Discount: - ₹ %.2f", billData.discount));
        ((TextView) findViewById(R.id.tvTax)).setText(String.format(Locale.getDefault(), "Tax: + ₹ %.2f", billData.tax));
        ((TextView) findViewById(R.id.tvNetTotal)).setText(String.format(Locale.getDefault(), "Grand Total: ₹ %.2f", billData.grandTotal));
    }

    private void populateItemsForPreview() {
        layoutItems.removeAllViews();
        int sno = 1;
        for (Map<String, Object> item : billData.items) {
            LinearLayout itemRow = new LinearLayout(this);
            itemRow.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            itemRow.setOrientation(LinearLayout.HORIZONTAL);
            itemRow.setPadding(4, 4, 4, 4);

            TextView tvSNo = createTextView(sno + "", 30, false, Gravity.START);
            String name = getStringFromMap(item, "itemName");
            TextView tvName = createTextView(name, 0, true, Gravity.START);
            double qty = getDoubleFromMap(item, "quantity");
            String unit = getStringFromMap(item, "unit");
            TextView tvQty = createTextView(String.format(Locale.getDefault(), "%.2f %s", qty, unit), 50, false, Gravity.END);
            double rate = getDoubleFromMap(item, "price");
            TextView tvRate = createTextView(String.format(Locale.getDefault(), "%.2f", rate), 65, false, Gravity.END);
            double total = getDoubleFromMap(item, "total");
            TextView tvTotalValue = createTextView(String.format(Locale.getDefault(), "%.2f", total), 75, false, Gravity.END);

            itemRow.addView(tvSNo);
            itemRow.addView(tvName);
            itemRow.addView(tvQty);
            itemRow.addView(tvRate);
            itemRow.addView(tvTotalValue);

            layoutItems.addView(itemRow);
            sno++;
        }
    }

    private TextView createTextView(String text, int widthDp, boolean isWeighted, int gravity) {
        TextView tv = new TextView(this);
        LinearLayout.LayoutParams params;
        if (isWeighted) {
            params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        } else {
            params = new LinearLayout.LayoutParams(dpToPx(widthDp), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        tv.setLayoutParams(params);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setGravity(gravity);
        return tv;
    }

    private int dpToPx(int dp) {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        return Math.round(dp * (displayMetrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT));
    }

    private File createPdfFile() throws IOException {
        File folder = new File(getExternalCacheDir(), "invoices");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return new File(folder, "Invoice_" + billData.invoiceNo + ".pdf");
    }

    private void generateAndPrintBill() {
        try {
            File pdfFile = createPdfFile();
            generatePdf(pdfFile);
            PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
            String jobName = getString(R.string.app_name) + " Document";
            printManager.print(jobName, new PdfPrintAdapter(this, pdfFile), null);
        } catch (IOException e) {
            Toast.makeText(this, "Error generating PDF for printing: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void generateAndShareBill() {
        try {
            File pdfFile = createPdfFile();
            generatePdf(pdfFile);
            String phone = etPhone.getText().toString().trim();
            sharePdfToApp(pdfFile, phone.isEmpty() ? null : phone);
        } catch (IOException e) {
            Toast.makeText(this, "Error generating PDF for sharing: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void savePdfToDownloads() {
        finish();

    }
    private void sharePdfToApp(File pdfFile, String phoneNumber) {

        try {
            Uri contentUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".provider",
                    pdfFile
            );

            Intent waIntent = new Intent(Intent.ACTION_SEND);
            waIntent.setType("application/pdf");
            waIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            waIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            waIntent.setPackage("com.whatsapp");

            if (phoneNumber != null && !phoneNumber.isEmpty()) {
                if (!phoneNumber.startsWith("+")) {
                    phoneNumber = "+91" + phoneNumber;
                }
                waIntent.putExtra("jid",
                        phoneNumber.replace("+", "") + "@s.whatsapp.net");
            }

            startActivity(waIntent);

        } catch (Exception e) {
            Toast.makeText(this, "WhatsApp share failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String getStringFromMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return (value instanceof String) ? (String) value : "";
    }

    private double getDoubleFromMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return (value instanceof Number) ? ((Number) value).doubleValue() : 0.0;
    }

    private static class PageItemsResult {
        final int nextY;
        final double pageTotal;

        PageItemsResult(int nextY, double pageTotal) {
            this.nextY = nextY;
            this.pageTotal = pageTotal;
        }
    }

    private void generatePdf(File pdfFile) throws IOException {
        PdfDocument document = new PdfDocument();
        final int maxItemsPerPage = 30;
        int currentItemIndex = 0;
        final int totalItems = billData.items.size();
        final int totalPages = (totalItems == 0) ? 1 : (int) Math.ceil((double) totalItems / maxItemsPerPage);

        for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH_POINTS, PAGE_HEIGHT_POINTS, pageIndex + 1).create();
            PdfDocument.Page page = document.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            int yStart = drawBillHeader(canvas, pageInfo.getPageWidth(), pageIndex > 0);
            yStart = drawTableHeader(canvas, yStart);

            int itemsToDraw = Math.min(maxItemsPerPage, totalItems - currentItemIndex);
            int endItemIndex = currentItemIndex + itemsToDraw;

            PageItemsResult result = drawItemsAndGetTotal(canvas, yStart, currentItemIndex, endItemIndex);
            yStart = result.nextY;

            boolean isLastPage = (pageIndex == totalPages - 1);
            if (isLastPage) {
                drawBillFooter(canvas, yStart + 10);
            } else {
                drawContinuationFooter(canvas, yStart + 10, pageIndex + 1, totalPages, result.pageTotal);
            }

            document.finishPage(page);
            currentItemIndex = endItemIndex;
        }

        try (FileOutputStream fos = new FileOutputStream(pdfFile)) {
            document.writeTo(fos);
        } finally {
            document.close();
        }
    }

    private int drawBillHeader(Canvas canvas, int pageWidth, boolean isContinuation) {
        Paint p = new Paint();
        p.setColor(Color.BLACK);
        int y = 50;

        p.setTextSize(20);
        p.setFakeBoldText(true);
        String title = isContinuation ? "INVOICE (Continued)" : "INVOICE";
        float titleWidth = p.measureText(title);
        canvas.drawText(title, (pageWidth - titleWidth) / 2, y, p);
        y += 30;

        p.setTextSize(12);
        p.setFakeBoldText(false);
        canvas.drawText(String.format("Invoice No: %s", billData.invoiceNo), 20, y, p);
        canvas.drawText(String.format("Date: %s", billData.date), pageWidth - 100, y, p);
        y += 18;

        p.setFakeBoldText(true);
        canvas.drawText(String.format("To: %s", billData.party), 20, y, p);
        p.setFakeBoldText(false);
        y += 25;

        return y;
    }

    private int drawTableHeader(Canvas canvas, int yStart) {
        Paint p = new Paint();
        p.setTextSize(12);
        p.setFakeBoldText(true);
        int y = yStart;

        // Draw top line
        canvas.drawLine(MARGIN, y, PAGE_WIDTH_POINTS - MARGIN, y, p);
        y += 15; // Move down for text, providing padding

        p.setTextAlign(Paint.Align.CENTER);
        int xSNo = MARGIN;
        int xItem = xSNo + SNO_WIDTH;
        int xQty = xItem + ITEM_WIDTH;
        int xRate = xQty + QTY_WIDTH;
        int xTotal = xRate + RATE_WIDTH;

        canvas.drawText("SNo", xSNo + SNO_WIDTH / 2, y, p);
        canvas.drawText("Item", xItem + ITEM_WIDTH / 2, y, p);
        canvas.drawText("Qty Unit", xQty + QTY_WIDTH / 2, y, p);
        canvas.drawText("Rate", xRate + RATE_WIDTH / 2, y, p);
        canvas.drawText("Total (₹)", xTotal + TOTAL_WIDTH / 2, y, p);

        y += 5; // Padding below text before the line
        // Draw bottom line
        canvas.drawLine(MARGIN, y, PAGE_WIDTH_POINTS - MARGIN, y, p);

        return y + 10; // Space for the first item row
    }

    private PageItemsResult drawItemsAndGetTotal(Canvas canvas, int yStart, int startIndex, int endIndex) {
        Paint textPaint = new Paint();
        textPaint.setTextSize(10);
        int y = yStart;
        double pageTotal = 0.0;
        final int ROW_HEIGHT = 18;

        Paint oddRowPaint = new Paint();
        oddRowPaint.setStyle(Paint.Style.FILL);
        oddRowPaint.setColor(Color.parseColor("#F0F0F0")); // Light gray

        int xSNo = MARGIN;
        int xItem = xSNo + SNO_WIDTH;
        int xQty = xItem + ITEM_WIDTH;
        int xRate = xQty + QTY_WIDTH;
        int xTotal = xRate + RATE_WIDTH;
        int tableEnd = xTotal + TOTAL_WIDTH;

        for (int i = startIndex; i < endIndex; i++) {
            if (i % 2 != 0) {
                canvas.drawRect(MARGIN, y - textPaint.getTextSize() - 4, tableEnd, y + ROW_HEIGHT - textPaint.getTextSize() - 4, oddRowPaint);
            }

            Map<String, Object> item = billData.items.get(i);

            String name = getStringFromMap(item, "itemName");
            double qty = getDoubleFromMap(item, "quantity");
            String unit = getStringFromMap(item, "unit");
            double rate = getDoubleFromMap(item, "price");
            double total = getDoubleFromMap(item, "total");

            pageTotal += total;

            String snoText = String.valueOf(i + 1);
            String qtyText = String.format(Locale.getDefault(), "%.2f %s", qty, unit);
            String rateText = String.format(Locale.getDefault(), "%.2f", rate);
            String totalText = String.format(Locale.getDefault(), "%.2f", total);

            textPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(snoText, xSNo + 5, y, textPaint);
            canvas.drawText(name, xItem + 5, y, textPaint);

            textPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(qtyText, xQty + QTY_WIDTH - 5, y, textPaint);
            canvas.drawText(rateText, xRate + RATE_WIDTH - 5, y, textPaint);
            canvas.drawText(totalText, xTotal + TOTAL_WIDTH - 5, y, textPaint);

            y += ROW_HEIGHT;
        }
        return new PageItemsResult(y, pageTotal);
    }

    private void drawBillFooter(Canvas canvas, int yStart) {
        Paint p = new Paint();
        p.setTextSize(12);
        int y = yStart;

        int rightAlignX = PAGE_WIDTH_POINTS - MARGIN;
        int leftAlignX = rightAlignX - 150;

        double subTotal = billData.grandTotal - billData.tax + billData.discount;

        p.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("Sub-Total:", leftAlignX, y, p);
        p.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(String.format(Locale.getDefault(), "₹ %.2f", subTotal), rightAlignX, y, p);
        y += 18;

        p.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("Discount:", leftAlignX, y, p);
        p.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(String.format(Locale.getDefault(), "- ₹ %.2f", billData.discount), rightAlignX, y, p);
        y += 18;

        p.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("Tax:", leftAlignX, y, p);
        p.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(String.format(Locale.getDefault(), "+ ₹ %.2f", billData.tax), rightAlignX, y, p);
        y += 10;

        canvas.drawLine(leftAlignX - 10, y, rightAlignX, y, p);
        y += 18;

        p.setFakeBoldText(true);
        p.setTextSize(14);
        p.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("GRAND TOTAL:", leftAlignX, y, p);
        p.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(String.format(Locale.getDefault(), "₹ %.2f", billData.grandTotal), rightAlignX, y, p);
        y += 25;

        p.setTextAlign(Paint.Align.LEFT);
        p.setTextSize(12);
        p.setFakeBoldText(false);
        canvas.drawText(String.format("Remarks: %s", billData.remarks), MARGIN, y, p);
    }

    private void drawContinuationFooter(Canvas canvas, int yStart, int pageNumber, int totalPages, double pageTotal) {
        Paint p = new Paint();
        int rightAlignX = PAGE_WIDTH_POINTS - MARGIN;
        int y = yStart;

        p.setColor(Color.BLACK);
        canvas.drawLine(MARGIN, y, rightAlignX, y, p);
        y += 20;

        p.setTextSize(12);
        p.setFakeBoldText(true);
        p.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(String.format(Locale.getDefault(), "Page Total: ₹ %.2f", pageTotal), rightAlignX, y, p);

        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(false);
        p.setTextSize(10);
        String pageMarker = String.format(Locale.getDefault(), "Page %d of %d (Continued...)", pageNumber, totalPages);
        canvas.drawText(pageMarker, PAGE_WIDTH_POINTS / 2, PAGE_HEIGHT_POINTS - 20, p);
    }

    private static class PdfPrintAdapter extends PrintDocumentAdapter {
        private final File file;

        public PdfPrintAdapter(Context context, File file) {
            this.file = file;
        }

        @Override
        public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes, CancellationSignal cancellationSignal, LayoutResultCallback callback, Bundle extras) {
            if (cancellationSignal.isCanceled()) {
                callback.onLayoutCancelled();
                return;
            }
            PrintDocumentInfo info = new PrintDocumentInfo.Builder(file.getName())
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                    .build();
            callback.onLayoutFinished(info, true);
        }

        @Override
        public void onWrite(final PageRange[] pages, final ParcelFileDescriptor destination, final CancellationSignal cancellationSignal, final WriteResultCallback callback) {
            try (FileOutputStream output = new FileOutputStream(destination.getFileDescriptor());
                 java.io.FileInputStream input = new java.io.FileInputStream(file)) {
                byte[] buf = new byte[1024];
                int len;
                while ((len = input.read(buf)) > 0) {
                    output.write(buf, 0, len);
                }
                callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
            } catch (Exception e) {
                callback.onWriteFailed(e.getMessage());
            }
        }
    }
}
