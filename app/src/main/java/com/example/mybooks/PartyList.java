package com.example.mybooks;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
public class PartyList extends AppCompatActivity {

    EditText searchBar;
    ListView listView;
    Button btnBack;

    ArrayList<PartyModel> partyList = new ArrayList<>();
    ArrayList<PartyModel> filteredList = new ArrayList<>();
    PartyAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_party_list);

        searchBar = findViewById(R.id.searchBar);
        btnBack   = findViewById(R.id.btnBack);
        listView  = findViewById(R.id.listViewParties);

        btnBack.setOnClickListener(v -> finish());

        adapter = new PartyAdapter(this, filteredList);
        listView.setAdapter(adapter);

        loadParties();
        setupSearch();

        listView.setOnItemClickListener((adapterView, view, i, l) -> {
            PartyModel p = filteredList.get(i);

          /*  Intent intent = new Intent(PartyList.this, LedgerPage.class);
            intent.putExtra("partyName", p.name);
            startActivity(intent);*/
        });
    }

    private void loadParties() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("Party").get().addOnSuccessListener(query -> {
            partyList.clear();

            for (DocumentSnapshot doc : query) {
                partyList.add(new PartyModel(
                        doc.getString("partyName"),
                        doc.getString("group"),
                        doc.getLong("currentBalance").intValue()
                ));
            }

            filteredList.clear();
            filteredList.addAll(partyList);

            adapter.notifyDataSetChanged();
        });
    }

    private void setupSearch() {
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {

                String search = s.toString().toLowerCase();
                filteredList.clear();

                for (PartyModel p : partyList) {
                    if (p.name.toLowerCase().contains(search) ||
                            p.group.toLowerCase().contains(search) ||
                            String.valueOf(p.balance).contains(search)) {

                        filteredList.add(p);
                    }
                }

                adapter.notifyDataSetChanged();
            }

            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
        });
    }


    public static class PartyModel {
        String name, group;
        int balance;

        public PartyModel(String name, String group, int balance) {
            this.name = name;
            this.group = group;
            this.balance = balance;
        }
    }
    public class PartyAdapter extends BaseAdapter {
        Context c;
        ArrayList<PartyModel> list;

        public PartyAdapter(Context c, ArrayList<PartyModel> list) {
            this.c = c;
            this.list = list;
        }

        @Override public int getCount() { return list.size(); }
        @Override public Object getItem(int i) { return list.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View v, ViewGroup parent) {
            if (v == null)
                v = LayoutInflater.from(c).inflate(R.layout.party_row, parent, false);

            TextView name = v.findViewById(R.id.txtName);
            TextView group = v.findViewById(R.id.txtGroup);
            TextView balance = v.findViewById(R.id.txtBalance);

            PartyModel m = list.get(i);

            name.setText(m.name);
            group.setText(m.group);
            balance.setText(String.valueOf(m.balance));

            return v;
        }
    }
}