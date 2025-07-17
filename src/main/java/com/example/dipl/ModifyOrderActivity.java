package com.example.dipl;
import androidx.appcompat.app.AppCompatActivity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ModifyOrderActivity extends AppCompatActivity {

    private static final String TAG = "ModifyOrderActivity";
    private static final String API_BASE_URL_ORDER_OPS = "http://10.0.2.2:5000/api/order/";
    private static final String API_ALL_MENU_ITEMS_URL = "http://10.0.2.2:5000/api/menu_items";

    private TextView textViewModifyOrderTitle, textViewModifyResponse;
    private EditText editTextModifyTable;
    private ListView listViewModifyOrderItems;
    private Button buttonSaveChanges, buttonCancelOrder;

    private MenuItemsAdapter modifiableItemsAdapter;
    private List<MenuItem> allMenuItemsFromSource;
    private List<MenuItem> itemsCurrentlyInOrder;

    private int orderIdToModify = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_modify_order);

        textViewModifyOrderTitle = findViewById(R.id.textViewModifyOrderTitle);
        textViewModifyResponse = findViewById(R.id.textViewModifyResponse);
        editTextModifyTable = findViewById(R.id.editTextModifyTable);
        listViewModifyOrderItems = findViewById(R.id.listViewModifyOrderItems);
        buttonSaveChanges = findViewById(R.id.buttonSaveChanges);
        buttonCancelOrder = findViewById(R.id.buttonCancelOrder);

        allMenuItemsFromSource = new ArrayList<>();
        itemsCurrentlyInOrder = new ArrayList<>();
        modifiableItemsAdapter = new MenuItemsAdapter(this, itemsCurrentlyInOrder);
        listViewModifyOrderItems.setAdapter(modifiableItemsAdapter);

        orderIdToModify = getIntent().getIntExtra("ORDER_ID", -1);

        if (orderIdToModify == -1) {
            Toast.makeText(this, "Ошибка: ID заказа не передан", Toast.LENGTH_LONG).show();
            Log.e(TAG, "ORDER_ID не был передан в Intent.");
            finish();
            return;
        }
        textViewModifyOrderTitle.setText("Изменение Заказа #" + orderIdToModify);


        loadAllMenuItems();

        buttonSaveChanges.setOnClickListener(v -> saveChanges());
        buttonCancelOrder.setOnClickListener(v -> cancelOrder());
    }

    private void loadAllMenuItems() {
        textViewModifyResponse.setText("Загрузка меню...");
        textViewModifyResponse.setVisibility(View.VISIBLE);
        new FetchAllMenuItemsTask().execute(API_ALL_MENU_ITEMS_URL);
    }

    private void loadOrderDetails() {
        if (orderIdToModify != -1) {
            textViewModifyResponse.setText("Загрузка деталей заказа...");
            new FetchOrderDetailsTask().execute(API_BASE_URL_ORDER_OPS + orderIdToModify);
        }
    }

    private void populateAdapterWithOrderDetails(JSONObject orderDetailsJson) {
        try {
            editTextModifyTable.setText(orderDetailsJson.optString("table_number", ""));
            JSONArray itemsInOrderJsonArray = orderDetailsJson.getJSONArray("items");

            itemsCurrentlyInOrder.clear();


            for (MenuItem menuItemFromCatalog : allMenuItemsFromSource) {

                MenuItem displayItem = new MenuItem(menuItemFromCatalog.getId(), menuItemFromCatalog.getName());
                displayItem.setSelected(false);
                displayItem.setQuantity(1);


                for (int i = 0; i < itemsInOrderJsonArray.length(); i++) {
                    JSONObject orderItemJson = itemsInOrderJsonArray.getJSONObject(i);
                    if (orderItemJson.getInt("menu_item_id") == displayItem.getId()) {
                        displayItem.setSelected(true);
                        displayItem.setQuantity(orderItemJson.getInt("quantity"));
                        break;
                    }
                }
                itemsCurrentlyInOrder.add(displayItem);
            }
            modifiableItemsAdapter.notifyDataSetChanged();
            textViewModifyResponse.setVisibility(View.GONE);

        } catch (JSONException e) {
            Log.e(TAG, "JSONException при парсинге деталей заказа: " + e.getMessage());
            textViewModifyResponse.setText("Ошибка отображения деталей заказа.");
            Toast.makeText(this, "Ошибка парсинга деталей заказа.", Toast.LENGTH_LONG).show();
        }
    }

    private void saveChanges() {
        String table = editTextModifyTable.getText().toString().trim();


        JSONArray itemsToUpdateArray = new JSONArray();
        for (MenuItem item : itemsCurrentlyInOrder) {
            if (item.isSelected() && item.getQuantity() > 0) {
                try {
                    JSONObject itemJson = new JSONObject();
                    itemJson.put("itemId", item.getId());
                    itemJson.put("qty", item.getQuantity());
                    itemsToUpdateArray.put(itemJson);
                } catch (JSONException e) {
                    Log.e(TAG, "JSONException при формировании элемента заказа для обновления: " + e.getMessage());
                    Toast.makeText(this, "Ошибка подготовки данных заказа", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        }



        JSONObject putData = new JSONObject();
        try {

            putData.put("items", itemsToUpdateArray);
        } catch (JSONException e) {
            Log.e(TAG, "JSONException при формировании JSON для PUT: " + e.getMessage());
            Toast.makeText(this, "Ошибка подготовки данных для сохранения", Toast.LENGTH_SHORT).show();
            return;
        }

        textViewModifyResponse.setText("Сохранение изменений...");
        textViewModifyResponse.setVisibility(View.VISIBLE);
        new SubmitOrderOperationTask().execute("PUT", API_BASE_URL_ORDER_OPS + orderIdToModify, putData.toString());
    }

    private void cancelOrder() {
        textViewModifyResponse.setText("Отмена заказа...");
        textViewModifyResponse.setVisibility(View.VISIBLE);
        new SubmitOrderOperationTask().execute("POST", API_BASE_URL_ORDER_OPS + orderIdToModify + "/cancel", "CANCEL_ACTION");
    }



    private class FetchAllMenuItemsTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            return performGetRequest(params[0]);
        }

        @Override
        protected void onPostExecute(String result) {
            if (result == null) {
                textViewModifyResponse.setText("Ошибка загрузки меню (null).");
                Toast.makeText(ModifyOrderActivity.this, "Не удалось загрузить меню.", Toast.LENGTH_LONG).show();
                return;
            }
            Log.d(TAG, "All Menu Items Result: " + result);
            try {
                JSONArray jsonArray = new JSONArray(result);
                allMenuItemsFromSource.clear();
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject menuItemJson = jsonArray.getJSONObject(i);
                    allMenuItemsFromSource.add(new MenuItem(menuItemJson.getInt("id"), menuItemJson.getString("name")));
                }
                if (!allMenuItemsFromSource.isEmpty()) {
                    loadOrderDetails();
                } else {
                    textViewModifyResponse.setText("Меню не загружено или пусто.");
                    Toast.makeText(ModifyOrderActivity.this, "Меню пусто.", Toast.LENGTH_SHORT).show();
                }
            } catch (JSONException e) {
                Log.e(TAG, "JSONException при парсинге меню: " + e.getMessage() + "\nData: " + result);
                textViewModifyResponse.setText("Ошибка парсинга меню.");
                Toast.makeText(ModifyOrderActivity.this, "Ошибка данных меню.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private class FetchOrderDetailsTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            return performGetRequest(params[0]);
        }

        @Override
        protected void onPostExecute(String result) {
            if (result == null) {
                textViewModifyResponse.setText("Ошибка загрузки деталей заказа (null).");
                Toast.makeText(ModifyOrderActivity.this, "Не удалось загрузить детали заказа.", Toast.LENGTH_LONG).show();
                return;
            }
            Log.d(TAG, "Order Details Result: " + result);
            try {
                JSONObject orderDetailsJson = new JSONObject(result);
                if (orderDetailsJson.has("error")) {
                    String errorMsg = "Ошибка: " + orderDetailsJson.getString("error");
                    textViewModifyResponse.setText(errorMsg);
                    Toast.makeText(ModifyOrderActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                } else {
                    populateAdapterWithOrderDetails(orderDetailsJson);
                }
            } catch (JSONException e) {
                Log.e(TAG, "JSONException при парсинге деталей заказа: " + e.getMessage() + "\nData: " + result);
                textViewModifyResponse.setText("Ошибка парсинга деталей заказа.");
                Toast.makeText(ModifyOrderActivity.this, "Ошибка данных заказа.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private class SubmitOrderOperationTask extends AsyncTask<String, Void, String> {
        private String method;

        @Override
        protected String doInBackground(String... params) {
            method = params[0];
            String urlString = params[1];
            String jsonData = params[2];

            HttpURLConnection urlConnection = null;
            String responseString = null;
            try {
                URL url = new URL(urlString);
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod(method);
                urlConnection.setConnectTimeout(15000);
                urlConnection.setReadTimeout(10000);

                if (method.equals("PUT")) {
                    urlConnection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    urlConnection.setRequestProperty("Accept", "application/json");
                    urlConnection.setDoOutput(true);
                    OutputStream os = urlConnection.getOutputStream();
                    byte[] input = jsonData.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                    os.close();
                } else if (method.equals("POST") && urlString.endsWith("/cancel")) {

                    urlConnection.setDoOutput(false);
                }


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
                    responseString = "{\"error\":\"Нет ответа от сервера, код: " + responseCode + "\"}";
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception in SubmitOrderOperationTask: " + e.getMessage());
                responseString = "{\"error\":\"Ошибка сети: " + e.getMessage() + "\"}";
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
            }
            return responseString;
        }

        @Override
        protected void onPostExecute(String result) {
            textViewModifyResponse.setVisibility(View.GONE);
            if (result == null) {
                textViewModifyResponse.setText("Ошибка операции (null).");
                textViewModifyResponse.setVisibility(View.VISIBLE);
                return;
            }
            Log.d(TAG, "Submit Operation Result: " + result);
            try {
                JSONObject jsonResponse = new JSONObject(result);
                if (jsonResponse.has("message")) {
                    Toast.makeText(ModifyOrderActivity.this, jsonResponse.getString("message"), Toast.LENGTH_LONG).show();
                    finish(); // Закрыть Activity после успешной операции
                } else if (jsonResponse.has("error")) {
                    String errorMsg = "Ошибка: " + jsonResponse.getString("error");
                    textViewModifyResponse.setText(errorMsg);
                    textViewModifyResponse.setVisibility(View.VISIBLE);
                    Toast.makeText(ModifyOrderActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                } else {
                    textViewModifyResponse.setText("Неизвестный ответ от сервера.");
                    textViewModifyResponse.setVisibility(View.VISIBLE);
                }
            } catch (JSONException e) {
                Log.e(TAG, "JSONException on SubmitOperation: " + e.getMessage() + "\nData: " + result);
                textViewModifyResponse.setText("Ошибка парсинга ответа: " + result);
                textViewModifyResponse.setVisibility(View.VISIBLE);
            }
        }
    }


    // Вспомогательный метод для GET-запросов, чтобы не дублировать код
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