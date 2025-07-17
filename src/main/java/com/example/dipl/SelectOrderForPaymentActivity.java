package com.example.dipl;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import android.app.Activity;

public class SelectOrderForPaymentActivity extends AppCompatActivity {

    private static final String ORDERS_FOR_PAYMENT_API_URL ="http://10.0.2.2:5000/api/orders/ready_for_payment";

    private ListView listViewOrders;
    private TextView textViewStatus;

    private ArrayAdapter<OrderForReview> ordersAdapter;
    private List<OrderForReview> orderList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_order_for_payment);

        listViewOrders = findViewById(R.id.listViewOrdersForPayment);
        textViewStatus = findViewById(R.id.textViewOrdersForPaymentStatus);
        orderList = new ArrayList<>();

        ordersAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, orderList);
        listViewOrders.setAdapter(ordersAdapter);

        listViewOrders.setOnItemClickListener((parent, view, position, id) -> {
            OrderForReview selectedOrder = orderList.get(position);
            Intent intent = new Intent(SelectOrderForPaymentActivity.this, PaymentActivity.class);
            intent.putExtra("ORDER_ID", selectedOrder.getId());
            startActivity(intent);
        });
        fetchOrdersForPayment();
    }

    private void fetchOrdersForPayment() {
        textViewStatus.setText("Загрузка заказов к оплате...");
        textViewStatus.setVisibility(View.VISIBLE);
        new FetchOrdersForPaymentTask().execute(ORDERS_FOR_PAYMENT_API_URL);
    }

    private class FetchOrdersForPaymentTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            String urlString = params[0]; HttpURLConnection urlConnection = null; BufferedReader reader = null; String responseJsonString = null;
            try {
                URL url = new URL(urlString); urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET"); urlConnection.setRequestProperty("Accept", "application/json");
                urlConnection.setConnectTimeout(15000); urlConnection.setReadTimeout(10000);
                int responseCode = urlConnection.getResponseCode();
                InputStream inputStream = (responseCode >= 200 && responseCode < 300) ? urlConnection.getInputStream() : urlConnection.getErrorStream();
                if (inputStream == null) return "{\"error_message\":\"Нет потока (оплата), код: " + responseCode + "\"}";
                reader = new BufferedReader(new InputStreamReader(inputStream)); StringBuilder buffer = new StringBuilder(); String line;
                while ((line = reader.readLine()) != null) buffer.append(line).append("\n");
                responseJsonString = buffer.length() == 0 ? "{\"error_message\":\"Пустой ответ (оплата), код: " + responseCode + "\"}" : buffer.toString();
            } catch (Exception e) { Log.e("FetchOrdersForPayment", "Error: " + e.getMessage()); return "{\"error_message\":\"Ошибка загрузки (оплата): " + e.getMessage() + "\"}"; }
            finally { if (urlConnection != null) { urlConnection.disconnect(); } if (reader != null) { try { reader.close(); } catch (IOException ex) {} } }
            return responseJsonString;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            textViewStatus.setVisibility(View.GONE);
            if (result == null) { /* ... */ return; }
            Log.d("FetchOrdersForPayment", "Result: " + result);
            try {
                JSONArray jsonArray = new JSONArray(result);
                orderList.clear();
                if (jsonArray.length() == 0) {
                    textViewStatus.setText("Нет заказов, готовых к оплате.");
                    textViewStatus.setVisibility(View.VISIBLE);
                } else {
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject orderJson = jsonArray.getJSONObject(i);
                        int orderId = orderJson.getInt("id");
                        String description = orderJson.getString("description");
                        orderList.add(new OrderForReview(orderId, description));
                    }
                }
                ordersAdapter.notifyDataSetChanged();
            } catch (JSONException e) { /* ... */
                Log.e("FetchOrdersForPayment", "JSONException: " + e.getMessage());
                Toast.makeText(SelectOrderForPaymentActivity.this, "Ошибка парсинга заказов", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
