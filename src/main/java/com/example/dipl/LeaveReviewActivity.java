package com.example.dipl;

import androidx.appcompat.app.AppCompatActivity;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class LeaveReviewActivity extends AppCompatActivity {

    private static final String API_ORDERS_FOR_REVIEW_URL = "http://10.0.2.2:5000/api/orders_for_review";
    private static final String API_SUBMIT_REVIEW_URL = "http://10.0.2.2:5000/api/review";


    private Spinner spinnerSelectOrder, spinnerRating;
    private EditText editTextComment, editTextReviewerName;
    private Button buttonSubmitReview;
    private TextView textViewResponse, textViewSelectedOrderInfo, textViewLoadingOrders;
    private LinearLayout layoutReviewFormContainer;

    private ArrayAdapter<OrderForReview> ordersAdapter;
    private List<OrderForReview> orderList;
    private OrderForReview selectedOrderForReview;

    private ArrayAdapter<Integer> ratingAdapter;
    private Integer[] ratings = {1, 2, 3, 4, 5};
    private int currentLoggedInUserId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leave_review);

        spinnerSelectOrder = findViewById(R.id.spinnerSelectOrder);
        spinnerRating = findViewById(R.id.spinnerRating);
        editTextComment = findViewById(R.id.editTextReviewComment);
        buttonSubmitReview = findViewById(R.id.buttonSubmitReview);
        textViewResponse = findViewById(R.id.textViewReviewResponse);
        layoutReviewFormContainer = findViewById(R.id.layoutReviewFormContainer);
        textViewSelectedOrderInfo = findViewById(R.id.textViewSelectedOrderInfo);
        textViewLoadingOrders = findViewById(R.id.textViewLoadingOrders);
        editTextReviewerName = findViewById(R.id.editTextReviewerName);


        ratingAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, ratings);
        ratingAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRating.setAdapter(ratingAdapter);
        spinnerRating.setSelection(ratings.length - 1);


        orderList = new ArrayList<>();
        ordersAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, orderList);
        ordersAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSelectOrder.setAdapter(ordersAdapter);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        currentLoggedInUserId = prefs.getInt("LOGGED_IN_USER_ID", -1);

        spinnerSelectOrder.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedOrderForReview = (OrderForReview) parent.getItemAtPosition(position);
                if (selectedOrderForReview != null && selectedOrderForReview.getId() != 0) { // 0 - ID для заглушки
                    layoutReviewFormContainer.setVisibility(View.VISIBLE);
                    textViewSelectedOrderInfo.setText("Отзыв для: " + selectedOrderForReview.getDescription());
                    buttonSubmitReview.setEnabled(true);
                } else {
                    layoutReviewFormContainer.setVisibility(View.GONE);
                    textViewSelectedOrderInfo.setText("");
                    buttonSubmitReview.setEnabled(false);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedOrderForReview = null;
                layoutReviewFormContainer.setVisibility(View.GONE);
                textViewSelectedOrderInfo.setText("");
                buttonSubmitReview.setEnabled(false);
            }
        });




        loadOrders();

        buttonSubmitReview.setOnClickListener(v -> submitReview());
    }

    private void loadOrders() {
        textViewLoadingOrders.setVisibility(View.VISIBLE);
        layoutReviewFormContainer.setVisibility(View.GONE);
        buttonSubmitReview.setEnabled(false);
        new FetchOrdersForReviewTask().execute(API_ORDERS_FOR_REVIEW_URL);
    }

    private void submitReview() {
        if (selectedOrderForReview == null || selectedOrderForReview.getId() == 0) {
            Toast.makeText(this, "Пожалуйста, выберите заказ", Toast.LENGTH_SHORT).show();
            return;
        }
        String comment = editTextComment.getText().toString().trim();
        String reviewerName = editTextReviewerName.getText().toString().trim();
        Integer selectedRating = (Integer) spinnerRating.getSelectedItem();

        if (reviewerName.isEmpty()) {
            Toast.makeText(this, "Пожалуйста, введите ваше имя", Toast.LENGTH_SHORT).show();
            return;
        }
        if (comment.isEmpty()) { /* ... */ return; }
        if (selectedRating == null) { /* ... */ return; }

        JSONObject reviewData = new JSONObject();
        try {
            reviewData.put("order_id", selectedOrderForReview.getId());
            reviewData.put("rating", selectedRating);
            reviewData.put("comment", comment);
            reviewData.put("reviewer_name", reviewerName);
            if (currentLoggedInUserId != -1) {
                // Отправляем ID залогиненного пользователя
                reviewData.put("user_id", currentLoggedInUserId);
            }
        } catch (JSONException e) {
            Log.e("LeaveReviewActivity", "JSONException: " + e.getMessage());
            textViewResponse.setText("Ошибка создания JSON для отзыва.");
            return;
        }
        new SubmitReviewTask().execute(API_SUBMIT_REVIEW_URL, reviewData.toString());
    }

    private class FetchOrdersForReviewTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {

            String urlString = params[0]; HttpURLConnection urlConnection = null; BufferedReader reader = null; String responseJsonString = null;
            try {
                URL url = new URL(urlString); urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET"); urlConnection.setRequestProperty("Accept", "application/json");
                urlConnection.setConnectTimeout(15000); urlConnection.setReadTimeout(10000);
                int responseCode = urlConnection.getResponseCode();
                InputStream inputStream = (responseCode >= 200 && responseCode < 300) ? urlConnection.getInputStream() : urlConnection.getErrorStream();
                if (inputStream == null) return "{\"error_message\":\"Нет потока (заказы), код: " + responseCode + "\"}";
                reader = new BufferedReader(new InputStreamReader(inputStream)); StringBuilder buffer = new StringBuilder(); String line;
                while ((line = reader.readLine()) != null) buffer.append(line).append("\n");
                responseJsonString = buffer.length() == 0 ? "{\"error_message\":\"Пустой ответ (заказы), код: " + responseCode + "\"}" : buffer.toString();
            } catch (Exception e) {
                Log.e("FetchOrdersTask", "Error: " + e.getMessage());
                return "{\"error_message\":\"Ошибка загрузки заказов: " + e.getMessage() + "\"}";
            } finally {
                if (urlConnection != null) { urlConnection.disconnect(); }
                if (reader != null) { try { reader.close(); } catch (IOException e) { /* ignore */ } }
            }
            return responseJsonString;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            textViewLoadingOrders.setVisibility(View.GONE);
            if (result == null) {
                Toast.makeText(LeaveReviewActivity.this, "Не удалось загрузить заказы (null)", Toast.LENGTH_LONG).show();
                return;
            }
            Log.d("FetchOrdersTask", "Raw Orders Result: " + result);
            orderList.clear();
            orderList.add(new OrderForReview(0, "Выберите заказ..."));
            try {
                JSONArray jsonArray = new JSONArray(result);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject orderJson = jsonArray.getJSONObject(i);
                    int id = orderJson.getInt("id");
                    String description = orderJson.getString("description");
                    orderList.add(new OrderForReview(id, description));
                }
            } catch (JSONException e) {
                Log.e("FetchOrdersTask", "JSONException: " + e.getMessage() + "\nData: " + result);
                Toast.makeText(LeaveReviewActivity.this, "Ошибка парсинга списка заказов.", Toast.LENGTH_LONG).show();
                try {
                    JSONObject errorObject = new JSONObject(result);
                    if (errorObject.has("error_message")) textViewResponse.setText(errorObject.getString("error_message"));
                } catch (JSONException ignored) {}
            } finally {
                ordersAdapter.notifyDataSetChanged();
                if (orderList.size() > 1) {
                    spinnerSelectOrder.setSelection(0);
                    layoutReviewFormContainer.setVisibility(View.GONE);
                    buttonSubmitReview.setEnabled(false);
                } else {
                    Toast.makeText(LeaveReviewActivity.this, "Нет заказов для отзыва.", Toast.LENGTH_SHORT).show();
                    layoutReviewFormContainer.setVisibility(View.GONE);
                    buttonSubmitReview.setEnabled(false);
                }
            }
        }
    }

    private class SubmitReviewTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            String urlString = params[0]; String jsonData = params[1]; HttpURLConnection urlConnection = null; String responseString = null;
            try {
                URL url = new URL(urlString); urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("POST"); urlConnection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                urlConnection.setRequestProperty("Accept", "application/json");
                urlConnection.setConnectTimeout(15000); urlConnection.setReadTimeout(10000); urlConnection.setDoOutput(true);
                OutputStream os = urlConnection.getOutputStream(); byte[] input = jsonData.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length); os.close();
                int responseCode = urlConnection.getResponseCode();
                InputStream inputStream = (responseCode >= 200 && responseCode < 300) ? urlConnection.getInputStream() : urlConnection.getErrorStream();
                if (inputStream != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream)); StringBuilder sb = new StringBuilder(); String line;
                    while ((line = reader.readLine()) != null) { sb.append(line); }
                    reader.close(); responseString = sb.toString();
                } else { responseString = "{\"error\":\"Нет ответа (отзыв), код: " + responseCode + "\"}"; }
                Log.d("SubmitReviewTask", "Response (" + responseCode + "): " + responseString);
            } catch (Exception e) {
                Log.e("SubmitReviewTask", "Error: " + e.getMessage());
                responseString = "{\"error\":\"Ошибка сети (отзыв): " + e.getMessage() + "\"}";
            } finally { if (urlConnection != null) { urlConnection.disconnect(); } }
            return responseString;
        }
        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result); textViewResponse.setText("");
            if (result == null) { textViewResponse.setText("Ошибка: нет ответа (отзыв)."); return; }
            try {
                JSONObject jsonResponse = new JSONObject(result);
                if (jsonResponse.has("message")) {
                    Toast.makeText(LeaveReviewActivity.this, jsonResponse.getString("message"), Toast.LENGTH_LONG).show();
                    editTextComment.setText("");
                    spinnerRating.setSelection(ratings.length - 1);
                    loadOrders();
                } else if (jsonResponse.has("error")) {
                    textViewResponse.setText("Ошибка: " + jsonResponse.getString("error"));
                } else { textViewResponse.setText("Неизвестный ответ (отзыв)."); }
            } catch (JSONException e) {
                Log.e("SubmitReviewTask", "JSONException: " + e.getMessage() + "\nData: " + result);
                textViewResponse.setText("Ошибка парсинга (отзыв): " + result);
            }
        }
    }
}
