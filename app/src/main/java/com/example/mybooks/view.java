package com.example.mybooks;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;

public class view extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view);

        MaterialCardView saleReportCard = findViewById(R.id.sale_report_card);
        MaterialCardView purchaseReportCard = findViewById(R.id.purchase_report_card);
        MaterialCardView partyLedgerCard = findViewById(R.id.party_ledger_card);
        MaterialCardView trialBalanceCard = findViewById(R.id.trial_balance_card);
        MaterialCardView balanceSheetCard = findViewById(R.id.balance_sheet_card);



        purchaseReportCard.setOnClickListener(v -> startActivity(new Intent(view.this, purchaseReport .class)));
        saleReportCard.setOnClickListener(v -> startActivity(new Intent(view.this, saleReport .class)));

        partyLedgerCard.setOnClickListener(v -> startActivity(new Intent(view.this, PartyList.class)));


        trialBalanceCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(view.this, "Trial Balance Clicked", Toast.LENGTH_SHORT).show();
                // Intent intent = new Intent(view.this, TrialBalanceActivity.class);
                // startActivity(intent);
            }
        });

        balanceSheetCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(view.this, "Balance Sheet Clicked", Toast.LENGTH_SHORT).show();
                // Intent intent = new Intent(view.this, BalanceSheetActivity.class);
                // startActivity(intent);
            }
        });
    }
}
