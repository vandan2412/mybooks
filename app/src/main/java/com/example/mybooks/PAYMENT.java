package com.example.mybooks;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PAYMENT extends AppCompatActivity {

    EditText tvDate, tvInvoice;
    TextInputEditText etRemarks;

    AutoCompleteTextView party1, party2, party3, party4, party5, party6;
    EditText amt1, amt2, amt3, amt4, amt5, amt6;
    List<AutoCompleteTextView> partyFields;
    List<EditText> amtList;

    MaterialButton btnPrev, btnNext, btnCancel, btnSave, btnDelete;

    FirebaseFirestore db = FirebaseFirestore.getInstance();

    List<String> partyNames = new ArrayList<>();
    Map<String, DocumentSnapshot> partySnapshots = new HashMap<>();

    private boolean isUpdatingAmounts = false;
    private String currentInvoiceBase = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment);

        bindViews();
        setupAutoDate();
        loadPartyNamesAndBalances();
        generateInvoiceNumber();
        setupFocusAndWatchers();
        setupButtons();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public  void handleOnBackPressed() {
                rollbackInvoiceIfNeeded();
                finish();
            }
        });
    }

    private void bindViews() {
        tvDate = findViewById(R.id.tvDate);
        tvInvoice = findViewById(R.id.tvInvoice);
        etRemarks = findViewById(R.id.etRemarks);

        party1 = findViewById(R.id.party1);
        party2 = findViewById(R.id.party2);
        party3 = findViewById(R.id.party3);
        party4 = findViewById(R.id.party4);
        party5 = findViewById(R.id.party5);
        party6 = findViewById(R.id.party6);

        amt1 = findViewById(R.id.amt1);
        amt2 = findViewById(R.id.amt2);
        amt3 = findViewById(R.id.amt3);
        amt4 = findViewById(R.id.amt4);
        amt5 = findViewById(R.id.amt5);
        amt6 = findViewById(R.id.amt6);

        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);
        btnCancel = findViewById(R.id.btnCancel);
        btnSave = findViewById(R.id.btnSave);
        btnDelete = findViewById(R.id.btnDelete);

        partyFields = Arrays.asList(party1, party2, party3, party4, party5, party6);
        amtList = Arrays.asList(amt1, amt2, amt3, amt4, amt5, amt6);
    }
    private void rollbackInvoiceIfNeeded() {

        String inv = tvInvoice.getText().toString();   // example: PY-5
        if (inv == null || !inv.startsWith("PY-")) return;

        int currentNum;
        try {
            currentNum = Integer.parseInt(inv.substring(3));
        } catch (Exception e) {
            return;
        }

        DocumentReference invRef = db.collection("invno").document("PY-");

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
    private void setupAutoDate() {
        tvDate.setText(new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date()));
        tvDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(PAYMENT.this, (view, year, month, dayOfMonth) ->
                    tvDate.setText(String.format(Locale.getDefault(), "%02d-%02d-%04d", dayOfMonth, month + 1, year)),
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });
    }

    private void loadPartyNamesAndBalances() {
        db.collection("Party").get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Toast.makeText(this, "Failed to load parties.", Toast.LENGTH_SHORT).show();
                return;
            }
            partyNames.clear();
            partySnapshots.clear();
            List<String> partyDisplay = new ArrayList<>();
            for (QueryDocumentSnapshot ds : task.getResult()) {
                String name = ds.getString("partyName");
                if (name == null) continue;
                double bal = ds.getDouble("currentBalance") != null ? ds.getDouble("currentBalance") : 0.0;
                partyNames.add(name);
                partyDisplay.add(name + " (Bal: " + formatDouble(bal) + ")");
                partySnapshots.put(name, ds);
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, partyDisplay);
            for (AutoCompleteTextView act : partyFields) {
                act.setAdapter(adapter);
                act.setOnItemClickListener((parent, view, position, id) -> {
                    String selectedDisplay = adapter.getItem(position);
                    if (selectedDisplay != null) {
                        String selectedName = selectedDisplay.substring(0, selectedDisplay.indexOf(" ("));
                        act.setText(selectedName);
                        act.setSelection(selectedName.length());
                        focusNextAfterPartySelection(act);
                    }
                });
            }
        });
    }

    private String formatDouble(double v) {
        return v == (long) v ? String.format(Locale.getDefault(), "%d", (long) v) : String.format(Locale.getDefault(), "%.2f", v);
    }
    private void generateInvoiceNumber() {
        DocumentReference invRef = db.collection("invno").document("PY-");

        invRef.get().addOnSuccessListener(doc -> {

            long last = 0;

            if (doc.exists() && doc.contains("number")) {
                last = doc.getLong("number");
            }

            long next = last + 1;
            String nextInvoice = "PY-" + last;
            tvInvoice.setText(nextInvoice);
            clearFieldsForNewInvoice();

            invRef.update("number", next)
                    .addOnFailureListener(e -> {
                        invRef.set(Collections.singletonMap("number", next));
                    });

        }).addOnFailureListener(e -> {
            String defaultInvoice = "PY-1";
            tvInvoice.setText(defaultInvoice);
            clearFieldsForNewInvoice();
            invRef.set(Collections.singletonMap("number", 1L));
        });


    }

    private void setupFocusAndWatchers() {
        party1.requestFocus();

        party1.setOnItemClickListener((parent, view, position, id) -> amt1.requestFocus());

        party1.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                amt1.requestFocus();
                return true;
            }
            return false;
        });

        amt1.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE) {
                double toAmt = getDoubleFromEditText(amt1);
                isUpdatingAmounts = true;
                amt2.setText(formatDouble(toAmt));
                for (int i = 3; i < amtList.size(); i++) {
                    amtList.get(i).setText("");
                }
                isUpdatingAmounts = false;
                party2.requestFocus();
                return true;
            }
            return false;
        });

        for (int i = 1; i < partyFields.size(); i++) {
            int idx = i;
            partyFields.get(i).setOnItemClickListener((parent, view, position, id) -> amtList.get(idx).requestFocus());
        }

        for (int i = 1; i < amtList.size(); i++) {
            int idx = i;
            amtList.get(i).addTextChangedListener(new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (isUpdatingAmounts) return;
                    // Optionally you can remove or keep live recalculation here if desired
                }
            });

            amtList.get(i).setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE) {
                    double toAmount = getDoubleFromEditText(amt1);
                    double sumBefore = 0;
                    for (int j = 1; j < amtList.size(); j++) {
                        if (j != idx) sumBefore += getDoubleFromEditText(amtList.get(j));
                    }
                    double editedVal = getDoubleFromEditText(amtList.get(idx));
                    double totalFrom = sumBefore + editedVal;
                    double difference = toAmount - totalFrom;

                    if (difference != 0 && (idx + 1) < amtList.size()) {
                        amtList.get(idx + 1).setText(formatDouble(difference));
                    }

                    int nextPartyIndex = idx + 1;
                    if (nextPartyIndex < partyFields.size()) {
                        partyFields.get(nextPartyIndex).requestFocus();
                    }
                    return true;
                }
                return false;
            });
        }
    }



    private void setupButtons() {
        btnPrev.setOnClickListener(v ->  navigateInvoice(false));
        btnNext.setOnClickListener(v -> navigateInvoice(true));
        btnCancel.setOnClickListener(v -> {
            rollbackInvoiceIfNeeded();
            finish();        });
        btnSave.setOnClickListener(v -> attemptSave());
        btnDelete.setOnClickListener(v -> deleteInvoice());
    }

    private void clearFieldsForNewInvoice() {
        currentInvoiceBase = tvInvoice.getText().toString();
        tvDate.setText(new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date()));
        etRemarks.setText("");
        for (int i = 0; i < partyFields.size(); i++) {
            partyFields.get(i).setText("");
            amtList.get(i).setText("");
        }
        btnDelete.setVisibility(MaterialButton.GONE);
        party1.requestFocus();
    }

    private double getDoubleFromEditText(EditText et) {
        try {
            String str = et.getText().toString().trim();
            if (str.isEmpty()) return 0.0;
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private void focusNextAfterPartySelection(AutoCompleteTextView currentField) {
        int index = partyFields.indexOf(currentField);

        if (index == 0) {
            amt1.requestFocus();
            amt1.setSelection(amt1.getText().length());   // 👉 move cursor to end
        } else if (index > 0 && index < partyFields.size()) {
            EditText nextAmt = amtList.get(index);
            nextAmt.requestFocus();
            nextAmt.setSelection(nextAmt.getText().length());  // 👉 move cursor to end
        }
    }

    private abstract class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }

    private void attemptSave() {
        double toAmount = getDoubleFromEditText(amt1);
        if (toAmount <= 0) {
            Toast.makeText(this, "To amount must be positive.", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> fromParties = new ArrayList<>();
        List<Double> fromAmounts = new ArrayList<>();
        double fromTotal = 0.0;

        for (int i = 1; i < partyFields.size(); i++) {
            String partyName = partyFields.get(i).getText().toString().trim();
            if (!partyName.isEmpty()) {
                fromParties.add(partyName);
                double amt = getDoubleFromEditText(amtList.get(i));
                if (amt <= 0) {
                    Toast.makeText(this, "From amounts must be positive.", Toast.LENGTH_SHORT).show();
                    return;
                }
                fromAmounts.add(amt);
                fromTotal += amt;
            }
        }

        if (fromParties.isEmpty()) {
            Toast.makeText(this, "At least one from-party required.", Toast.LENGTH_SHORT).show();
            return;
        }

        // ✅ Check if fromTotal matches toAmount
        if (Math.abs(fromTotal - toAmount) > 0.01) {
            Toast.makeText(this, "Sum of from amounts (" + formatDouble(fromTotal) + ") must equal to amount (" + formatDouble(toAmount) + ").", Toast.LENGTH_LONG).show();
            return;
        }

        String toParty = party1.getText().toString().trim();
        if (toParty.isEmpty()) {
            Toast.makeText(this, "To party required.", Toast.LENGTH_SHORT).show();
            return;
        }

        String invoiceBase = tvInvoice.getText().toString();
        String dateStr = tvDate.getText().toString();
        String remarks = etRemarks.getText().toString();

        WriteBatch batch = db.batch();

        // 1️⃣ Revert old balances if invoice exists
        db.collection("Journal").whereEqualTo("invoiceNo", invoiceBase).get().addOnSuccessListener(querySnapshot -> {
            for (DocumentSnapshot oldDoc : querySnapshot) {
                String oldTo = oldDoc.getString("to");
                String oldFrom = oldDoc.getString("from");
                double oldAmt = oldDoc.getDouble("amount") != null ? oldDoc.getDouble("amount") : 0.0;

                if (partySnapshots.containsKey(oldTo)) {
                    DocumentReference toRef = partySnapshots.get(oldTo).getReference();
                    batch.update(toRef, "currentBalance", FieldValue.increment(-oldAmt));
                }
                if (partySnapshots.containsKey(oldFrom)) {
                    DocumentReference fromRef = partySnapshots.get(oldFrom).getReference();
                    batch.update(fromRef, "currentBalance", FieldValue.increment(oldAmt));
                }

                batch.delete(oldDoc.getReference());
            }

            // 2️⃣ Save new journal entries and update balances
            for (int i = 0; i < fromParties.size(); i++) {
                String fromParty = fromParties.get(i);
                double fromAmount = fromAmounts.get(i);

                String docName = invoiceBase + "(" + (i + 1) + ")";
                DocumentReference docRef = db.collection("Journal").document(docName);

                Map<String, Object> journalData = new HashMap<>();
                journalData.put("amount", fromAmount);
                journalData.put("date", dateStr);
                journalData.put("from", fromParty);
                journalData.put("invoiceNo", invoiceBase);
                journalData.put("remarks", remarks);
                journalData.put("type", "payment");
                journalData.put("to", toParty);

                batch.set(docRef, journalData);

                if (partySnapshots.containsKey(toParty)) {
                    DocumentReference toRef = partySnapshots.get(toParty).getReference();
                    batch.update(toRef, "currentBalance", FieldValue.increment(fromAmount));
                }
                if (partySnapshots.containsKey(fromParty)) {
                    DocumentReference fromRef = partySnapshots.get(fromParty).getReference();
                    batch.update(fromRef, "currentBalance", FieldValue.increment(-fromAmount));
                }
            }

            batch.commit().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Toast.makeText(this, "Saved invoice entries for " + invoiceBase, Toast.LENGTH_SHORT).show();
                    btnDelete.setVisibility(MaterialButton.VISIBLE);
                    currentInvoiceBase = invoiceBase;

                    // 🔥 RELOAD PARTY BALANCES IMMEDIATELY
                    loadPartyNamesAndBalances();

                    // Go to new invoice
                    generateInvoiceNumber();
                } else {
                    Toast.makeText(this, "Failed to save invoice.", Toast.LENGTH_SHORT).show();
                }
            });

        });
    }
    private void navigateInvoice(boolean isNext) {
        String currentRaw = (tvInvoice.getText() != null) ? tvInvoice.getText().toString().trim() : "";
        if (!currentRaw.startsWith("PY-")) {
            Toast.makeText(this, "Invalid invoice format!", Toast.LENGTH_SHORT).show();
            return;
        }

        final Integer currentNum;
        try {
            currentNum = Integer.parseInt(currentRaw.substring(3).trim());
        } catch (Exception e) {
            Toast.makeText(this, "Invalid invoice number!", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("Journal")
                .get()
                .addOnSuccessListener(querySnapshot -> {

                    if (querySnapshot == null) {
                        Toast.makeText(this, "No invoices found.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    ArrayList<Integer> nums = new ArrayList<>();

                    for (DocumentSnapshot d : querySnapshot) {
                        String inv = d.getString("invoiceNo");
                        if (inv == null) continue;
                        inv = inv.trim();
                        if (!inv.startsWith("PY-")) continue;

                        try {
                            nums.add(Integer.parseInt(inv.substring(3).trim()));
                        } catch (Exception ignored) {}
                    }

                    if (nums.isEmpty()) {
                        Toast.makeText(this, "No invoices exist!", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Collections.sort(nums);

                    int index = nums.indexOf(currentNum);

                    // If current invoice is not inside list
                    if (index == -1) {

                        int insertion = Collections.binarySearch(nums, currentNum);
                        int insertionPoint = (insertion < 0) ? -(insertion + 1) : insertion;

                        if (isNext) {
                            if (insertionPoint >= nums.size()) {

                                // 👉 Load latest invoice from invno table
                                loadLatestInvoiceFromInvno();
                                return;
                            }
                            int newInvNumber = nums.get(insertionPoint);
                            String newInvoiceNo = "PY-" + newInvNumber;
                            tvInvoice.setText(newInvoiceNo);
                            loadInvoice(newInvoiceNo);
                            return;
                        } else {
                            int prevIndex = insertionPoint - 1;
                            if (prevIndex < 0) {
                                Toast.makeText(this, "No invoice before this!", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            int newInvNumber = nums.get(prevIndex);
                            String newInvoiceNo = "PY-" + newInvNumber;
                            tvInvoice.setText(newInvoiceNo);
                            loadInvoice(newInvoiceNo);
                            return;
                        }
                    }

                    // Current index found normally
                    int newIndex = isNext ? index + 1 : index - 1;

                    // PREV beyond limit
                    if (newIndex < 0) {
                        Toast.makeText(this, "No invoice before this!", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // NEXT beyond limit → load from invno
                    if (newIndex >= nums.size()) {
                        loadLatestInvoiceFromInvno();
                        return;
                    }

                    int newInvNumber = nums.get(newIndex);
                    String newInvoiceNo = "PY-" + newInvNumber;
                    tvInvoice.setText(newInvoiceNo);
                    loadInvoice(newInvoiceNo);

                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load invoices: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    // ⭐ Loads the latest available invoice from invno table
    private void loadLatestInvoiceFromInvno() {
        DocumentReference invRef = db.collection("invno").document("PY-");

        invRef.get().addOnSuccessListener(doc -> {
            if (doc.exists() && doc.contains("number")) {

                long savedNum = doc.getLong("number"); // example: 29 → last invoice will be PY-28
                String latestInvoice = "PY-" + (savedNum - 1);

                tvInvoice.setText(latestInvoice);
                loadInvoice(latestInvoice);
                clearFieldsForNewInvoice();
            }
        });
    }

    private void loadInvoice(String invoiceBase) {
        db.collection("Journal")
                .whereGreaterThanOrEqualTo("invoiceNo", invoiceBase)
                .whereLessThanOrEqualTo("invoiceNo", invoiceBase )
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                        String toParty = null;
                        String dateStr = null;
                        String remarks = null;
                        double toamt=0;
                        List<String> fromParties = new ArrayList<>();
                        List<Double> fromAmounts = new ArrayList<>();

                        for (DocumentSnapshot doc : task.getResult()) {
                            if (toParty == null) toParty = doc.getString("to");
                            if (dateStr == null) dateStr = doc.getString("date");
                            if (remarks == null) remarks = doc.getString("remarks");
                            fromParties.add(doc.getString("from"));
                            fromAmounts.add(doc.getDouble("amount") != null ? doc.getDouble("amount") : 0.0);
                            toamt+=doc.getDouble("amount") != null ? doc.getDouble("amount") : 0.0;
                        }

                        tvInvoice.setText(invoiceBase);
                        tvDate.setText(dateStr != null ? dateStr : "");
                        etRemarks.setText(remarks != null ? remarks : "");
                        party1.setText(toParty != null ? toParty : "");
                        amt1.setText(toamt+"");

                        for (int i = 0; i < partyFields.size() - 1; i++) {
                            if (i < fromParties.size()) {
                                partyFields.get(i + 1).setText(fromParties.get(i));
                                amtList.get(i + 1).setText(formatDouble(fromAmounts.get(i)));
                            } else {
                                partyFields.get(i + 1).setText("");
                                amtList.get(i + 1).setText("");
                            }
                        }
                        btnDelete.setVisibility(MaterialButton.VISIBLE);
                        currentInvoiceBase = invoiceBase;
                    } else {
                        Toast.makeText(this, "Invoice not found: " + invoiceBase, Toast.LENGTH_SHORT).show();
                    }
                });
    }


    private void deleteInvoice() {
        String invoiceBase = tvInvoice.getText().toString();
        if (invoiceBase.isEmpty()) {
            Toast.makeText(this, "No invoice to delete.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show confirmation dialog
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete Invoice")
                .setMessage("Are you sure you want to delete invoice " + invoiceBase + "?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    // User confirmed → delete
                    db.collection("Journal").whereEqualTo("invoiceNo", invoiceBase)
                            .get()
                            .addOnSuccessListener(querySnapshot -> {
                                if (querySnapshot.isEmpty()) {
                                    Toast.makeText(this, "Invoice not found.", Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                WriteBatch batch = db.batch();
                                for (DocumentSnapshot doc : querySnapshot) {
                                    String toParty = doc.getString("to");
                                    String fromParty = doc.getString("from");
                                    double amt = doc.getDouble("amount") != null ? doc.getDouble("amount") : 0.0;

                                    // revert balances
                                    if (partySnapshots.containsKey(toParty)) {
                                        batch.update(partySnapshots.get(toParty).getReference(), "currentBalance", FieldValue.increment(-amt));
                                    }
                                    if (partySnapshots.containsKey(fromParty)) {
                                        batch.update(partySnapshots.get(fromParty).getReference(), "currentBalance", FieldValue.increment(amt));
                                    }

                                    batch.delete(doc.getReference());
                                }

                                batch.commit().addOnCompleteListener(task -> {
                                    if (task.isSuccessful()) {
                                        Toast.makeText(this, "Deleted invoice " + invoiceBase, Toast.LENGTH_SHORT).show();

                                        // 🔥 RELOAD UPDATED BALANCES IMMEDIATELY
                                        loadPartyNamesAndBalances();

                                        clearFieldsForNewInvoice();
                                    } else {
                                        Toast.makeText(this, "Failed to delete invoice.", Toast.LENGTH_SHORT).show();
                                    }
                                });

                            })
                            .addOnFailureListener(e -> Toast.makeText(this, "Failed to delete invoice.", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                .show();
    }


}
