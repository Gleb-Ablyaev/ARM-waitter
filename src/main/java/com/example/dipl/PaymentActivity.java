package com.example.dipl;

import androidx.appcompat.app.AppCompatActivity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
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
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;

public class PaymentActivity extends AppCompatActivity {

    private static final String TAG = "PaymentActivity";
    private static final String API_ORDER_DETAILS_BASE_URL = "http://10.0.2.2:5000/api/order/";
    private static final String API_PAY_ORDER_SUFFIX = "/pay";

    private TextView textViewPaymentOrderTitle, textViewTotalAmount, textViewOrderItemsDetails, textViewPaymentResponse;
    private Button buttonConfirmPayment;

    private int orderIdForPayment = -1;
    private DecimalFormat currencyFormatter = new DecimalFormat("#,##0.00");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment);

        textViewPaymentOrderTitle = findViewById(R.id.textViewPaymentOrderTitle);
        textViewTotalAmount = findViewById(R.id.textViewTotalAmount);
        textViewOrderItemsDetails = findViewById(R.id.textViewOrderItemsDetails);
        textViewPaymentResponse = findViewById(R.id.textViewPaymentResponse);
        buttonConfirmPayment = findViewById(R.id.buttonConfirmPayment);

        orderIdForPayment = getIntent().getIntExtra("ORDER_ID", -1);

        if (orderIdForPayment == -1) {
            Toast.makeText(this, "Ошибка: ID заказа не передан для оплаты", Toast.LENGTH_LONG).show();
            Log.e(TAG, "ORDER_ID не был передан в Intent.");
            finish();
            return;
        }
        textViewPaymentOrderTitle.setText("Расчет Заказа #" + orderIdForPayment);

        loadOrderDetailsAndAmount();

        buttonConfirmPayment.setOnClickListener(v -> confirmPayment());
    }

    private void loadOrderDetailsAndAmount() {
        textViewPaymentResponse.setText("Загрузка данных заказа...");
        textViewPaymentResponse.setVisibility(View.VISIBLE);
        buttonConfirmPayment.setEnabled(false);
        new FetchOrderDetailsForPaymentTask().execute(API_ORDER_DETAILS_BASE_URL + orderIdForPayment);
    }

    private void confirmPayment() {
        textViewPaymentResponse.setText("Обработка оплаты...");
        textViewPaymentResponse.setVisibility(View.VISIBLE);
        buttonConfirmPayment.setEnabled(false);

        new SubmitPaymentTask().execute("POST", API_ORDER_DETAILS_BASE_URL + orderIdForPayment + API_PAY_ORDER_SUFFIX, "PAY_ACTION");
    }


    private class FetchOrderDetailsForPaymentTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            return performGetRequest(params[0]);
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            textViewPaymentResponse.setVisibility(View.GONE);

            if (result == null) {
                String errorMsg = "Ошибка загрузки деталей заказа (нет ответа от сервера).";
                textViewPaymentResponse.setText(errorMsg);
                textViewPaymentResponse.setVisibility(View.VISIBLE);
                Toast.makeText(PaymentActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                return;
            }
            Log.d(TAG, "Order Details for Payment Result: " + result);
            try {
                JSONObject orderJson = new JSONObject(result);
                if (orderJson.has("error")) {
                    String errorMsg = "Ошибка: " + orderJson.getString("error");
                    textViewPaymentResponse.setText(errorMsg);
                    textViewPaymentResponse.setVisibility(View.VISIBLE);
                    Toast.makeText(PaymentActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                } else {
                    double totalAmount = orderJson.optDouble("total_amount", 0.0);
                    textViewTotalAmount.setText(currencyFormatter.format(totalAmount));

                    JSONArray itemsArray = orderJson.optJSONArray("items");
                    StringBuilder itemsBuilder = new StringBuilder();
                    if (itemsArray != null && itemsArray.length() > 0) {
                        itemsBuilder.append("Состав заказа:\n");
                        for (int i = 0; i < itemsArray.length(); i++) {
                            JSONObject item = itemsArray.getJSONObject(i);
                            itemsBuilder.append("- ")
                                    .append(item.optString("menu_item_name", "Неизвестное блюдо"))
                                    .append(" x").append(item.optInt("quantity", 0));
                            if (item.has("price")) {
                                itemsBuilder.append(" (")
                                        .append(currencyFormatter.format(item.optDouble("price", 0.0) * item.optInt("quantity", 0)))
                                        .append(")");
                            }
                            itemsBuilder.append("\n");
                        }
                    } else {
                        itemsBuilder.append("В заказе нет позиций.");
                    }
                    textViewOrderItemsDetails.setText(itemsBuilder.toString());
                    buttonConfirmPayment.setEnabled(true); // Разблокируем кнопку после успешной загрузки
                }
            } catch (JSONException e) {
                Log.e(TAG, "JSONException при парсинге деталей заказа: " + e.getMessage() + "\nData: " + result);
                String errorMsg = "Ошибка парсинга данных заказа.";
                textViewPaymentResponse.setText(errorMsg);
                textViewPaymentResponse.setVisibility(View.VISIBLE);
                Toast.makeText(PaymentActivity.this, errorMsg, Toast.LENGTH_LONG).show();
            }
        }
    }


    private class SubmitPaymentTask extends AsyncTask<String, Void, String> {
        private String method;

        @Override
        protected String doInBackground(String... params) {
            method = params[0];
            String urlString = params[1];


            HttpURLConnection urlConnection = null;
            String responseString = null;
            try {
                URL url = new URL(urlString);
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod(method);
                urlConnection.setConnectTimeout(15000);
                urlConnection.setReadTimeout(10000);

                int responseCode = urlConnection.getResponseCode();
                Log.d(TAG, method + " to " + urlString + " Response Code: " + responseCode);

                InputStream inputStream = (responseCode >= 200 && responseCode < 300) ?
                        urlConnection.getInputStream() : urlConnection.getErrorStream();

                if (inputStream != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    StringBuilder sb = new StringBuilder(); String line;
                    while ((line = reader.readLine()) != null) { sb.append(line); }
                    reader.close();
                    responseString = sb.toString();
                } else {
                    responseString = "{\"error\":\"Нет ответа от сервера (оплата), код: " + responseCode + "\"}";
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception in SubmitPaymentTask: " + e.getMessage());
                responseString = "{\"error\":\"Ошибка сети при оплате: " + e.getMessage() + "\"}";
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
            }
            return responseString;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            textViewPaymentResponse.setVisibility(View.GONE);

            if (result == null) {
                String errorMsg = "Ошибка оплаты (нет ответа от сервера).";
                textViewPaymentResponse.setText(errorMsg);
                textViewPaymentResponse.setVisibility(View.VISIBLE);
                buttonConfirmPayment.setEnabled(true);
                return;
            }
            Log.d(TAG, "Payment Operation Result: " + result);
            try {
                JSONObject jsonResponse = new JSONObject(result);
                if (jsonResponse.has("message")) {
                    Toast.makeText(PaymentActivity.this, jsonResponse.getString("message"), Toast.LENGTH_LONG).show();
                    setResult(RESULT_OK);
                    finish();
                } else if (jsonResponse.has("error")) {
                    String errorMsg = "Ошибка: " + jsonResponse.getString("error");
                    textViewPaymentResponse.setText(errorMsg);
                    textViewPaymentResponse.setVisibility(View.VISIBLE);
                    Toast.makeText(PaymentActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                    buttonConfirmPayment.setEnabled(true);
                } else {
                    String unknownMsg = "Неизвестный ответ от сервера.";
                    textViewPaymentResponse.setText(unknownMsg);
                    textViewPaymentResponse.setVisibility(View.VISIBLE);
                    buttonConfirmPayment.setEnabled(true);
                }
            } catch (JSONException e) {
                Log.e(TAG, "JSONException on SubmitPayment: " + e.getMessage() + "\nData: " + result);
                String parseErrorMsg = "Ошибка парсинга ответа сервера (оплата).";
                textViewPaymentResponse.setText(parseErrorMsg);
                textViewPaymentResponse.setVisibility(View.VISIBLE);
                buttonConfirmPayment.setEnabled(true);
            }
        }
    }


    private String performGetRequest(String urlString) {
        HttpURLConnection urlConnection = null;
        BufferedReader reader = null;
        String responseJsonString = null;
        try {
            URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.setRequestProperty("Accept", "application/json");
            urlConnection.setConnectTimeout(15000);
            urlConnection.setReadTimeout(10000);

            int responseCode = urlConnection.getResponseCode();
            Log.d(TAG, "GET to " + urlString + " Response Code: " + responseCode);

            InputStream inputStream = (responseCode >= 200 && responseCode < 300) ?
                    urlConnection.getInputStream() : urlConnection.getErrorStream();

            if (inputStream == null) {
                return "{\"error_message\":\"Нет потока ответа от сервера, код: " + responseCode + "\"}";
            }
            reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder buffer = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line).append("\n");
            }
            if (buffer.length() == 0) {
                return "{\"error_message\":\"Пустой ответ от сервера, код: " + responseCode + "\"}";
            }
            responseJsonString = buffer.toString();
        } catch (MalformedURLException e) {
            Log.e(TAG, "MalformedURLException: " + e.getMessage());
            return "{\"error_message\":\"Ошибка URL: " + e.getMessage() + "\"}";
        } catch (IOException e) {
            Log.e(TAG, "IOException: " + e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("Unable to resolve host")) {
                return "{\"error_message\":\"Ошибка сети: Не удалось найти хост.\"}";
            }
            return "{\"error_message\":\"Ошибка ввода-вывода: " + e.getMessage() + "\"}";
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (final IOException e) {
                    Log.e(TAG, "Error closing stream", e);
                }
            }
        }
        return responseJsonString;
    }
}