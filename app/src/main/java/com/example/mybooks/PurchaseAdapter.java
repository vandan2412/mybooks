package com.example.mybooks;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
public class PurchaseAdapter extends BaseAdapter {

    Context context;
    ArrayList<Object> dataList;   // contains Map OR String
    LayoutInflater inflater;

    private static final int TYPE_ROW = 0;
    private static final int TYPE_DAY_TOTAL = 1;

    public PurchaseAdapter(Context ctx, ArrayList<Object> list) {
        this.context = ctx;
        this.dataList = list;
        this.inflater = LayoutInflater.from(ctx);
    }

    @Override
    public int getCount() {
        return dataList.size();
    }

    @Override
    public Object getItem(int position) {
        return dataList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        Object obj = dataList.get(position);
        if (obj instanceof String) return TYPE_DAY_TOTAL;
        return TYPE_ROW;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        int type = getItemViewType(position);

        if (type == TYPE_DAY_TOTAL) {
            return getDayTotalView(position, convertView, parent);
        } else {
            return getNormalRowView(position, convertView, parent);
        }
    }

    private View getNormalRowView(int position, View convertView, ViewGroup parent) {

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.row_purchase, parent, false);
        }

        Map<String, Object> map = (Map<String, Object>) dataList.get(position);

        TextView tvSno = convertView.findViewById(R.id.tvSno);
        TextView tvDate = convertView.findViewById(R.id.tvDate);
        TextView tvInv = convertView.findViewById(R.id.tvInvoice);
        TextView tvParty = convertView.findViewById(R.id.tvParty);
        TextView tvAmt = convertView.findViewById(R.id.tvAmount);

        tvSno.setText(String.valueOf(position + 1));
        tvDate.setText(map.get("date").toString());
        tvInv.setText(map.get("invoiceNo").toString());
        tvParty.setText(map.get("party").toString());
        tvAmt.setText(map.get("grandTotal").toString());

        return convertView;
    }

    private View getDayTotalView(int position, View convertView, ViewGroup parent) {

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.row_day_total, parent, false);
        }

        String totalText = (String) dataList.get(position);

        TextView tvTotal = convertView.findViewById(R.id.tvDayTotal);
        tvTotal.setText(totalText);

        return convertView;
    }
}
