package com.example.mybooks;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.firestore.*;
import java.util.*;

public class Item extends AppCompatActivity {

    EditText etSearch;
    Button btnAddItem;
    ListView listViewItems;
    BaseAdapter listAdapter;
    List<Map<String, Object>> itemList = new ArrayList<>();
    Map<String, String> docIds = new HashMap<>();
    FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item);

        db = FirebaseFirestore.getInstance();
        etSearch = findViewById(R.id.etSearch);
        btnAddItem = findViewById(R.id.btnAddItemMain);
        listViewItems = findViewById(R.id.listViewItemsMain);

        View headerView = getLayoutInflater().inflate(R.layout.item_list_header, null);
        listViewItems.addHeaderView(headerView, null, false);

        listAdapter = new BaseAdapter() {
            @Override public int getCount() { return itemList.size(); }
            @Override public Object getItem(int position) { return itemList.get(position); }
            @Override public long getItemId(int position) { return position; }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null)
                    convertView = getLayoutInflater().inflate(R.layout.item_list_row, parent, false);

                TextView tvSNo = convertView.findViewById(R.id.tvSNo);
                TextView tvItemName = convertView.findViewById(R.id.tvItemName);
                TextView tvQuantity = convertView.findViewById(R.id.tvQuantity);
                TextView tvUnit = convertView.findViewById(R.id.tvUnit);

                Map<String, Object> item = itemList.get(position);
                tvSNo.setText(String.valueOf(position + 1));
                tvItemName.setText(String.valueOf(item.get("itemName")));
                tvQuantity.setText(String.valueOf(item.get("quantity")));
                tvUnit.setText(String.valueOf(item.get("unit")));

                return convertView;
            }
        };
        listViewItems.setAdapter(listAdapter);

        loadItems();

        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterList(s.toString()); }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        btnAddItem.setOnClickListener(v -> showAddOrModifyDialog(null, null));

        listViewItems.setOnItemClickListener((parent, view, position, id) -> {
            if (position == 0) return;
            Map<String, Object> item = itemList.get(position - 1);
            String itemName = (String) item.get("itemName");
            String docId = docIds.get(itemName);
            showModifyDeleteDialog(itemName, docId);
        });
    }

    private void filterList(String query) {
        db.collection("items").orderBy("itemName", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(qs -> {
                    itemList.clear();
                    for (var doc : qs.getDocuments()) {
                        Map<String, Object> itemData = doc.getData();
                        if (itemData != null && ((String)itemData.get("itemName")).toLowerCase().contains(query.toLowerCase())) {
                            itemList.add(itemData);
                        }
                    }
                    listAdapter.notifyDataSetChanged();
                });
    }

    private void loadItems() {
        db.collection("items").orderBy("itemName", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(qs -> {
                    itemList.clear();
                    docIds.clear();
                    for (var doc : qs.getDocuments()) {
                        Map<String, Object> itemData = doc.getData();
                        if (itemData != null) {
                            itemList.add(itemData);
                            docIds.put((String) itemData.get("itemName"), doc.getId());
                        }
                    }
                    listAdapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load items", Toast.LENGTH_SHORT).show());
    }

    private void showModifyDeleteDialog(String itemName, String docId) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.custom_modify_delete_dialog);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        TextView title = dialog.findViewById(R.id.dialogTitle);
        Button btnModify = dialog.findViewById(R.id.btnModify);
        Button btnDelete = dialog.findViewById(R.id.btnDelete);

        title.setText(itemName);

        btnModify.setOnClickListener(v -> {
            dialog.dismiss();
            fetchItemForModify(docId);
        });

        btnDelete.setOnClickListener(v -> {
            dialog.dismiss();
            deleteItem(docId, itemName);
        });

        dialog.show();
    }

    private void showAddOrModifyDialog(@Nullable String docId, @Nullable Map<String,Object> existingData) {
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_add_item);
        dialog.setCancelable(true);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        EditText etItemName = dialog.findViewById(R.id.etItemName);
        EditText etQuantity = dialog.findViewById(R.id.etQuantity);
        EditText etPurchasePrice = dialog.findViewById(R.id.etPurchasePrice);
        EditText etWholesale = dialog.findViewById(R.id.etWholesaleRate);
        EditText etRetail = dialog.findViewById(R.id.etRetailRate);
        Spinner spinnerUnit = dialog.findViewById(R.id.spinnerUnit);
        Button btnAdd = dialog.findViewById(R.id.btnAddItem);
        Button btnCancel = dialog.findViewById(R.id.btnCancel);

        etItemName.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        etQuantity.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        etPurchasePrice.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        etWholesale.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        etRetail.setImeOptions(EditorInfo.IME_ACTION_DONE);

        List<String> units = new ArrayList<>();
        units.add("Select Unit");
        units.add("Add Unit");
        ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, units);
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerUnit.setAdapter(unitAdapter);

        db.collection("units").orderBy("name", Query.Direction.ASCENDING)
                .get().addOnSuccessListener(qs -> {
                    for (var doc : qs.getDocuments()) {
                        String name = doc.getString("name");
                        if(name != null && !units.contains(name)) units.add(name);
                    }
                    unitAdapter.notifyDataSetChanged();
                    if(existingData != null){
                        String unitName = (String) existingData.get("unit");
                        if(unitName != null && units.contains(unitName)) spinnerUnit.setSelection(units.indexOf(unitName));
                    }
                });

        spinnerUnit.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if(position == 1){
                    spinnerUnit.setSelection(0);
                    showAddUnitDialog(units, unitAdapter, spinnerUnit);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        if(existingData != null){
            etItemName.setText((String)existingData.get("itemName"));
            // When modifying, show the OPENING quantity in the dialog.
            if (existingData.containsKey("openingQuantity")) {
                etQuantity.setText(String.valueOf(existingData.get("openingQuantity")));
            } else {
                etQuantity.setText(String.valueOf(existingData.get("quantity")));
            }
            etPurchasePrice.setText(String.valueOf(existingData.get("purchasePrice")));
            etWholesale.setText(String.valueOf(existingData.get("wholesaleRate")));
            etRetail.setText(String.valueOf(existingData.get("retailRate")));
            btnAdd.setText("Modify");
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        View.OnClickListener addLogic = v -> addItemAndReset(etItemName, etQuantity, etPurchasePrice,
                etWholesale, etRetail, spinnerUnit, units, docId, existingData, dialog, false);

        btnAdd.setOnClickListener(addLogic);

        etRetail.setOnEditorActionListener((v, actionId, event) -> {
            if(actionId == EditorInfo.IME_ACTION_DONE){
                addItemAndReset(etItemName, etQuantity, etPurchasePrice,
                        etWholesale, etRetail, spinnerUnit, units, docId, existingData, dialog, false);
                return true;
            }
            return false;
        });

        dialog.show();
    }

    private void addItemAndReset(EditText etItemName, EditText etQuantity, EditText etPurchasePrice,
                                 EditText etWholesale, EditText etRetail, Spinner spinnerUnit,
                                 List<String> units, @Nullable String docId, @Nullable Map<String, Object> existingData, Dialog dialog, boolean dismissAfter) {

        String name = etItemName.getText().toString().trim();
        String qtyStringFromDialog = etQuantity.getText().toString().trim();
        String pur = etPurchasePrice.getText().toString().trim();
        String whole = etWholesale.getText().toString().trim();
        String retail = etRetail.getText().toString().trim();
        String unit = spinnerUnit.getSelectedItem() != null ? spinnerUnit.getSelectedItem().toString() : "";

        if(name.isEmpty()||qtyStringFromDialog.isEmpty()||pur.isEmpty()||whole.isEmpty()||retail.isEmpty()||unit.equals("Select Unit")||unit.equals("Add Unit")){
            Toast.makeText(this,"⚠ Fill all required fields",Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String,Object> data = new HashMap<>();
        data.put("itemName", name);
        data.put("unit", unit);
        data.put("purchasePrice", pur);
        data.put("wholesaleRate", whole);
        data.put("retailRate", retail);

        if (docId == null) { // New item is being added
            data.put("quantity", qtyStringFromDialog);
            data.put("openingQuantity", qtyStringFromDialog);
        } else if (existingData != null) { // Existing item is being modified
            try {
                double dialogValue = Double.parseDouble(qtyStringFromDialog);

                Object oqObject = existingData.getOrDefault("openingQuantity", existingData.get("quantity"));
                double originalOpeningQuantity = Double.parseDouble(String.valueOf(oqObject));

                double originalQuantity = Double.parseDouble(String.valueOf(existingData.get("quantity")));

                double difference = dialogValue - originalOpeningQuantity;

                double newQuantity = originalQuantity + difference;

                data.put("quantity", String.valueOf(newQuantity));
                data.put("openingQuantity", String.valueOf(dialogValue));

            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number format for quantity", Toast.LENGTH_SHORT).show();
                return;
            }
        }


        db.collection("items").get().addOnSuccessListener(q -> {
            boolean exists = false;
            String originalName = (existingData != null) ? (String) existingData.get("itemName") : null;
            for(var doc : q.getDocuments()){
                String dbName = doc.getString("itemName");
                if(dbName != null && dbName.equalsIgnoreCase(name) && !dbName.equalsIgnoreCase(originalName)){
                    exists = true;
                    break;
                }
            }

            if(exists){
                Toast.makeText(this,"⚠ Item with this name already exists",Toast.LENGTH_SHORT).show();
            } else {
                if (originalName != null && !name.equalsIgnoreCase(originalName)) {
                    db.collection("items").document(originalName).delete();
                }
                db.collection("items").document(name).set(data)
                        .addOnSuccessListener(r -> {
                            Toast.makeText(this, (docId == null ? "✅ Item Added" : "✅ Item Modified"), Toast.LENGTH_SHORT).show();
                            loadItems();
                            if (dialog.isShowing()) {
                                clearFields(etItemName, etQuantity, etPurchasePrice, etWholesale, etRetail, spinnerUnit);
                                if(dismissAfter) dialog.dismiss();
                            }
                        })
                        .addOnFailureListener(e -> Toast.makeText(this,"❌ Error saving item",Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void clearFields(EditText name, EditText qty, EditText pp, EditText wp, EditText rp, Spinner unit){
        name.setText("");
        qty.setText("");
        pp.setText("");
        wp.setText("");
        rp.setText("");
        unit.setSelection(0);
        name.requestFocus();
    }

    private void showAddUnitDialog(List<String> units, ArrayAdapter<String> adapter, Spinner spinnerUnit){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Unit");
        final EditText input = new EditText(this);
        input.setHint("Enter Unit Name");
        builder.setView(input);

        builder.setPositiveButton("Add", (dialog, which) -> {
            String unitName = input.getText().toString().trim();
            if(!unitName.isEmpty() && !units.contains(unitName)){
                Map<String,Object> data = new HashMap<>();
                data.put("name", unitName);
                db.collection("units").document(unitName).set(data)
                        .addOnSuccessListener(r -> {
                            Toast.makeText(this,"✅ Unit added",Toast.LENGTH_SHORT).show();
                            units.add(unitName);
                            adapter.notifyDataSetChanged();
                            spinnerUnit.setSelection(units.indexOf(unitName));
                        }).addOnFailureListener(e -> Toast.makeText(this,"❌ Failed to add unit",Toast.LENGTH_SHORT).show());
            } else Toast.makeText(this,"⚠ Unit already exists or invalid",Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Cancel",(dialog, which)->dialog.dismiss());
        builder.show();
    }

    private void fetchItemForModify(String docId){
        db.collection("items").document(docId).get()
                .addOnSuccessListener(doc -> { if(doc.exists()) showAddOrModifyDialog(docId, doc.getData()); });
    }

    private void deleteItem(String docId, String itemName){
        new AlertDialog.Builder(this)
                .setTitle("Delete "+itemName+"?")
                .setMessage("This item will be removed permanently.")
                .setPositiveButton("🗑 Delete",(d,w)->db.collection("items").document(docId).delete()
                        .addOnSuccessListener(v->{ Toast.makeText(this,"❌ Item Deleted",Toast.LENGTH_SHORT).show(); loadItems(); }))
                .setNegativeButton("Cancel",null)
                .show();
    }
}
