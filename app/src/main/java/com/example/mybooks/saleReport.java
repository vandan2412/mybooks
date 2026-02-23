package com.example.mybooks;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.List;
import java.util.Map;

public class saleReport extends AppCompatActivity {

    EditText etFromDate, etToDate, etSearchParty;
    Button btnFilter, btnExportPdf, btnExportExcel, btnPrint, btnBack, btnClear;
    Spinner spinnerSort;
    ListView listsales;
    TextView tvTotal;

    FirebaseFirestore db;

    SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());

    List<Map<String, Object>> rawList = new ArrayList<>();
    ArrayList<Object> displayList = new ArrayList<>();

    PurchaseAdapter adapter;

    boolean enableDayTotal = true;  // auto controlled

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sale_report);

        etFromDate = findViewById(R.id.etFromDate);
        etToDate = findViewById(R.id.etToDate);
        etSearchParty = findViewById(R.id.etSearchParty);

        btnFilter = findViewById(R.id.btnFilter);
        btnExportPdf = findViewById(R.id.btnExportPdf);
        btnExportExcel = findViewById(R.id.btnExportExcel);
        btnPrint = findViewById(R.id.btnPrint);
        btnBack = findViewById(R.id.btnBack);
        btnClear = findViewById(R.id.btnClearFilter);

        spinnerSort = findViewById(R.id.spinnerSort);
        listsales=findViewById(R.id.listsales);
        tvTotal = findViewById(R.id.tvTotal);

        db = FirebaseFirestore.getInstance();

        adapter = new PurchaseAdapter(this, displayList);
        listsales.setAdapter(adapter);
        listsales.setOnItemClickListener((parent, view, position, id) -> {
            Object obj = displayList.get(position);

            // Skip day total rows
            if (obj instanceof String) return;

            Map<String, Object> m = (Map<String, Object>) obj;

            String invoice = (m.get("invoiceNo") != null) ? m.get("invoiceNo").toString() : "";

            if (invoice.isEmpty()) {
                Toast.makeText(this, "No invoice number found", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i;

            if (invoice.startsWith("S-")) {
                i = new Intent(saleReport.this, sale.class);
            } else {
                i = new Intent(saleReport.this, sale2.class);
            }

            i.putExtra("invno", invoice);
            startActivity(i);

        });


        ArrayAdapter<String> spinAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"Sort: Date Asc", "Date Desc", "Amount Asc", "Amount Desc", "Party Asc", "Party Desc", "Invoice Asc"});
        spinAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSort.setAdapter(spinAdapter);

        pickDate(etFromDate);
        pickDate(etToDate);

        loadAllsales();

        btnFilter.setOnClickListener(v -> applyFiltersAndDisplay());
        btnClear.setOnClickListener(v -> clearFilters());

        etSearchParty.setOnEditorActionListener((v, actionId, event) -> {
            applyFiltersAndDisplay();
            return false;
        });

        spinnerSort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyFiltersAndDisplay();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnExportPdf.setOnClickListener(v -> askPhoneAndSendPdf());
        btnExportExcel.setOnClickListener(v -> askPhoneAndSendExcel());
        btnPrint.setOnClickListener(v -> printReport());
        btnBack.setOnClickListener(v -> finish());
    }

    private void pickDate(EditText e) {
        e.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            DatePickerDialog dp = new DatePickerDialog(this, (view, y, m, d) -> {
                e.setText(String.format("%02d-%02d-%04d", d, m + 1, y));
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
            dp.show();
        });
    }

    private void clearFilters() {
        etFromDate.setText("");
        etToDate.setText("");
        etSearchParty.setText("");
        spinnerSort.setSelection(0);
        applyFiltersAndDisplay();
    }
    protected void onResume() {
        super.onResume();
        loadAllsales();

    }
    private void loadAllsales() {
        db.collection("Sales")
                .get()
                .addOnSuccessListener(q -> {
                    rawList.clear();
                    for (QueryDocumentSnapshot ds : q) rawList.add(ds.getData());
                    applyFiltersAndDisplay();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load sales: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }
    private void applyFiltersAndDisplay() {

        String from = etFromDate.getText().toString();
        String to = etToDate.getText().toString();
        String search = etSearchParty.getText().toString().trim().toLowerCase(); // GLOBAL SEARCH

        Date f = null, t = null;
        try { if (!from.isEmpty()) f = sdf.parse(from); } catch(Exception ignored) {}
        try { if (!to.isEmpty()) t = sdf.parse(to); } catch(Exception ignored) {}

        List<Map<String,Object>> filtered = new ArrayList<>();

        for (Map<String,Object> m : rawList) {
            try {
                Date d = sdf.parse(m.get("date").toString());
                boolean ok = true;

                // ---------- DATE RANGE CHECK ----------
                if (f != null && d.before(f)) ok = false;
                if (t != null && d.after(t)) ok = false;

                // ---------- GLOBAL SEARCH CHECK ----------
                if (!search.isEmpty()) {

                    // Convert all fields to lower-case Strings
                    String party = m.get("party") != null ? m.get("party").toString().toLowerCase() : "";
                    String invoice = m.get("invoiceNo") != null ? m.get("invoiceNo").toString().toLowerCase() : "";
                    String dateStr = m.get("date") != null ? m.get("date").toString().toLowerCase() : "";
                    String amount = m.get("grandTotal") != null ? m.get("grandTotal").toString().toLowerCase() : "";

                    boolean match =
                            party.contains(search) ||
                                    invoice.contains(search) ||
                                    dateStr.contains(search) ||
                                    amount.contains(search);

                    if (!match) ok = false;
                }

                if (ok) filtered.add(m);

            } catch(Exception ignored){}
        }

        // ---------- SORT RESULTS ----------
        int s = spinnerSort.getSelectedItemPosition();
        switch (s) {
            case 0: Collections.sort(filtered, Comparator.comparing(a -> parse(a.get("date").toString()))); break;
            case 1: Collections.sort(filtered, (a,b)-> parse(b.get("date").toString()).compareTo(parse(a.get("date").toString()))); break;
            case 2: Collections.sort(filtered, Comparator.comparingDouble(a->Double.parseDouble(a.get("grandTotal").toString()))); break;
            case 3: Collections.sort(filtered, (a,b)->Double.compare(
                    Double.parseDouble(b.get("grandTotal").toString()),
                    Double.parseDouble(a.get("grandTotal").toString()))); break;
            case 4: Collections.sort(filtered, Comparator.comparing(a->a.get("party")!=null? a.get("party").toString() : "")); break;
            case 5: Collections.sort(filtered, (a,b)-> {
                String pa = a.get("party")!=null? a.get("party").toString() : "";
                String pb = b.get("party")!=null? b.get("party").toString() : "";
                return pb.compareTo(pa);
            }); break;
            case 6: Collections.sort(filtered, Comparator.comparing(a->a.get("invoiceNo")!=null? a.get("invoiceNo").toString() : "")); break;
        }

        // enable day total only if search empty and sorting by date
        enableDayTotal = search.isEmpty() && (s == 0 || s == 1);

        displayList.clear();
        double total = 0;
        String prev = "";
        double dayTotal = 0;

        for (Map<String,Object> m : filtered) {
            String date = (m.get("date") != null) ? m.get("date").toString() : "";
            double gt = 0;
            try { gt = Double.parseDouble(String.valueOf(m.get("grandTotal"))); } catch(Exception ignored) {}

            if (enableDayTotal) {
                if (!prev.equals("") && !prev.equals(date)) {
                    displayList.add("DAYTOTAL:" + dayTotal);
                    dayTotal = 0;
                }
            }

            displayList.add(m);

            dayTotal += gt;
            total += gt;
            prev = date;
        }

        if (enableDayTotal && !filtered.isEmpty())
            displayList.add("DAYTOTAL:" + dayTotal);

        tvTotal.setText("Total: " + total);
        adapter.notifyDataSetChanged();
    }
    private Date parse(String s) {
        try { return sdf.parse(s); } catch(Exception e){ return new Date(0); }
    }

    private void askPhoneAndSendPdf() {
        EditText input = new EditText(this);
        input.setHint("Enter WhatsApp Number (optional)");

        new AlertDialog.Builder(this)
                .setTitle("Send to WhatsApp")
                .setView(input)
                .setPositiveButton("Send", (d, w) -> {
                    String num = input.getText().toString().trim();
                    sendPdfToWhatsapp(num); // number may be empty
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    private void sendPdfToWhatsapp(String phone) {
        try {
            // Create invoices directory inside externalCacheDir
            File invoiceDir = new File(getExternalCacheDir(), "invoices");
            if (!invoiceDir.exists()) invoiceDir.mkdirs();

            File pdfFile = new File(invoiceDir, "sale_report_temp.pdf");
            createPdfFile(pdfFile);

            if (!pdfFile.exists()) {
                Toast.makeText(this, "PDF not created", Toast.LENGTH_SHORT).show();
                return;
            }

            Uri uri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".provider",
                    pdfFile
            );

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // ---------------------------
            // CASE A — PHONE GIVEN
            // ---------------------------
            if (phone != null && !phone.trim().isEmpty()) {

                phone = phone.trim();

                // Auto add 91 for 10-digit numbers
                if (phone.length() == 10) {
                    phone = "91" + phone;
                }

                intent.putExtra("jid", phone + "@s.whatsapp.net");
            }
            try {
                getPackageManager().getPackageInfo("com.whatsapp", 0);
                intent.setPackage("com.whatsapp");
                startActivity(intent);
                return;
            } catch (Exception ignored) {}

            // Try WhatsApp Business
            try {
                getPackageManager().getPackageInfo("com.whatsapp.w4b", 0);
                intent.setPackage("com.whatsapp.w4b");
                startActivity(intent);
                return;
            } catch (Exception ignored) {}

            Toast.makeText(this, "WhatsApp not installed", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }


    private void createPdfFile(File f) throws Exception {
        PdfDocument pdf = new PdfDocument();
        Paint p = new Paint();
        int W = 595, H = 842;

        PdfDocument.PageInfo pi = new PdfDocument.PageInfo.Builder(W, H, 1).create();
        PdfDocument.Page page = pdf.startPage(pi);
        Canvas c = page.getCanvas();
        int y = 30;

        p.setTextSize(18);
        c.drawText("sale Report", 20, y, p);
        y += 25;

        p.setTextSize(12);
        c.drawText("Generated: " + sdf.format(new Date()), 20, y, p);
        y += 20;

        p.setTextSize(10);
        c.drawText("SNo", 20, y, p);
        c.drawText("Date", 60, y, p);
        c.drawText("Invoice", 140, y, p);
        c.drawText("Party", 220, y, p);
        c.drawText("Amount", 460, y, p);
        y += 14;

        int sno = 1;

        for (Object o : displayList) {

            if (y > H - 60) {
                pdf.finishPage(page);
                pi = new PdfDocument.PageInfo.Builder(W, H, pdf.getPages().size() + 1).create();
                page = pdf.startPage(pi);
                c = page.getCanvas();
                y = 30;
            }

            if (o instanceof String && ((String) o).startsWith("DAYTOTAL:")) {
                String total = ((String) o).split(":")[1];
                c.drawText("Day Total: " + total, 220, y, p);
                y += 14;
                continue;
            }

            Map m = (Map) o;

            c.drawText(String.valueOf(sno++), 20, y, p);
            c.drawText(m.get("date")!=null?m.get("date").toString():"", 60, y, p);
            c.drawText(m.get("invoiceNo")!=null?m.get("invoiceNo").toString():"", 140, y, p);
            c.drawText(m.get("party")!=null?m.get("party").toString():"", 220, y, p);
            c.drawText(m.get("grandTotal")!=null?m.get("grandTotal").toString():"", 460, y, p);

            y += 14;
        }

        pdf.finishPage(page);

        // ensure directory exists
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        FileOutputStream fos = new FileOutputStream(f);
        pdf.writeTo(fos);
        fos.close();
        pdf.close();
    }

    private void exportCsv() {
        try {
            File f = new File(getExternalFilesDir(null), "sale_report.csv");
            FileOutputStream fos = new FileOutputStream(f);
            StringBuilder sb = new StringBuilder();

            sb.append("SNo,Date,Invoice,Party,Amount\n");
            int sno = 1;

            for (Object o : displayList) {

                // SKIP DAYTOTAL ROWS
                if (o instanceof String && ((String) o).startsWith("DAYTOTAL:"))
                    continue;

                Map m = (Map) o;
                sb.append(sno++).append(",");
                sb.append(m.get("date")!=null?m.get("date"):"").append(",");
                sb.append(m.get("invoiceNo")!=null?m.get("invoiceNo"):"").append(",");
                sb.append(m.get("party")!=null?m.get("party"):"").append(",");
                sb.append(m.get("grandTotal")!=null?m.get("grandTotal"):"").append("\n");
            }

            fos.write(sb.toString().getBytes());
            fos.close();

            Toast.makeText(this, "CSV Saved: " + f.getAbsolutePath(), Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Toast.makeText(this, "CSV Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    private void sendExcelToWhatsapp(String phone) {
        try {
            // Directory inside cache
            File invoiceDir = new File(getExternalCacheDir(), "invoices");
            if (!invoiceDir.exists()) invoiceDir.mkdirs();

            // Create CSV inside cache
            File excelFile = new File(invoiceDir, "sale_report_temp.csv");

            FileOutputStream fos = new FileOutputStream(excelFile);
            StringBuilder sb = new StringBuilder();

            sb.append("SNo,Date,Invoice,Party,Amount\n");
            int sno = 1;

            for (Object o : displayList) {

                // SKIP DAY TOTAL ROWS
                if (o instanceof String && ((String) o).startsWith("DAYTOTAL:"))
                    continue;

                Map m = (Map) o;
                sb.append(sno++).append(",");
                sb.append(m.get("date")!=null?m.get("date"):"").append(",");
                sb.append(m.get("invoiceNo")!=null?m.get("invoiceNo"):"").append(",");
                sb.append(m.get("party")!=null?m.get("party"):"").append(",");
                sb.append(m.get("grandTotal")!=null?m.get("grandTotal"):"").append("\n");
            }

            fos.write(sb.toString().getBytes());
            fos.close();

            if (!excelFile.exists()) {
                Toast.makeText(this, "CSV not created", Toast.LENGTH_SHORT).show();
                return;
            }

            // FileProvider URI
            Uri uri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".provider",
                    excelFile
            );

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/csv");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // Optional phone (JID)
            if (phone != null && !phone.trim().isEmpty()) {
                phone = phone.trim();
                if (phone.length() == 10) phone = "91" + phone;
                intent.putExtra("jid", phone + "@s.whatsapp.net");
            }

            // Try normal WhatsApp
            try {
                getPackageManager().getPackageInfo("com.whatsapp", 0);
                intent.setPackage("com.whatsapp");
                startActivity(intent);
                return;
            } catch (Exception ignored) {}

            // Try WhatsApp Business
            try {
                getPackageManager().getPackageInfo("com.whatsapp.w4b", 0);
                intent.setPackage("com.whatsapp.w4b");
                startActivity(intent);
                return;
            } catch (Exception ignored) {}

            Toast.makeText(this, "WhatsApp not installed", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this, "CSV error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    private void askPhoneAndSendExcel() {
        EditText input = new EditText(this);
        input.setHint("Enter WhatsApp Number (optional)");

        new AlertDialog.Builder(this)
                .setTitle("Send Excel to WhatsApp")
                .setView(input)
                .setPositiveButton("Send", (d, w) -> {
                    String num = input.getText().toString().trim();
                    sendExcelToWhatsapp(num);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void printReport() {
        try {
            // use cache/invoices to match provider paths
            File invoicesDir = new File(getExternalCacheDir(), "invoices");
            if (!invoicesDir.exists()) invoicesDir.mkdirs();
            File f = new File(invoicesDir, "temp_print.pdf");
            createPdfFile(f);

            PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
            PrintAttributes pa = new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build();
            PrintDocumentAdapterPDF adapter = new PrintDocumentAdapterPDF(this, f);
            pm.print("saleReport", adapter, pa);

        } catch (Exception e) {
            Toast.makeText(this, "Print Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
