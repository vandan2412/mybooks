package com.example.mybooks;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.DialogFragment;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Transaction extends AppCompatActivity {

    private CardView cardPurchase, cardSale, cardPayment, cardReceipt, cardSale2, cardUnitConversion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction);

        // Initialize Cards
        cardPurchase = findViewById(R.id.cardPurchase);
        cardSale = findViewById(R.id.cardSale);
        cardPayment = findViewById(R.id.cardPayment);
        cardReceipt = findViewById(R.id.cardReceipt);
        cardSale2= findViewById(R.id.cardSale2);
        cardUnitConversion = findViewById(R.id.cardUnitConversion);

        // Set Click Listeners
        cardPurchase.setOnClickListener(v -> startActivity(new Intent(Transaction.this, purchase.class)));

        cardSale.setOnClickListener(v -> startActivity(new Intent(Transaction.this, sale2.class)));

        cardSale2.setOnClickListener(v -> startActivity(new Intent(Transaction.this, sale.class)));

        cardPayment.setOnClickListener(v ->  startActivity(new Intent(Transaction.this,PAYMENT.class)));

        cardReceipt.setOnClickListener(v -> startActivity(new Intent(Transaction.this,receipt.class)));

        cardUnitConversion.setOnClickListener(v -> {
            UnitConversionDialogFragment dialog = new UnitConversionDialogFragment();
            dialog.show(getSupportFragmentManager(), "UnitConversionDialogFragment");
        });
    }

    public static class UnitConversionDialogFragment extends DialogFragment {

        private static final String TAG = "UnitConversionDialog";
        private AutoCompleteTextView actvHigherUnit, actvLowerUnit;
        private TextInputEditText etConversionRate;
        private Button btnCancel, btnSave;
        private FirebaseFirestore db;

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.dialog_unit_conversion, container, false);
        }

        @Override
        public void onStart() {
            super.onStart();
            Dialog dialog = getDialog();
            if (dialog != null) {
                Window window = dialog.getWindow();
                if (window != null) {
                    int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90);
                    window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
                }
            }
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            actvHigherUnit = view.findViewById(R.id.actvHigherUnit);
            actvLowerUnit = view.findViewById(R.id.actvLowerUnit);
            etConversionRate = view.findViewById(R.id.etConversionRate);
            btnCancel = view.findViewById(R.id.btnCancel);
            btnSave = view.findViewById(R.id.btnSave);

            db = FirebaseFirestore.getInstance();

            fetchUnitsAndPopulateSpinners();

            btnCancel.setOnClickListener(v -> dismiss());

            btnSave.setOnClickListener(v -> saveUnitConversion());
        }

        private void fetchUnitsAndPopulateSpinners() {
            List<String> units = new ArrayList<>();
            db.collection("units")
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                String unitName = document.getString("name");
                                if (unitName != null) {
                                    units.add(unitName);
                                }
                            }
                            if (getContext() != null) {
                                ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line, units);
                                actvHigherUnit.setAdapter(adapter);
                                actvLowerUnit.setAdapter(adapter);
                            }
                        } else {
                            Log.w(TAG, "Error getting documents: ", task.getException());
                            Toast.makeText(getContext(), "Failed to fetch units.", Toast.LENGTH_SHORT).show();
                        }
                    });
        }

        private void saveUnitConversion() {
            String higherUnit = actvHigherUnit.getText().toString();
            String lowerUnit = actvLowerUnit.getText().toString();
            String conversionRateStr = etConversionRate.getText().toString();

            if (higherUnit.isEmpty() || lowerUnit.isEmpty() || conversionRateStr.isEmpty()) {
                Toast.makeText(getContext(), "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            if (higherUnit.equals(lowerUnit)) {
                Toast.makeText(getContext(), "Units cannot be the same", Toast.LENGTH_SHORT).show();
                return;
            }

            db.collection("unit_conversions")
                    .whereEqualTo("higher_unit", higherUnit)
                    .whereEqualTo("lower_unit", lowerUnit)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && !task.getResult().isEmpty()) {
                            Toast.makeText(getContext(), "This conversion already exists.", Toast.LENGTH_SHORT).show();
                        } else {
                            // Check the other combination
                            db.collection("unit_conversions")
                                    .whereEqualTo("higher_unit", lowerUnit)
                                    .whereEqualTo("lower_unit", higherUnit)
                                    .get()
                                    .addOnCompleteListener(task2 -> {
                                        if (task2.isSuccessful() && !task2.getResult().isEmpty()) {
                                            Toast.makeText(getContext(), "This conversion already exists.", Toast.LENGTH_SHORT).show();
                                        } else {
                                            // If no duplicates are found, save the new conversion
                                            double conversionRate = Double.parseDouble(conversionRateStr);
                                            Map<String, Object> conversionValues = new HashMap<>();
                                            conversionValues.put("higher_unit", higherUnit);
                                            conversionValues.put("lower_unit", lowerUnit);
                                            conversionValues.put("conversion_rate", conversionRate);

                                            db.collection("unit_conversions")
                                                    .add(conversionValues)
                                                    .addOnSuccessListener(documentReference -> {
                                                        Toast.makeText(getContext(), "Unit conversion saved.", Toast.LENGTH_SHORT).show();
                                                        dismiss();
                                                    })
                                                    .addOnFailureListener(e -> {
                                                        Toast.makeText(getContext(), "Failed to save: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                                        Log.w(TAG, "Error adding document", e);
                                                    });
                                        }
                                    });
                        }
                    });
        }
    }
}
