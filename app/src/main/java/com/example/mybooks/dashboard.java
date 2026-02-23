package com.example.mybooks;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

public class dashboard extends AppCompatActivity {

    LinearLayout cardParty, cardItems, cardTransactions, cardView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        cardParty = findViewById(R.id.cardParty);
        cardItems = findViewById(R.id.cardItems);
        cardTransactions = findViewById(R.id.cardTransactions);
        cardView = findViewById(R.id.cardView);

        // Party card click
        cardParty.setOnClickListener(v ->
                startActivity(new Intent(dashboard.this, Party.class))
        );

        // Item card click
        cardItems.setOnClickListener(v ->
                startActivity(new Intent(dashboard.this, Item.class))
        );

        // Transaction card click
        cardTransactions.setOnClickListener(v ->
                startActivity(new Intent(dashboard.this, Transaction.class))
        );

        // View card click
        cardView.setOnClickListener(v ->
                startActivity(new Intent(dashboard.this, view.class))
        );
    }
}
