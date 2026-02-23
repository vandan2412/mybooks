// BillData.java
package com.example.mybooks;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Map;

public class BillData implements Serializable {
    public String invoiceNo;
    public String date;
    public String party;
    public ArrayList<Map<String, Object>> items;
    public double discount;
    public double tax;
    public double grandTotal;
    public String remarks;
    public double total;
}
