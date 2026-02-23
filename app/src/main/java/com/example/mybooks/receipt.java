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

public class receipt extends AppCompatActivity {

    EditText tvDate, tvInvoice;
    TextInputEditText etRemarks;

    AutoCompleteTextView party1, party2, party3, party4, party5, party6;
    EditText amt1, amt2, amt3, amt4, amt5, amt6;

    List<AutoCompleteTextView> partyFields;
    List<EditText> amtList;

    MaterialButton btnPrev, btnNext, btnCancel, btnSave, btnDelete;

    FirebaseFirestore db = FirebaseFirestore.getInstance();

    Map<String, DocumentSnapshot> partySnapshots = new HashMap<>();

    private boolean isUpdatingAmounts = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receipt);

        bindViews();
        setupAutoDate();
        loadPartyNamesAndBalances();
        generateInvoiceNumber();
        setupFocusAndWatchers();
        setupButtons();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                rollbackInvoiceIfNeeded();
                finish();
            }
        });
    }

    private void bindViews() {
        tvDate = findViewById(R.id.tvDate);
        tvInvoice = findViewById(R.id.tvInvoice);
        etRemarks = findViewById(R.id.etRemarks);

        party1 = findViewById(R.id.party1); // FROM
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

    private void setupAutoDate() {
        tvDate.setText(new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date()));

        tvDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(receipt.this, (view, year, month, day) ->
                    tvDate.setText(String.format(Locale.getDefault(),
                            "%02d-%02d-%04d", day, month + 1, year)),
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });
    }

    private void rollbackInvoiceIfNeeded() {
        String inv = tvInvoice.getText().toString();
        if (!inv.startsWith("RE-")) return;

        int currentNum;
        try {
            currentNum = Integer.parseInt(inv.substring(3));
        } catch (Exception e) {
            return;
        }

        DocumentReference invRef = db.collection("invno").document("RE-");
        invRef.get().addOnSuccessListener(doc -> {
            if (doc.exists() && doc.contains("number")) {
                long saved = doc.getLong("number");
                if (saved - 1 == currentNum) {
                    invRef.update("number", saved - 1);
                }
            }
        });
    }

    private void loadPartyNamesAndBalances() {

        db.collection("Party").get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Toast.makeText(this, "Failed to load parties.", Toast.LENGTH_SHORT).show();
                return;
            }

            partySnapshots.clear();
            List<String> display = new ArrayList<>();

            for (QueryDocumentSnapshot ds : task.getResult()) {
                String name = ds.getString("partyName");
                double bal = ds.getDouble("currentBalance") != null ? ds.getDouble("currentBalance") : 0;

                partySnapshots.put(name, ds);
                display.add(name + " (Bal: " + bal + ")");
            }

            ArrayAdapter<String> adapter =
                    new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, display);

            for (AutoCompleteTextView act : partyFields) {
                act.setAdapter(adapter);

                act.setOnItemClickListener((parent, view, position, id) -> {
                    String selected = adapter.getItem(position);
                    String name = selected.substring(0, selected.indexOf(" ("));
                    act.setText(name);
                    act.setSelection(name.length());
                    focusNextAfterPartySelection(act);
                });
            }
        });
    }

    private void generateInvoiceNumber() {
        DocumentReference invRef = db.collection("invno").document("RE-");

        invRef.get().addOnSuccessListener(doc -> {
            long last = 0;
            if (doc.exists() && doc.contains("number")) last = doc.getLong("number");

            tvInvoice.setText("RE-" + last);
            long next = last + 1;

            clearFieldsForNewInvoice();

            invRef.update("number", next).addOnFailureListener(e ->
                    invRef.set(Collections.singletonMap("number", next)));
        });
    }

    private void clearFieldsForNewInvoice() {
        tvDate.setText(new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date()));
        etRemarks.setText("");

        for (AutoCompleteTextView act : partyFields) act.setText("");
        for (EditText et : amtList) et.setText("");

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
    private String formatDouble(double v) {
        return v == (long) v ? String.format(Locale.getDefault(), "%d", (long) v) : String.format(Locale.getDefault(), "%.2f", v);
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
            amtList.get(i).addTextChangedListener(new receipt.SimpleTextWatcher() {
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


    private double getDouble(EditText et) {
        try {
            String s = et.getText().toString().trim();
            return s.isEmpty() ? 0 : Double.parseDouble(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private void setupButtons() {
        btnPrev.setOnClickListener(v -> navigateInvoice(false));
        btnNext.setOnClickListener(v -> navigateInvoice(true));
        btnCancel.setOnClickListener(v -> {
            rollbackInvoiceIfNeeded();
            finish();
        });
        btnSave.setOnClickListener(v -> attemptSave());
        btnDelete.setOnClickListener(v -> deleteInvoice());
    }

    private void attemptSave() {

        double fromAmount = getDouble(amt1);
        if (fromAmount <= 0) {
            Toast.makeText(this, "Amount must be positive", Toast.LENGTH_SHORT).show();
            return;
        }

        String fromParty = party1.getText().toString().trim();
        if (fromParty.isEmpty()) {
            Toast.makeText(this, "FROM party required", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> toParties = new ArrayList<>();
        List<Double> toAmounts = new ArrayList<>();

        double totalTo = 0;

        for (int i = 1; i < partyFields.size(); i++) {
            String party = partyFields.get(i).getText().toString().trim();
            if (!party.isEmpty()) {
                double amt = getDouble(amtList.get(i));
                if (amt <= 0) {
                    Toast.makeText(this, "Amounts must be positive", Toast.LENGTH_SHORT).show();
                    return;
                }
                toParties.add(party);
                toAmounts.add(amt);
                totalTo += amt;
            }
        }

        if (toParties.isEmpty()) {
            Toast.makeText(this, "At least one TO party required", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Math.abs(totalTo - fromAmount) > 0.01) {
            Toast.makeText(this, "Amounts do not match", Toast.LENGTH_SHORT).show();
            return;
        }

        String invoice = tvInvoice.getText().toString();
        String date = tvDate.getText().toString();
        String remarks = etRemarks.getText().toString();

        WriteBatch batch = db.batch();

        // ❗ REVERT OLD VALUES IF EDITING
        db.collection("Journal").whereEqualTo("invoiceNo", invoice)
                .get()
                .addOnSuccessListener(snap -> {

                    for (DocumentSnapshot d : snap) {
                        String oldFrom = d.getString("from");
                        String oldTo = d.getString("to");
                        double amt = d.getDouble("amount");

                        // revert — Receipt reverse logic
                        batch.update(partySnapshots.get(oldFrom).getReference(),
                                "currentBalance", FieldValue.increment(+amt));

                        batch.update(partySnapshots.get(oldTo).getReference(),
                                "currentBalance", FieldValue.increment(-amt));

                        batch.delete(d.getReference());
                    }

                    // SAVE NEW ENTRIES — Receipt Logic
                    for (int i = 0; i < toParties.size(); i++) {
                        String to = toParties.get(i);
                        double amt = toAmounts.get(i);

                        String docName = invoice + "(" + (i + 1) + ")";
                        DocumentReference ref = db.collection("Journal").document(docName);

                        Map<String, Object> data = new HashMap<>();
                        data.put("amount", amt);
                        data.put("date", date);
                        data.put("from", fromParty);
                        data.put("to", to);
                        data.put("invoiceNo", invoice);
                        data.put("remarks", remarks);
                        data.put("type", "receipt");

                        batch.set(ref, data);

                        // UPDATE BALANCES (receipt logic)
                        batch.update(partySnapshots.get(fromParty).getReference(),
                                "currentBalance", FieldValue.increment(-amt));

                        batch.update(partySnapshots.get(to).getReference(),
                                "currentBalance", FieldValue.increment(+amt));
                    }

                    batch.commit().addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {

                            loadPartyNamesAndBalances();  // 🔥 Refresh balances immediately

                            Toast.makeText(this, "Receipt saved", Toast.LENGTH_SHORT).show();
                            btnDelete.setVisibility(MaterialButton.VISIBLE);
                            generateInvoiceNumber();

                        } else {
                            Toast.makeText(this, "Failed to save", Toast.LENGTH_SHORT).show();
                        }
                    });
                });
    }

    private void navigateInvoice(boolean isNext) {
        String currentRaw = (tvInvoice.getText() != null) ? tvInvoice.getText().toString().trim() : "";
        if (!currentRaw.startsWith("RE-")) {
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
                        if (!inv.startsWith("RE-")) continue;

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
                            String newInvoiceNo = "RE-" + newInvNumber;
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
                            String newInvoiceNo = "RE-" + newInvNumber;
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
                    String newInvoiceNo = "RE-" + newInvNumber;
                    tvInvoice.setText(newInvoiceNo);
                    loadInvoice(newInvoiceNo);

                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load invoices: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }
    private void loadLatestInvoiceFromInvno() {
        DocumentReference invRef = db.collection("invno").document("RE-");

        invRef.get().addOnSuccessListener(doc -> {
            if (doc.exists() && doc.contains("number")) {

                long savedNum = doc.getLong("number"); // example: 29 → last invoice will be RE-28
                String latestInvoice = "RE-" + (savedNum - 1);

                tvInvoice.setText(latestInvoice);
                loadInvoice(latestInvoice);
                clearFieldsForNewInvoice();
            }
        });
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

    private void loadInvoice(String invoice) {

        db.collection("Journal")
                .whereEqualTo("invoiceNo", invoice)
                .get()
                .addOnSuccessListener(snap -> {

                    if (snap.isEmpty()) {
                        Toast.makeText(this, "Not found", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    clearFieldsForNewInvoice();

                    String from = null;
                    String date = null;
                    String remarks = null;
                    double totalFromAmt = 0;

                    List<String> toParties = new ArrayList<>();
                    List<Double> toAmounts = new ArrayList<>();

                    for (DocumentSnapshot d : snap) {
                        if (from == null) from = d.getString("from");
                        if (date == null) date = d.getString("date");
                        if (remarks == null) remarks = d.getString("remarks");

                        String to = d.getString("to");
                        double amt = d.getDouble("amount");

                        toParties.add(to);
                        toAmounts.add(amt);

                        totalFromAmt += amt;
                    }

                    tvInvoice.setText(invoice);
                    tvDate.setText(date);
                    etRemarks.setText(remarks);

                    party1.setText(from);
                    amt1.setText(String.valueOf(totalFromAmt));

                    for (int i = 0; i < toParties.size(); i++) {
                        partyFields.get(i + 1).setText(toParties.get(i));
                        amtList.get(i + 1).setText(String.valueOf(toAmounts.get(i)));
                    }

                    btnDelete.setVisibility(MaterialButton.VISIBLE);

                });
    }

    private void deleteInvoice() {

        String invoice = tvInvoice.getText().toString();
        if (invoice.isEmpty()) return;

        db.collection("Journal").whereEqualTo("invoiceNo", invoice)
                .get()
                .addOnSuccessListener(snap -> {

                    if (snap.isEmpty()) {
                        Toast.makeText(this, "Not found", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    WriteBatch batch = db.batch();

                    for (DocumentSnapshot d : snap) {

                        String from = d.getString("from");
                        String to = d.getString("to");
                        double amt = d.getDouble("amount");

                        // revert receipt logic
                        batch.update(partySnapshots.get(from).getReference(),
                                "currentBalance", FieldValue.increment(+amt));

                        batch.update(partySnapshots.get(to).getReference(),
                                "currentBalance", FieldValue.increment(-amt));

                        batch.delete(d.getReference());
                    }

                    batch.commit().addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {

                            loadPartyNamesAndBalances(); // 🔥 Refresh balances instantly

                            Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                            clearFieldsForNewInvoice();
                        }
                    });
                });
    }
    private abstract class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }

}
