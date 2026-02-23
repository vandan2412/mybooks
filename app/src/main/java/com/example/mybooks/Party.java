package com.example.mybooks;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.firestore.*;
import java.util.*;

public class Party extends AppCompatActivity {

    EditText etSearchParty;
    Button btnAddParty;
    ListView listViewParties;
    FirebaseFirestore db;

    ArrayList<Map<String, Object>> partyList = new ArrayList<>();
    ArrayList<Map<String, Object>> filteredList = new ArrayList<>();
    PartyAdapter adapter;

    ArrayList<String> groupList = new ArrayList<>();
    ArrayAdapter<String> groupAdapter;

    boolean spinnerFirstCall = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_party);

        db = FirebaseFirestore.getInstance();

        etSearchParty = findViewById(R.id.etSearchParty);
        btnAddParty = findViewById(R.id.btnAddParty);
        listViewParties = findViewById(R.id.listViewParties);

        adapter = new PartyAdapter();
        listViewParties.setAdapter(adapter);

        loadParties();

        btnAddParty.setOnClickListener(v -> showAddOrModifyDialog(null, null));

        etSearchParty.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { filterParties(s.toString()); }
            public void afterTextChanged(android.text.Editable s) {}
        });

        listViewParties.setOnItemClickListener((parent, view, position, id) -> {
            Map<String, Object> selected = filteredList.get(position);
            String docId = (String) selected.get("docId");

            new AlertDialog.Builder(this)
                    .setTitle("Select Action")
                    .setMessage("Do you want to update or delete this party?")
                    .setPositiveButton("Update", (d, w) -> showAddOrModifyDialog(docId, selected))
                    .setNegativeButton("Delete", (d, w) -> deleteParty(docId))
                    .setNeutralButton("Cancel", null)
                    .show();
        });
    }

    // 🔹 Load all parties from Firestore
    private void loadParties() {
        db.collection("Party").get().addOnSuccessListener(query -> {
            partyList.clear();
            for (DocumentSnapshot doc : query) {
                Map<String, Object> data = doc.getData();
                if (data == null) continue;
                data.put("docId", doc.getId());
                partyList.add(data);
            }
            filterParties("");
        }).addOnFailureListener(e -> Toast.makeText(this, "Failed to load parties", Toast.LENGTH_SHORT).show());
    }

    private void filterParties(String search) {
        filteredList.clear();
        for (Map<String, Object> p : partyList) {
            String name = p.get("partyName").toString().toLowerCase();
            String city = p.get("city").toString().toLowerCase();
            String group = p.get("group").toString().toLowerCase();
            if (name.contains(search.toLowerCase()) || city.contains(search.toLowerCase()) || group.contains(search.toLowerCase())) {
                filteredList.add(p);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void deleteParty(String docId) {
        db.collection("Party").document(docId).delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Party deleted", Toast.LENGTH_SHORT).show();
                    loadParties();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error deleting party", Toast.LENGTH_SHORT).show());
    }

    private void loadGroups(Spinner spinnerGroup, @Nullable String selectedGroup) {
        groupList.clear();
        groupList.add("Select Group");
        groupList.add("+ Add Group");

        db.collection("Groups").get().addOnSuccessListener(query -> {
            for (DocumentSnapshot doc : query) {
                Map<String, Object> g = doc.getData();
                if (g != null && g.containsKey("subGroupName")) {
                    groupList.add(g.get("subGroupName").toString());
                }
            }
            groupAdapter.notifyDataSetChanged();

            if (selectedGroup != null) {
                int pos = groupAdapter.getPosition(selectedGroup);
                if (pos >= 0) spinnerGroup.setSelection(pos);
            }
        });
    }

    private void showAddOrModifyDialog(String docId, Map<String, Object> existingData) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_add_party);

        EditText etPartyName = dialog.findViewById(R.id.etPartyName);
        EditText etCity = dialog.findViewById(R.id.etCity);
        Spinner spinnerGroup = dialog.findViewById(R.id.spinnerGroup);
        EditText etOpening = dialog.findViewById(R.id.etOpening);
        EditText etPhone = dialog.findViewById(R.id.etPhone);
        EditText etAltPhone = dialog.findViewById(R.id.etAltPhone);
        Button btnAdd = dialog.findViewById(R.id.btnAdd);
        Button btnCancel = dialog.findViewById(R.id.btnCancel);

        spinnerFirstCall = true;

        groupAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, groupList);
        groupAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGroup.setAdapter(groupAdapter);

        String existingGroup = existingData != null ? (String) existingData.get("group") : null;
        loadGroups(spinnerGroup, existingGroup);

        spinnerGroup.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                if (spinnerFirstCall) { spinnerFirstCall = false; return; }
                String selected = groupList.get(position);
                if ("+ Add Group".equals(selected)) {
                    showAddGroupDialog(spinnerGroup);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnAdd.setText(docId == null ? "Add" : "Update");

        if (existingData != null) {
            etPartyName.setText((String) existingData.get("partyName"));
            etCity.setText((String) existingData.get("city"));
            etOpening.setText(String.valueOf(existingData.get("openingBalance")));
            etPhone.setText((String) existingData.get("phone"));
            etAltPhone.setText((String) existingData.get("altPhone"));
        }

        // ✅ Handle keyboard focus flow
        etPartyName.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        etCity.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        etOpening.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        etPhone.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        etAltPhone.setImeOptions(EditorInfo.IME_ACTION_DONE);

        etPartyName.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                etCity.requestFocus();
                return true;
            }
            return false;
        });

        etCity.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                etOpening.requestFocus();
                return true;
            }
            return false;
        });

        etOpening.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                etPhone.requestFocus();
                return true;
            }
            return false;
        });

        etPhone.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                etAltPhone.requestFocus();
                return true;
            }
            return false;
        });

        // ✅ Trigger Add on Done key from last field
        etAltPhone.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                btnAdd.performClick();
                return true;
            }
            return false;
        });

        btnAdd.setOnClickListener(v -> {
            String name = etPartyName.getText().toString().trim();
            String city = etCity.getText().toString().trim();
            String group = spinnerGroup.getSelectedItem().toString();

            if (name.isEmpty() || city.isEmpty() || group.equals("Select Group")) {
                Toast.makeText(this, "Please enter valid details and select group", Toast.LENGTH_SHORT).show();
                return;
            }

            String phone = etPhone.getText().toString().trim();
            String altPhone = etAltPhone.getText().toString().trim();

            if (!phone.isEmpty() && phone.length() != 10) {
                Toast.makeText(this, "Enter valid 10-digit phone", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!altPhone.isEmpty() && altPhone.length() != 10) {
                Toast.makeText(this, "Enter valid 10-digit alt phone", Toast.LENGTH_SHORT).show();
                return;
            }

            double opening;
            try {
                // ✅ Allow negative balance input
                opening = Double.parseDouble(etOpening.getText().toString().trim());
            } catch (Exception e) {
                opening = 0;
            }

            double finalOpening = opening;
            db.collection("Party").get().addOnSuccessListener(query -> {
                boolean exists = false;
                for (DocumentSnapshot doc : query) {
                    String existingName = doc.getString("partyName");
                    if (existingName != null && existingName.equalsIgnoreCase(name)) {
                        if (docId == null || !doc.getId().equals(docId)) {
                            exists = true;
                            break;
                        }
                    }
                }

                if (exists) {
                    Toast.makeText(this, "Party name already exists!", Toast.LENGTH_SHORT).show();
                    return;
                }

                Map<String, Object> data = new HashMap<>();
                data.put("partyName", name);
                data.put("city", city);
                data.put("group", group);
                data.put("openingBalance", finalOpening);
                data.put("phone", phone);
                data.put("altPhone", altPhone);
                data.put("currentBalance", finalOpening);

                String docName = name.trim(); // Use party name as document ID
                if (docId == null) {
                    // 🔹 New Party
                    db.collection("Party").document(docName).set(data)
                            .addOnSuccessListener(d -> {
                                Toast.makeText(this, "Party added", Toast.LENGTH_SHORT).show();
                                loadParties();

                                etPartyName.setText("");
                                etCity.setText("");
                                etOpening.setText("");
                                etPhone.setText("");
                                etAltPhone.setText("");
                                etPartyName.requestFocus();

                                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                                imm.hideSoftInputFromWindow(etAltPhone.getWindowToken(), 0);
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Failed to save party", Toast.LENGTH_SHORT).show());
                } else {
                    // 🔹 Existing Party → adjust current balance if opening changed
                    db.collection("Party").document(docId).get().addOnSuccessListener(oldDoc -> {
                        double oldOpening = 0, oldCurrent = 0;
                        if (oldDoc.exists()) {
                            oldOpening = oldDoc.contains("openingBalance") ? ((Number) oldDoc.get("openingBalance")).doubleValue() : 0;
                            oldCurrent = oldDoc.contains("currentBalance") ? ((Number) oldDoc.get("currentBalance")).doubleValue() : 0;
                        }

                        double diff = finalOpening - oldOpening;
                        double newCurrent = oldCurrent + diff; // Adjust current balance

                        data.put("currentBalance", newCurrent);

                        db.collection("Party").document(docId).set(data)
                                .addOnSuccessListener(unused -> {
                                    Toast.makeText(this, "Party updated successfully", Toast.LENGTH_SHORT).show();
                                    loadParties();
                                    dialog.dismiss();
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, "Failed to update party", Toast.LENGTH_SHORT).show());
                    });
                }



            }).addOnFailureListener(e ->
                    Toast.makeText(this, "Error checking existing parties", Toast.LENGTH_SHORT).show()
            );
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
    }

    private void showAddGroupDialog(Spinner spinnerGroup) {
        Dialog gDialog = new Dialog(this);
        gDialog.setContentView(R.layout.dialog_add_group);

        EditText etSubGroupName = gDialog.findViewById(R.id.etSubGroupName);
        Spinner spinnerMainGroup = gDialog.findViewById(R.id.spinnerMainGroup);
        Button btnSave = gDialog.findViewById(R.id.btnSave);
        Button btnCancel = gDialog.findViewById(R.id.btnCancel);

        ArrayList<String> mainGroups = new ArrayList<>(Arrays.asList("Bank", "Debtors", "Creditors", "Income", "Expense", "Asset", "Liabilities"));
        ArrayAdapter<String> mainAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, mainGroups);
        mainAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMainGroup.setAdapter(mainAdapter);

        btnSave.setOnClickListener(v -> {
            String subGroup = etSubGroupName.getText().toString().trim();
            String mainGroup = spinnerMainGroup.getSelectedItem().toString();

            if (subGroup.isEmpty()) {
                Toast.makeText(this, "Enter subgroup name", Toast.LENGTH_SHORT).show();
                return;
            }

            Map<String, Object> data = new HashMap<>();
            data.put("subGroupName", subGroup);
            data.put("mainGroup", mainGroup);

            db.collection("Groups").document(subGroup).set(data).addOnSuccessListener(aVoid -> {
                Toast.makeText(this, "Group added", Toast.LENGTH_SHORT).show();
                gDialog.dismiss();
                loadGroups(spinnerGroup, subGroup);
            });
        });

        btnCancel.setOnClickListener(v -> gDialog.dismiss());
        gDialog.show();
        gDialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
    }

    class PartyAdapter extends BaseAdapter {
        @Override public int getCount() { return filteredList.size(); }
        @Override public Object getItem(int position) { return filteredList.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, android.view.ViewGroup parent) {
            if (convertView == null) convertView = getLayoutInflater().inflate(R.layout.party_list_item, parent, false);
            TextView tvSNo = convertView.findViewById(R.id.tvSNo);
            TextView tvName = convertView.findViewById(R.id.tvName);
            TextView tvCity = convertView.findViewById(R.id.tvCity);
            TextView tvGroup = convertView.findViewById(R.id.tvGroup);

            Map<String, Object> p = filteredList.get(position);
            tvSNo.setText(String.valueOf(position + 1));
            tvName.setText((String) p.get("partyName"));
            tvCity.setText((String) p.get("city"));
            tvGroup.setText((String) p.get("group"));
            return convertView;
        }
    }
}
