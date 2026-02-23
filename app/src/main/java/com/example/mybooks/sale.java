package com.example.mybooks;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.*;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.firestore.*;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class sale extends AppCompatActivity {

    EditText etDate, etInvoice, etDiscount, etTax, etRemarks;
    AutoCompleteTextView etSearchParty;
    Button btnAddItem, btnSave, btnCancel, btnNext, btnPrev, btnDeleteSale;
    ListView listViewItems;
    TextView tvGrandTotal, tvFinalTotal;
    EditText etDiscountPercent, etTaxPercent;

    FirebaseFirestore db;
    ArrayList<Map<String, Object>> saleItems = new ArrayList<>();
    ItemAdapter adapter;
    ArrayList<Map<String, Object>> partyList = new ArrayList<>();

    double subTotal = 0;
    double finalCalculatedTotal = 0;
    DecimalFormat df = new DecimalFormat("#.##");

    // Watcher references (class-level so we can remove/re-add safely)
    private android.text.TextWatcher discountWatcher, taxWatcher, discountPercentWatcher, taxPercentWatcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sale);

        db = FirebaseFirestore.getInstance();

        etDate = findViewById(R.id.etDate);
        etInvoice = findViewById(R.id.etInvoice);
        etSearchParty = findViewById(R.id.etParty);
        btnAddItem = findViewById(R.id.btnAddItem);
        btnSave = findViewById(R.id.btnSave);
        btnCancel = findViewById(R.id.btnCancel);
        btnNext = findViewById(R.id.btnNext);
        btnPrev = findViewById(R.id.btnPrev);

        tvGrandTotal = findViewById(R.id.tvGrandTotal);
        tvFinalTotal = findViewById(R.id.tvFinalTotal);
        listViewItems = findViewById(R.id.listViewItems);
        btnDeleteSale = findViewById(R.id.btnDeleteSale);

        etDiscount = findViewById(R.id.etDiscount);
        etTax = findViewById(R.id.etTax);
        etRemarks = findViewById(R.id.etRemarks);

        etDiscountPercent = findViewById(R.id.etDiscountPercent);
        etTaxPercent = findViewById(R.id.etTaxPercent);

        adapter = new ItemAdapter();
        listViewItems.setAdapter(adapter);

        checkIncomingInvoice();
        loadParties();

        setupWatchersAndListeners();

        btnAddItem.setOnClickListener(v -> showAddItemDialog(null, -1));
        btnSave.setOnClickListener(v -> saveFullSale());


        btnDeleteSale.setOnClickListener(v -> confirmAndDeleteSale());
        btnNext.setOnClickListener(v -> navigateInvoice(true));
        btnPrev.setOnClickListener(v -> navigateInvoice(false));

        listViewItems.setOnItemClickListener((parent, view, position, id) -> {
            new AlertDialog.Builder(this)
                    .setTitle("Choose Action")
                    .setItems(new String[]{"Modify", "Delete"}, (dialog, which) -> {
                        if (which == 0)
                            showAddItemDialog(saleItems.get(position), position);
                        else {
                            saleItems.remove(position);
                            sortItemsAndRecalculate();
                            adapter.notifyDataSetChanged();
                        }
                    })
                    .show();
        });
        btnCancel.setOnClickListener(v -> {
            rollbackInvoiceIfNeeded();
            finish();        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
           public  void handleOnBackPressed() {
                rollbackInvoiceIfNeeded();
                finish();
            }
        });
    }
    private void checkIncomingInvoice() {
        Intent intent = getIntent();

        if (intent != null && intent.hasExtra("invno")) {

            String inv = intent.getStringExtra("invno");

            if (inv != null && !inv.trim().isEmpty()) {
                etInvoice.setText(inv);
                loadSaleByInvoice(inv);
                return;
            }
        }else{
            setupDateAndInvoice();
        }
        // No invoice found → do nothing
    }
    @Override
    protected void onResume() {
        super.onResume();

        // Reload updated party balances from Firestore
        loadParties();

        // Reload current invoice (this updates tax, discount, total, items)
        String currentInvoice = etInvoice.getText().toString();
        if (!currentInvoice.isEmpty()) {
            loadSaleByInvoice(currentInvoice);
        }
    }


    private void rollbackInvoiceIfNeeded() {

        String inv = etInvoice.getText().toString();   // example: S-5
        if (inv == null || !inv.startsWith("S-")) return;

        int currentNum;
        try {
            currentNum = Integer.parseInt(inv.substring(2));
        } catch (Exception e) {
            return;
        }

        DocumentReference invRef = db.collection("invno").document("S-");

        invRef.get().addOnSuccessListener(doc -> {
            if (doc.exists() && doc.contains("number")) {

                long savedNum = doc.getLong("number");

                if (savedNum-1 == currentNum) {
                    long updated = savedNum - 1;
                    if (updated < 0) updated = 0;

                    invRef.update("number", updated);
                }
            }
        });
    }

    private void setupWatchersAndListeners() {
        // Create watchers once so we can remove/re-add them
        discountWatcher = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) { sortItemsAndRecalculate(); }
        };
        taxWatcher = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) { sortItemsAndRecalculate(); }
        };
        discountPercentWatcher = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) { sortItemsAndRecalculate(); }
        };
        taxPercentWatcher = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) { sortItemsAndRecalculate(); }
        };

        // Attach watchers
        etDiscount.addTextChangedListener(discountWatcher);
        etTax.addTextChangedListener(taxWatcher);
        etDiscountPercent.addTextChangedListener(discountPercentWatcher);
        etTaxPercent.addTextChangedListener(taxPercentWatcher);
    }

    private interface TextChangeListener {
        void onTextChanged();
    }

    // SimpleTextWatcher kept for compatibility (unused now but safe to keep)
    private static class SimpleTextWatcher implements android.text.TextWatcher {
        private final TextChangeListener listener;
        SimpleTextWatcher(TextChangeListener listener) { this.listener = listener; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { listener.onTextChanged(); }
        @Override public void afterTextChanged(android.text.Editable s) {}
    }


    private void setupDateAndInvoice() {
        etDate.setText(new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date()));
        etDate.setFocusable(false);
        etDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(sale.this, (view, year, month, day) ->
                    etDate.setText(String.format(Locale.getDefault(), "%02d-%02d-%04d", day, month + 1, year)),
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });

        DocumentReference invRef = db.collection("invno").document("S-");

        invRef.get().addOnSuccessListener(doc -> {

            long last = 0;

            if (doc.exists() && doc.contains("number")) {
                last = doc.getLong("number");
            }

            long next = last + 1;
            String nextInvoice = "S-" + last;
            etInvoice.setText(nextInvoice);

            loadSaleByInvoice(nextInvoice);

            invRef.update("number", next)
                    .addOnFailureListener(e -> {
                        invRef.set(Collections.singletonMap("number", next));
                    });

        }).addOnFailureListener(e -> {
            String defaultInvoice = "S-1";
            etInvoice.setText(defaultInvoice);
            loadSaleByInvoice(defaultInvoice);
            invRef.set(Collections.singletonMap("number", 1L));
        });

    }

    private void loadParties() {
        partyList.clear();
        db.collection("Party").get().addOnSuccessListener(partyQuery -> {
            ArrayList<String> names = new ArrayList<>();
            for (DocumentSnapshot d : partyQuery) {
                Map<String, Object> data = d.getData();
                if (data != null) {
                    partyList.add(data);
                    String name = (String) data.get("partyName");
                    String city = (String) data.get("city");
                    double bal = parseDoubleSafe(data.get("currentBalance"));
                    names.add(name + (city != null ? ", " + city : "") + " | Bal: ₹" + df.format(bal));
                }
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, names);
            etSearchParty.setAdapter(adapter);
            etSearchParty.setThreshold(1);

            etSearchParty.setOnItemClickListener((parent, view, position, id) -> {
                String selected = parent.getItemAtPosition(position).toString();
                String[] parts = selected.split("\\|");
                etSearchParty.setText(parts[0].split(",")[0].trim());
            });
        });
    }
    private void showAddItemDialog(Map<String, Object> existing, int editPosition) {

        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_add_sale_item);

        AutoCompleteTextView etSearchItem = dialog.findViewById(R.id.etSearchItem);
        Spinner spUnit = dialog.findViewById(R.id.spinnerUnit);
        EditText etQuantity = dialog.findViewById(R.id.etQuantity);
        EditText etPrice = dialog.findViewById(R.id.etPrice);
        Button btnAdd = dialog.findViewById(R.id.btnAdd);
        Button btnComplete = dialog.findViewById(R.id.btnComplete);

        ArrayList<Map<String, Object>> itemList = new ArrayList<>();
        ArrayList<String> itemNamesForDropdown = new ArrayList<>();
        ArrayList<String> unitList = new ArrayList<>();

        final String[] originalUnit = {""};   // store original unit (from DB or existing)
        final boolean[] priceWasAutoConverted = {false}; // tracks whether etPrice was auto-updated

        db.collection("items").get().addOnSuccessListener(q -> {
            for (DocumentSnapshot d : q) {
                Map<String, Object> data = d.getData();
                if (data != null) {
                    data.put("id", d.getId());
                    itemList.add(data);

                    String itemName = data.get("itemName") + "";
                    double qty = parseDoubleSafe(data.get("quantity"));
                    String unit = data.get("unit") + "";

                    itemNamesForDropdown.add(itemName + " | Qty: " + df.format(qty) + " " + unit);
                }
            }

            ArrayAdapter<String> itemAdapter = new ArrayAdapter<>(
                    sale.this,
                    android.R.layout.simple_dropdown_item_1line,
                    itemNamesForDropdown
            );
            etSearchItem.setAdapter(itemAdapter);
            etSearchItem.setThreshold(1);
        });

        db.collection("units").get().addOnSuccessListener(q -> {
            for (DocumentSnapshot d : q) {
                String name = d.getString("name");
                if (name != null) unitList.add(name);
            }

            ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(
                    sale.this,
                    android.R.layout.simple_spinner_item,
                    unitList
            );

            unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spUnit.setAdapter(unitAdapter);

            if (existing != null) {
                int pos = unitList.indexOf(existing.get("unit") + "");
                if (pos >= 0) spUnit.setSelection(pos);
            }
        });

        if (existing != null) {
            etSearchItem.setText(existing.get("itemName") + "");
            etQuantity.setText(existing.get("quantity") + "");
            etPrice.setText(existing.get("price") + "");
            originalUnit[0] = existing.get("unit") + "";
        }

        etSearchItem.setOnItemClickListener((parent, view, position, id) -> {
            String selected = parent.getItemAtPosition(position).toString();
            String itemName = selected.split("\\|")[0].trim();
            etSearchItem.setText(itemName);

            for (Map<String, Object> i : itemList) {
                if (i.get("itemName").toString().equals(itemName)) {

                    String retailRate = i.get("retailRate") != null ? i.get("retailRate").toString() : "";
                    etPrice.setText(retailRate);

                    String unit = i.get("unit") + "";
                    originalUnit[0] = unit;

                    int unitPos = unitList.indexOf(unit);
                    if (unitPos >= 0) spUnit.setSelection(unitPos);
                    break;
                }
            }
            etQuantity.requestFocus();
        });

        spUnit.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

                String newUnit = parent.getItemAtPosition(position).toString();
                String oldUnit = (originalUnit[0] == null) ? "" : originalUnit[0];

                if (oldUnit.isEmpty()) {
                    originalUnit[0] = newUnit;
                    return;
                }

                String priceText = etPrice.getText().toString().trim();
                if (priceText.isEmpty()) return;

                double currentPrice;
                try {
                    currentPrice = Double.parseDouble(priceText);
                } catch (Exception e) {
                    return;
                }

                if (oldUnit.equals(newUnit)) return;

                db.collection("unit_conversions")
                        .whereEqualTo("higher_unit", oldUnit)
                        .whereEqualTo("lower_unit", newUnit)
                        .get()
                        .addOnSuccessListener(oldToNew -> {

                            if (!oldToNew.isEmpty()) {
                                Double rateObj = oldToNew.getDocuments().get(0).getDouble("conversion_rate");
                                double rate = rateObj != null ? rateObj : 1.0;

                                double convertedPrice = currentPrice / rate;
                                etPrice.setText(String.format(Locale.getDefault(), "%.2f", convertedPrice));

                                priceWasAutoConverted[0] = true;

                                // *********** FIX: UPDATE ORIGINAL UNIT ***********
                                originalUnit[0] = newUnit;

                                return;
                            }

                            db.collection("unit_conversions")
                                    .whereEqualTo("higher_unit", newUnit)
                                    .whereEqualTo("lower_unit", oldUnit)
                                    .get()
                                    .addOnSuccessListener(newToOld -> {

                                        if (!newToOld.isEmpty()) {
                                            Double rateObj2 = newToOld.getDocuments().get(0).getDouble("conversion_rate");
                                            double rate2 = rateObj2 != null ? rateObj2 : 1.0;

                                            double convertedPrice = currentPrice * rate2;
                                            etPrice.setText(String.format(Locale.getDefault(), "%.2f", convertedPrice));

                                            priceWasAutoConverted[0] = true;

                                            // *********** FIX: UPDATE ORIGINAL UNIT ***********
                                            originalUnit[0] = newUnit;

                                            return;
                                        }


                                        db.collection("unit_conversions")
                                                .whereEqualTo("fromUnit", oldUnit)
                                                .whereEqualTo("toUnit", newUnit)
                                                .get()
                                                .addOnSuccessListener(f1 -> {

                                                    if (!f1.isEmpty()) {
                                                        Double rateObj3 = f1.getDocuments().get(0).getDouble("rate");
                                                        double rate3 = rateObj3 != null ? rateObj3 : 1.0;

                                                        double convertedPrice = currentPrice / rate3;
                                                        etPrice.setText(String.format(Locale.getDefault(), "%.2f", convertedPrice));

                                                        priceWasAutoConverted[0] = true;

                                                        // *********** FIX: UPDATE ORIGINAL UNIT ***********
                                                        originalUnit[0] = newUnit;

                                                        return;
                                                    }
                                                    db.collection("unit_conversions")
                                                            .whereEqualTo("fromUnit", newUnit)
                                                            .whereEqualTo("toUnit", oldUnit)
                                                            .get()
                                                            .addOnSuccessListener(f2 -> {

                                                                if (!f2.isEmpty()) {
                                                                    Double rateObj4 = f2.getDocuments().get(0).getDouble("rate");
                                                                    double rate4 = rateObj4 != null ? rateObj4 : 1.0;

                                                                    double convertedPrice = currentPrice * rate4;
                                                                    etPrice.setText(String.format(Locale.getDefault(), "%.2f", convertedPrice));

                                                                    priceWasAutoConverted[0] = true;

                                                                    originalUnit[0] = newUnit;

                                                                } else {
                                                                    priceWasAutoConverted[0] = false;
                                                                }
                                                            });
                                                });
                                    });
                        });
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });


        // Add button — when saving we DO NOT change qty, only price is read from etPrice (which may have been auto-converted)
        btnAdd.setOnClickListener(v -> {

            String itemText = etSearchItem.getText().toString().trim();
            String qtyStr = etQuantity.getText().toString().trim();
            String priceStr = etPrice.getText().toString().trim();

            // VALIDATION
            boolean existsInDB = false;
            for (Map<String, Object> i : itemList) {
                if (i.get("itemName").toString().equalsIgnoreCase(itemText)) {
                    existsInDB = true;
                    break;
                }
            }
            if (!existsInDB) {
                Toast.makeText(this,
                        "Error: Item '" + itemText + "' not found in database.",
                        Toast.LENGTH_LONG).show();
                return;
            }

            if (itemText.isEmpty() || qtyStr.isEmpty() || priceStr.isEmpty()) {
                Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            // Parse quantity and price (quantity MUST be kept as user-entered)
            double qtyVal;
            try {
                qtyVal = Double.parseDouble(qtyStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid quantity", Toast.LENGTH_SHORT).show();
                return;
            }

            double priceVal;
            try {
                priceVal = Double.parseDouble(priceStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid price", Toast.LENGTH_SHORT).show();
                return;
            }

            // Round price and total to 2 decimals before saving (prevents long decimal values)
            double priceRounded = Math.round(priceVal * 100.0) / 100.0;
            double totalRounded = Math.round((qtyVal * priceRounded) * 100.0) / 100.0;

            Map<String, Object> item = new HashMap<>();
            item.put("itemName", itemText);
            item.put("quantity", qtyVal); // DO NOT change quantity
            item.put("price", priceRounded); // save updated price
            item.put("total", totalRounded);
            item.put("unit", spUnit.getSelectedItem() + "");

            if (existing == null) {
                item.put("sno", getNextAvailableSNo());
                saleItems.add(item);
            } else {
                item.put("sno", existing.get("sno"));
                saleItems.set(editPosition, item);
            }

            sortItemsAndRecalculate();
            adapter.notifyDataSetChanged();

            // Reset dialog fields
            etSearchItem.setText("");
            etQuantity.setText("");
            etPrice.setText("");
            etSearchItem.requestFocus();
            etSearchItem.showDropDown();
        });

        btnComplete.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
        dialog.getWindow().setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
        );
    }


    private void recalculateTotal() {
        try {
            // --- 1. CRASH PREVENTION CHECKS ---
            if (df == null || etDiscount == null || etTax == null ||
                    etDiscountPercent == null || etTaxPercent == null ||
                    tvGrandTotal == null || tvFinalTotal == null) {
                Log.e("RecalcError", "One or more views not initialized!");
                return;
            }

            // --- 2. Calculate Subtotal ---
            subTotal = 0;
            if (saleItems != null) {
                for (Map<String, Object> item : saleItems) {
                    subTotal += parseDoubleSafe(item.get("total"));
                }
            }

        double discountPercent = parseDoubleSafe(etDiscountPercent.getText().toString());
            double taxPercent = parseDoubleSafe(etTaxPercent.getText().toString());

            double discountAmountInput = parseDoubleSafe(etDiscount.getText().toString());
            double taxAmountInput = parseDoubleSafe(etTax.getText().toString());

            double finalDiscount;
            double finalTax;

            // --- 4. Apply Discount Logic ---
            if (discountPercent != 0) { // Use != to allow for negative percentages
                finalDiscount = subTotal * (discountPercent / 100.0);
                String formatted = df.format(finalDiscount);
                if (!etDiscount.getText().toString().equals(formatted)) {
                    etDiscount.removeTextChangedListener(discountWatcher);
                    etDiscount.setText(formatted);
                    etDiscount.setSelection(formatted.length());
                    etDiscount.addTextChangedListener(discountWatcher);
                }
            } else {
                finalDiscount = discountAmountInput;
            }

            double totalAfterDiscount = subTotal - finalDiscount;

            // --- 5. Apply Tax Logic ---
            if (taxPercent != 0) { // Use != to allow for negative percentages
                finalTax = totalAfterDiscount * (taxPercent / 100.0);
                String formatted = df.format(finalTax);
                if (!etTax.getText().toString().equals(formatted)) {
                    etTax.removeTextChangedListener(taxWatcher);
                    etTax.setText(formatted);
                    etTax.setSelection(formatted.length());
                    etTax.addTextChangedListener(taxWatcher);
                }
            } else {
                finalTax = taxAmountInput;
            }

            // --- 6. Calculate Final Total ---
            finalCalculatedTotal = totalAfterDiscount + finalTax;

            // --- 7. Display Results ---
            tvGrandTotal.setText("Sub Total: ₹" + df.format(subTotal));
            tvFinalTotal.setText("Final Total: ₹" + df.format(finalCalculatedTotal));

        } catch (Exception e) {
            Log.e("RecalcError", "Crash in recalculateTotal(): " + e.getMessage());
            e.printStackTrace();
        }
    }

    private double parseDoubleSafe(Object o) {
        if (o == null) return 0;
        try { return Double.parseDouble(o.toString()); }
        catch (Exception e) { return 0; }
    }

    private void saveFullSale() {

        if (saleItems.isEmpty()) {
            Toast.makeText(this, "No items added!", Toast.LENGTH_SHORT).show();
            return;
        }

        sortItemsAndRecalculate();

        String invoiceNo = etInvoice.getText().toString().trim();
        String date = etDate.getText().toString().trim();
        String partyText = etSearchParty.getText().toString().trim();

        double discount = parseDoubleSafe(etDiscount.getText().toString());
        double tax = parseDoubleSafe(etTax.getText().toString());

        if (partyText.isEmpty()) {
            Toast.makeText(this, "Please select a party", Toast.LENGTH_SHORT).show();
            return;
        }

        // ✅ CHECK IF PARTY EXISTS IN FIRESTORE BEFORE SAVING
        db.collection("Party")
                .whereEqualTo("partyName", partyText)
                .get()
                .addOnCompleteListener(task -> {

                    if (!task.isSuccessful() || task.getResult().isEmpty()) {
                        Toast.makeText(this, "Party does not exist. Cannot save sale.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // ---------------------------
                    db.collection("Sales").document(invoiceNo).get().addOnSuccessListener(existing -> {

                        final ArrayList<Map<String, Object>> oldItems = new ArrayList<>();
                        double oldTotal = 0;
                        String oldParty = "";
                        if (existing.exists()) {
                            Map<String, Object> oldData = existing.getData();
                            if (oldData != null) {
                                if (oldData.containsKey("items")) {
                                    oldItems.addAll((ArrayList<Map<String, Object>>) oldData.get("items"));
                                }
                                try {
                                    oldParty = oldData.get("party") + "";
                                    oldTotal = parseDoubleSafe(oldData.get("grandTotal"));
                                } catch (Exception ignored) {}
                            }
                        }

                        // Recalculate with new items
                        recalculateTotal();
                        double diff = finalCalculatedTotal - oldTotal;

                        // Build sale data
                        Map<String, Object> saleData = new HashMap<>();
                        saleData.put("invoiceNo", invoiceNo);
                        saleData.put("date", date);
                        saleData.put("party", partyText);
                        saleData.put("items", saleItems);
                        saleData.put("discount", discount);
                        saleData.put("tax", tax);
                        saleData.put("grandTotal", finalCalculatedTotal);
                        saleData.put("remarks", etRemarks.getText().toString().trim());

                        String finalOldParty = oldParty;
                        double finalOldTotal = oldTotal;
                        db.collection("Sales").document(invoiceNo).set(saleData)
                                .addOnSuccessListener(aVoid -> {
                                    // --- NEW CODE: Correct party balance update ---
                                    if (existing.exists()) {
                                        if (!finalOldParty.equals(partyText)) {
                                            // 1. Reverse old party (subtract old amount)
                                            updatePartyBalance(finalOldParty, -finalOldTotal);

                                            // 2. Add amount to new party
                                            updatePartyBalance(partyText, finalCalculatedTotal);
                                        } else {
                                            // Same party → Only update difference
                                            updatePartyBalance(partyText, diff);
                                        }
                                    } else {
                                        // New sale entry → Add full amount
                                        updatePartyBalance(partyText, finalCalculatedTotal);
                                    }


                                    updateItemStocks(oldItems , new ArrayList<>(saleItems));
                                    Map<String, Object> journalEntry = new HashMap<>();
                                    journalEntry.put("invoiceNo", invoiceNo);
                                    journalEntry.put("date", date);

                                    if (finalCalculatedTotal < 0) {
                                        journalEntry.put("from", "Sales Account");
                                        journalEntry.put("to", partyText);
                                        journalEntry.put("amount", -finalCalculatedTotal);
                                        journalEntry.put("remarks", "Sale Return for " + invoiceNo);
                                        journalEntry.put("type", "Sale Return");
                                    } else {
                                        journalEntry.put("from", partyText);
                                        journalEntry.put("to", "Sales Account");
                                        journalEntry.put("amount", finalCalculatedTotal);
                                        journalEntry.put("remarks", "Sale Entry for " + invoiceNo);
                                        journalEntry.put("type", "Sale");
                                    }

                                    db.collection("Journal").document(invoiceNo).set(journalEntry)
                                            .addOnSuccessListener(v -> {

                                                // Discount Entry
                                                if (discount != 0) {
                                                    Map<String, Object> discountEntry = new HashMap<>();
                                                    discountEntry.put("invoiceNo", invoiceNo + "_DISCOUNT");
                                                    discountEntry.put("date", date);

                                                    if (discount < 0) {
                                                        discountEntry.put("from", "Discount Allowed");
                                                        discountEntry.put("to", "Sales Account");
                                                        discountEntry.put("amount", -discount);
                                                        discountEntry.put("remarks", "Discount Reversal for " + invoiceNo);
                                                        discountEntry.put("type", "Discount Reversal");
                                                    } else {
                                                        discountEntry.put("from", "Sales Account");
                                                        discountEntry.put("to", "Discount Allowed");
                                                        discountEntry.put("amount", discount);
                                                        discountEntry.put("remarks", "Discount for " + invoiceNo);
                                                        discountEntry.put("type", "Discount");
                                                    }

                                                    db.collection("Journal")
                                                            .document(invoiceNo + "_DISCOUNT")
                                                            .set(discountEntry);

                                                } else {
                                                    db.collection("Journal")
                                                            .document(invoiceNo + "_DISCOUNT")
                                                            .delete();
                                                }

                                                // Tax Entry
                                                if (tax != 0) {
                                                    Map<String, Object> taxEntry = new HashMap<>();
                                                    taxEntry.put("invoiceNo", invoiceNo + "_TAX");
                                                    taxEntry.put("date", date);

                                                    if (tax < 0) {
                                                        taxEntry.put("from", "Tax Payable");
                                                        taxEntry.put("to", "Sales Account");
                                                        taxEntry.put("amount", -tax);
                                                        taxEntry.put("remarks", "Tax Reversal for " + invoiceNo);
                                                        taxEntry.put("type", "Tax Reversal");
                                                    } else {
                                                        taxEntry.put("from", "Sales Account");
                                                        taxEntry.put("to", "Tax Payable");
                                                        taxEntry.put("amount", tax);
                                                        taxEntry.put("remarks", "Tax for " + invoiceNo);
                                                        taxEntry.put("type", "Tax");
                                                    }

                                                    db.collection("Journal")
                                                            .document(invoiceNo + "_TAX")
                                                            .set(taxEntry);

                                                } else {
                                                    db.collection("Journal")
                                                            .document(invoiceNo + "_TAX")
                                                            .delete();
                                                }
                                            });

                                    Toast.makeText(this, "Sale saved successfully!", Toast.LENGTH_SHORT).show();

                                    DocumentReference invRef = db.collection("invno").document("S-");
                                    invRef.get().addOnSuccessListener(doc -> {
                                        if (doc.exists() && doc.contains("number")) {
                                            long savedNum = doc.getLong("number");
                                            etInvoice.setText("S-"+savedNum);
                                            loadSaleByInvoice("S-"+savedNum);
                                            invRef.update("number", savedNum +1);

                                        }
                                    });

                                    // SEND TO PRINT PREVIEW
                                    BillData bill = new BillData();
                                    bill.invoiceNo = invoiceNo;
                                    bill.date = date;
                                    bill.party = partyText;
                                    bill.items = new ArrayList<>(saleItems);
                                    bill.discount = discount;
                                    bill.tax = tax;
                                    bill.total = finalCalculatedTotal - tax + discount;
                                    bill.grandTotal = finalCalculatedTotal;
                                    bill.remarks = etRemarks.getText().toString().trim();

                                    Intent intent = new Intent(sale.this, PrintPreviewActivit.class);
                                    intent.putExtra("billData", bill);
                                    startActivity(intent);

                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, "Failed to save: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    });
                });
    }

    private void confirmAndDeleteSale() {
        String invoiceNo = etInvoice.getText().toString();
        new AlertDialog.Builder(this)
                .setTitle("Confirm Deletion")
                .setMessage("Are you sure you want to delete Sale Invoice: " + invoiceNo + "? This action cannot be undone, and stock/balance will be adjusted.")
                .setPositiveButton("Yes, Delete", (dialog, which) -> deleteSale(invoiceNo))
                .setNegativeButton("Cancel", null)
                .show();
    }
    private void deleteSale(String invoiceNo) {
        db.collection("Sales").document(invoiceNo).get().addOnSuccessListener(d -> {
            if (!d.exists()) {
                Toast.makeText(this, "Invoice not found to delete.", Toast.LENGTH_SHORT).show();
                return;
            }

            Map<String, Object> oldData = d.getData();
            final ArrayList<Map<String, Object>> oldItems = new ArrayList<>();
            double total = 0;
            String partyName = "";

            if (oldData != null) {
                partyName = oldData.get("party") + "";
                total = parseDoubleSafe(oldData.get("grandTotal"));

                if (oldData.containsKey("items")) {
                    oldItems.addAll((ArrayList<Map<String, Object>>) oldData.get("items"));
                }
            } else {
                Toast.makeText(this, "Error reading old sale data.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Make final copies for lambda
            final double finalTotal = total;
            final String finalPartyName = partyName;

            db.collection("Sales").document(invoiceNo).delete()
                    .addOnSuccessListener(aVoid -> {
                        updateItemStocks(oldItems, new ArrayList<>()); // Restore stock from old items
                        updatePartyBalance(finalPartyName, -finalTotal); // Reverse balance
                        db.collection("Journal").document(invoiceNo).delete();
                        db.collection("Journal").document(invoiceNo + "_DISCOUNT").delete();
                        db.collection("Journal").document(invoiceNo + "_TAX").delete();
                        Toast.makeText(this, "Sale invoice " + invoiceNo + " successfully deleted.", Toast.LENGTH_LONG).show();
                        navigateInvoice(true);
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Error deleting sale: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        });
    }
    private void updateItemStocks(ArrayList<Map<String, Object>> oldItems,
                                  ArrayList<Map<String, Object>> newItems) {

        // CASE 1 → Build quick access maps
        Map<String, Map<String, Object>> oldMap = new HashMap<>();
        for (Map<String, Object> m : oldItems) {
            oldMap.put(m.get("itemName") + "", m);
        }

        Map<String, Map<String, Object>> newMap = new HashMap<>();
        for (Map<String, Object> m : newItems) {
            newMap.put(m.get("itemName") + "", m);
        }

        // CASE 2 → Build combined item list (union of old + new)
        Set<String> allItemNames = new HashSet<>();
        allItemNames.addAll(oldMap.keySet());
        allItemNames.addAll(newMap.keySet());

        // CASE 3 → Loop all involved items
        for (String itemName : allItemNames) {

            Map<String, Object> oldItem = oldMap.get(itemName);
            Map<String, Object> newItem = newMap.get(itemName);

            double oldQty, newQty;
            String oldUnit, newUnit;

            if (oldItem != null) {
                oldQty = parseDoubleSafe(oldItem.get("quantity"));
                oldUnit = oldItem.get("unit") + "";
            } else {
                oldQty = 0;
                oldUnit = "";
            }

            if (newItem != null) {
                newQty = parseDoubleSafe(newItem.get("quantity"));
                newUnit = newItem.get("unit") + "";
            } else {
                newQty = 0;
                newUnit = "";
            }

            // Fetch item stock
            db.collection("items")
                    .whereEqualTo("itemName", itemName)
                    .get()
                    .addOnSuccessListener(snapshot -> {

                        if (snapshot.isEmpty()) return;

                        DocumentSnapshot doc = snapshot.getDocuments().get(0);
                        double itemStock = parseDoubleSafe(doc.get("quantity"));
                        String itemUnit = doc.getString("unit");

                        // ----------- STOCK ADJUSTMENT LOGIC ------------ //

                        // CASE A: Item REMOVED from bill (old exists, new missing)
                        if (oldItem != null && newItem == null) {

                            convertToItemUnit(itemUnit, oldUnit, oldQty, convertedOld -> {
                                double finalStock = itemStock + convertedOld;
                                db.collection("items").document(doc.getId())
                                        .update("quantity", round2(finalStock));
                            });
                            return;
                        }

                        // CASE B: New item added in bill (old missing, new exists)
                        if (oldItem == null && newItem != null) {

                            convertToItemUnit(itemUnit, newUnit, newQty, convertedNew -> {
                                double finalStock = itemStock - convertedNew;
                                db.collection("items").document(doc.getId())
                                        .update("quantity", round2(finalStock));
                            });
                            return;
                        }

                        // CASE C: Item edited (old exists AND new exists)
                        convertToItemUnit(itemUnit, oldUnit, oldQty, convertedOld -> {
                            convertToItemUnit(itemUnit, newUnit, newQty, convertedNew -> {

                                double finalStock = itemStock + convertedOld - convertedNew;

                                db.collection("items").document(doc.getId())
                                        .update("quantity", round2(finalStock));
                            });
                        });

                    });
        }
    }
    private void convertToItemUnit(String itemUnit, String billUnit, double qty,
                                   OnQuantityConverted callback) {

        if (itemUnit.equalsIgnoreCase(billUnit)) {
            callback.onConverted(qty);
            return;
        }

        // CASE 1: itemUnit → billUnit (billUnit is lower)
        db.collection("unit_conversions")
                .whereEqualTo("higher_unit", itemUnit)
                .whereEqualTo("lower_unit", billUnit)
                .get()
                .addOnSuccessListener(h -> {
                    if (!h.isEmpty()) {
                        double r = h.getDocuments().get(0).getDouble("conversion_rate");
                        // bill qty → item qty
                        callback.onConverted(qty / r);
                        return;
                    }

                    // CASE 2: billUnit → itemUnit (billUnit is lower)
                    db.collection("unit_conversions")
                            .whereEqualTo("higher_unit", billUnit)
                            .whereEqualTo("lower_unit", itemUnit)
                            .get()
                            .addOnSuccessListener(l -> {
                                if (!l.isEmpty()) {
                                    double r = l.getDocuments().get(0).getDouble("conversion_rate");
                                    // bill qty → item qty
                                    callback.onConverted(qty * r);
                                    return;
                                }

                                // No conversion found
                                callback.onConverted(qty);
                            });
                });
    }
    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }
    interface OnQuantityConverted { void onConverted(double convertedQty); }
    private void updatePartyBalance(String partyText, double diffAmount) {
        if (Math.abs(diffAmount) < 0.001) return;

        final String partyName = partyText.split(",")[0].trim();
        db.collection("Party")
                .whereEqualTo("partyName", partyName)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        for (DocumentSnapshot d : querySnapshot) {
                            double currentBal = parseDoubleSafe(d.get("currentBalance"));
                            double newBal = currentBal + diffAmount;
                            db.collection("Party").document(d.getId())
                                    .update("currentBalance", newBal);
                            break;
                        }
                    }
                });
    }

    private void navigateInvoice(boolean isNext) {
        String currentRaw = (etInvoice.getText() != null) ? etInvoice.getText().toString().trim() : "";
        if (!currentRaw.startsWith("S-")) {
            Toast.makeText(this, "Invalid invoice format!", Toast.LENGTH_SHORT).show();
            Log.d("NavigateInv", "Invalid invoice format: '" + currentRaw + "'");
            return;
        }

        final Integer currentNum;
        try {
            currentNum = Integer.parseInt(currentRaw.substring(2).trim());
        } catch (Exception e) {
            Toast.makeText(this, "Invalid invoice number!", Toast.LENGTH_SHORT).show();
            Log.e("NavigateInv", "Failed parse current invoice: '" + currentRaw + "'", e);
            return;
        }

        // Fetch all Sales documents ONCE
        db.collection("Sales")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot == null) {
                        Toast.makeText(this, "No invoices found.", Toast.LENGTH_SHORT).show();
                        Log.w("NavigateInv", "querySnapshot == null");
                        return;
                    }

                    ArrayList<Integer> nums = new ArrayList<>();

                    for (DocumentSnapshot d : querySnapshot) {
                        String inv = d.getString("invoiceNo");
                        if (inv == null) continue;
                        inv = inv.trim();
                        if (!inv.startsWith("S-")) continue;
                        try {
                            int n = Integer.parseInt(inv.substring(2).trim());
                            nums.add(n);
                        } catch (Exception ex) {
                            Log.w("NavigateInv", "Skipping unparsable invoice: '" + inv + "'");
                        }
                    }

                    if (nums.isEmpty()) {
                        Toast.makeText(this, "No invoices exist!", Toast.LENGTH_SHORT).show();
                        Log.d("NavigateInv", "No numeric invoices parsed from Sales.");
                        return;
                    }

                    Collections.sort(nums);

                    // Debug: show list in log
                    Log.d("NavigateInv", "Invoices found: " + nums.toString());

                    // Try to find exact index
                    int index = nums.indexOf(currentNum);

                    if (index == -1) {
                        int insertion = Collections.binarySearch(nums, currentNum);
                        int insertionPoint = (insertion < 0) ? (-(insertion + 1)) : insertion;
                        Log.d("NavigateInv", "Current invoice not in list. insertionPoint=" + insertionPoint);

                        if (isNext) {
                            // Next means choose invoice at insertionPoint (first greater than currentNum)
                            if (insertionPoint >= nums.size()) {
                                Toast.makeText(this, "No invoice after this!", Toast.LENGTH_SHORT).show();
                                return;
                            } else {
                                int newInvNumber = nums.get(insertionPoint);
                                String newInvoiceNo = "S-" + newInvNumber;
                                etInvoice.setText(newInvoiceNo);
                                loadSaleByInvoice(newInvoiceNo);
                                return;
                            }
                        } else {
                            // Prev: choose invoice at insertionPoint-1 (largest less than currentNum)
                            int prevIndex = insertionPoint - 1;
                            if (prevIndex < 0) {
                                Toast.makeText(this, "No invoice before this!", Toast.LENGTH_SHORT).show();
                                return;
                            } else {
                                int newInvNumber = nums.get(prevIndex);
                                String newInvoiceNo = "S-" + newInvNumber;
                                etInvoice.setText(newInvoiceNo);
                                loadSaleByInvoice(newInvoiceNo);
                                return;
                            }
                        }
                    }

                    // index found
                    int newIndex = isNext ? index + 1 : index - 1;

                    if (newIndex < 0) {
                        Toast.makeText(this, "No invoice before this!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (newIndex >= nums.size()) {

                        DocumentReference invRef = db.collection("invno").document("S-");

                        invRef.get().addOnSuccessListener(doc -> {
                            if (doc.exists() && doc.contains("number")) {

                                long savedNum = doc.getLong("number");

                                String newInvoiceNo = "S-" + (savedNum-1);
                                etInvoice.setText(newInvoiceNo);
                                loadSaleByInvoice(newInvoiceNo);
                            }
                        });

                        return;
                    }

                    int newInvNumber = nums.get(newIndex);
                    String newInvoiceNo = "S-" + newInvNumber;
                    etInvoice.setText(newInvoiceNo);
                    loadSaleByInvoice(newInvoiceNo);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load invoices: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e("NavigateInv", "Failed to read Sales collection", e);
                });
    }

    private void loadSaleByInvoice(String invoiceNo) {
        db.collection("Sales").document(invoiceNo).get()
                .addOnSuccessListener(d -> {
                    saleItems.clear();

                    // Reset all editable fields
                    safeSetEditText(etDiscount, "");
                    safeSetEditText(etTax, "");
                    safeSetEditText(etDiscountPercent, "");
                    safeSetEditText(etTaxPercent, "");
                    safeSetEditText(etRemarks, "");

                    if (d.exists()) {
                        btnDeleteSale.setVisibility(View.VISIBLE);
                        btnSave.setText("Update");

                        Map<String, Object> saleData = d.getData();
                        if (saleData != null && saleData.containsKey("items")) {
                            ArrayList<Map<String, Object>> items =
                                    (ArrayList<Map<String, Object>>) saleData.get("items");
                            saleItems.addAll(items);
                        }

                        // --- Fetch main Journal entry (party & date) ---
                        db.collection("Journal").document(invoiceNo).get()
                                .addOnSuccessListener(journalDoc -> {
                                    if (journalDoc.exists()) {
                                        String from = journalDoc.getString("from");
                                        String to = journalDoc.getString("to");

                                        if ("Sales Account".equals(from)) {
                                            etSearchParty.setText(to);
                                        } else {
                                            etSearchParty.setText(from);
                                        }

                                        etDate.setText(journalDoc.getString("date"));
                                    } else {
                                        etSearchParty.setText("");
                                        etDate.setText(new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date()));
                                    }

                                    // --- Fetch TAX entry ---
                                    db.collection("Journal").document(invoiceNo + "_TAX").get()
                                            .addOnSuccessListener(taxDoc -> {
                                                if (taxDoc.exists()) {
                                                    Object taxAmt = taxDoc.get("amount");
                                                    if (taxAmt != null)
                                                        safeSetEditText(etTax, String.valueOf(taxAmt));
                                                }

                                                // --- Fetch DISCOUNT entry ---
                                                db.collection("Journal").document(invoiceNo + "_DISCOUNT").get()
                                                        .addOnSuccessListener(discountDoc -> {
                                                            if (discountDoc.exists()) {
                                                                Object discAmt = discountDoc.get("amount");
                                                                if (discAmt != null)
                                                                    safeSetEditText(etDiscount, String.valueOf(discAmt));
                                                            }
                                                            // Once all fetched, recalculate
                                                            sortItemsAndRecalculate();
                                                            adapter.notifyDataSetChanged();
                                                        });
                                            });
                                });

                    } else {
                        // New invoice
                        btnDeleteSale.setVisibility(View.GONE);
                        btnSave.setText("Save");

                        etSearchParty.setText("");
                        etDate.setText(new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date()));

                        sortItemsAndRecalculate();
                        adapter.notifyDataSetChanged();
                    }
                });
    }

    // helper that safely sets EditText without triggering watchers (removes/re-adds appropriate watcher)
    private void safeSetEditText(EditText et, String text) {
        if (et == null) return;
        android.text.TextWatcher w = null;
        if (et == etDiscount) w = discountWatcher;
        else if (et == etTax) w = taxWatcher;
        else if (et == etDiscountPercent) w = discountPercentWatcher;
        else if (et == etTaxPercent) w = taxPercentWatcher;

        if (w != null) et.removeTextChangedListener(w);
        et.setText(text);
        et.setSelection(Math.min(text.length(), Math.max(0, text.length())));
        if (w != null) et.addTextChangedListener(w);
    }

    private int getNextAvailableSNo() {
        List<Integer> snoList = new ArrayList<>();
        int maxSno = 0;

        for (Map<String, Object> item : saleItems) {
            int sno = 0;
            if (item.containsKey("sno")) {
                Object snoObj = item.get("sno");
                try {
                    sno = Integer.parseInt(snoObj.toString());
                } catch (NumberFormatException ignored) {}
            }
            if (sno > 0) {
                snoList.add(sno);
                if (sno > maxSno) maxSno = sno;
            }
        }

        if (snoList.isEmpty()) return 1;

        Collections.sort(snoList);
        int expected = 1;
        for (int sno : snoList) {
            if (sno != expected) return expected; // Found the gap
            expected++;
        }
        // No gaps found, return the next sequential number after the max
        return maxSno + 1;
    }


    private void sortItemsAndRecalculate() {
        // 1. Sort by the existing S.No. field
        sortItemsBySNo();

        // 2. Recalculate total
        recalculateTotal();
    }

    private void sortItemsBySNo() {
        Collections.sort(saleItems, (a, b) -> {
            int sno1 = 0;
            int sno2 = 0;
            // Defensive parsing
            try { sno1 = Integer.parseInt(a.getOrDefault("sno", 0).toString()); } catch (Exception ignored) {}
            try { sno2 = Integer.parseInt(b.getOrDefault("sno", 0).toString()); } catch (Exception ignored) {}
            return Integer.compare(sno1, sno2);
        });
    }

    class ItemAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return saleItems.size();
        }

        @Override
        public Object getItem(int i) {
            return saleItems.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View v, ViewGroup parent) {
            if (v == null)
                v = getLayoutInflater().inflate(R.layout.item_sale_list, parent, false);

            Map<String, Object> item = saleItems.get(i);

            TextView tvSNo = v.findViewById(R.id.tvSNo);
            TextView tvItemName = v.findViewById(R.id.tvItemName);
            TextView tvQtyPrice = v.findViewById(R.id.tvQtyPrice);
            TextView tvTotal = v.findViewById(R.id.tvTotal);

            String snoText = (i + 1) + "";
            if (item.containsKey("sno")) {
                snoText = item.get("sno").toString();
            }

            tvSNo.setText(snoText);
            tvItemName.setText(String.valueOf(item.get("itemName")));
            tvQtyPrice.setText(item.get("quantity") + " " + item.get("unit") + " x ₹" + item.get("price"));
            tvTotal.setText("₹" + df.format(parseDoubleSafe(item.get("total"))));

            return v;
        }
    }
}
