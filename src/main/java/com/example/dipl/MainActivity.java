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
import android.content.Intent;
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

public class MainActivity extends AppCompatActivity {

    private static final String BASE_URL = "http://10.0.2.2:5000/api";

    private EditText editTextTable;
    private ListView listViewMenuItems;
    private Button buttonSendOrder, buttonGetNewOrders, buttonGetReport;
    private TextView textViewResponse;

    private List<MenuItem> menuItemsList;
    private MenuItemsAdapter menuItemsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        editTextTable = findViewById(R.id.editTextTable);
        listViewMenuItems = findViewById(R.id.listViewMenuItems);
        buttonSendOrder = findViewById(R.id.buttonSendOrder);
        buttonGetNewOrders = findViewById(R.id.buttonGetNewOrders);
        buttonGetReport = findViewById(R.id.buttonGetReport);
        textViewResponse = findViewById(R.id.textViewResponse);

        menuItemsList = new ArrayList<>();
        menuItemsAdapter = new MenuItemsAdapter(this, menuItemsList);
        listViewMenuItems.setAdapter(menuItemsAdapter);

        loadMenuItems();

        buttonSendOrder.setOnClickListener(v -> sendOrder());
        buttonGetNewOrders.setOnClickListener(v -> getNewOrders());
        buttonGetReport.setOnClickListener(v -> getReport());

    }

    private void loadMenuItems() {
        new FetchMenuItemsTask().execute(BASE_URL + "/menu_items");
    }

    private void sendOrder() {
        String table = editTextTable.getText().toString().trim();
        if (table.isEmpty()) {
            Toast.makeText(this, "Пожалуйста, введите номер стола", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONArray itemsArray = new JSONArray();
        List<String> orderedItemsDescriptions = new ArrayList<>();

        for (MenuItem item : menuItemsList) {
            if (item.isSelected()) {
                if (item.getQuantity() <= 0) {
                    Toast.makeText(this, "Количество для '" + item.getName() + "' должно быть больше 0", Toast.LENGTH_SHORT).show();
                    return;
                }
                JSONObject orderItemJson = new JSONObject();
                try {
                    orderItemJson.put("itemId", item.getId());
                    orderItemJson.put("qty", item.getQuantity());
                    itemsArray.put(orderItemJson);
                    orderedItemsDescriptions.add(item.getName() + " x" + item.getQuantity());
                } catch (JSONException e) {
                    Log.e("SendOrder", "JSONException while creating item: " + e.getMessage());
                    textViewResponse.setText("Ошибка формирования JSON для блюда: " + item.getName());
                    return;
                }
            }
        }

        if (itemsArray.length() == 0) {
            Toast.makeText(this, "Пожалуйста, выберите хотя бы одно блюдо", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONObject postData = new JSONObject();
        try {
            postData.put("table", table);
            postData.put("items", itemsArray);
        } catch (JSONException e) {
            textViewResponse.setText("Ошибка создания основного JSON: " + e.getMessage());
            Log.e("SendOrder", "JSONException while creating main JSON: " + e.getMessage());
            return;
        }


        String currentOrderDescription = String.join(", ", orderedItemsDescriptions);
        new NetworkTask(currentOrderDescription, table).execute("POST", BASE_URL + "/order", postData.toString());
    }

    private void getNewOrders() {
        new NetworkTask(null, null).execute("GET", BASE_URL + "/orders/new", null);
    }

    private void getReport() {
        new NetworkTask(null, null).execute("GET", BASE_URL + "/report", null);
    }


    private class FetchMenuItemsTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            // ... (такой же как раньше)
            String urlString = params[0];
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
                InputStream inputStream = (responseCode >= 200 && responseCode < 300) ? urlConnection.getInputStream() : urlConnection.getErrorStream();
                if (inputStream == null) return "{\"error_message\":\"Нет потока (меню), код: " + responseCode + "\"}";
                reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder buffer = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) buffer.append(line).append("\n");
                responseJsonString = buffer.length() == 0 ? "{\"error_message\":\"Пустой ответ (меню), код: " + responseCode + "\"}" : buffer.toString();
            } catch (Exception e) {
                Log.e("FetchMenuItemsTask", "Error: " + e.getMessage());
                return "{\"error_message\":\"Ошибка загрузки меню: " + e.getMessage() + "\"}";
            } finally {
                if (urlConnection != null) { urlConnection.disconnect(); }
                if (reader != null) { try { reader.close(); } catch (IOException e) { /* ignore */ } }
            }
            return responseJsonString;
        }
        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            if (result == null) {
                Toast.makeText(MainActivity.this, "Не удалось загрузить список блюд (null)", Toast.LENGTH_LONG).show();
                return;
            }
            Log.d("FetchMenuItemsTask", "Raw Menu Result: " + result);
            try {
                JSONArray jsonArray = new JSONArray(result);
                menuItemsList.clear();
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject menuItemJson = jsonArray.getJSONObject(i);
                    int id = menuItemJson.getInt("id");
                    String name = menuItemJson.getString("name");
                    menuItemsList.add(new MenuItem(id, name));
                }
                menuItemsAdapter.notifyDataSetChanged();
                if (menuItemsList.isEmpty()){
                    Toast.makeText(MainActivity.this, "Список блюд пуст.", Toast.LENGTH_SHORT).show();
                }
            } catch (JSONException e) {
                Log.e("FetchMenuItemsTask", "JSONException: " + e.getMessage() + "\nData: " + result);
                Toast.makeText(MainActivity.this, "Ошибка парсинга списка блюд.", Toast.LENGTH_LONG).show();
                try {
                    JSONObject errorObject = new JSONObject(result);
                    if (errorObject.has("error_message")) textViewResponse.setText("Ошибка загрузки меню: " + errorObject.getString("error_message"));
                    else if (errorObject.has("error")) textViewResponse.setText("Ошибка загрузки меню: " + errorObject.getString("error"));
                } catch (JSONException ignored) {}
            }
        }
    }


    private class NetworkTask extends AsyncTask<String, Void, String> {
        private String requestMethod;
        private String requestUrl;
        private String orderDescriptionForToast;
        private String orderTableForToast;


        public NetworkTask(String orderDescription, String table) {
            this.orderDescriptionForToast = orderDescription;
            this.orderTableForToast = table;
        }


        @Override
        protected String doInBackground(String... params) {

            requestMethod = params[0];
            requestUrl = params[1];
            String jsonData = params[2];
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;
            String responseJsonString = null;
            try {
                URL url = new URL(requestUrl);
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod(requestMethod);
                urlConnection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                urlConnection.setRequestProperty("Accept", "application/json");
                urlConnection.setConnectTimeout(15000);
                urlConnection.setReadTimeout(10000);
                if ("POST".equals(requestMethod) && jsonData != null) {
                    urlConnection.setDoOutput(true);
                    OutputStream os = urlConnection.getOutputStream();
                    byte[] input = jsonData.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                    os.close();
                }
                int responseCode = urlConnection.getResponseCode();
                Log.d("NetworkTask", "URL: " + requestUrl + ", Response Code: " + responseCode);
                InputStream inputStream = (responseCode >= 200 && responseCode < 300) ? urlConnection.getInputStream() : urlConnection.getErrorStream();
                if (inputStream == null) return "{\"error_message\":\"Нет потока ответа, код: " + responseCode + "\"}";
                reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder buffer = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) buffer.append(line).append("\n");
                responseJsonString = buffer.length() == 0 ? "{\"error_message\":\"Пустой ответ, код: " + responseCode + "\"}" : buffer.toString();
            } catch (Exception e) {
                Log.e("NetworkTask", "Error: " + e.getMessage());
                String errorMessage = (e.getMessage() != null && e.getMessage().contains("Unable to resolve host")) ?
                        "Ошибка сети: Не удалось найти хост. Проверьте IP и сеть." :
                        "Ошибка ввода-вывода: " + e.getMessage();
                return "{\"error_message\":\"" + errorMessage + "\"}";
            } finally {
                if (urlConnection != null) { urlConnection.disconnect(); }
                if (reader != null) { try { reader.close(); } catch (IOException e) { /* ignore */ } }
            }
            return responseJsonString;
        }


        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            if (result == null) {
                textViewResponse.setText("Ошибка: Получен null результат.");
                return;
            }
            Log.d("NetworkTask", "Raw Result for " + requestUrl + ": " + result);

            try {
                // Проверяем, был ли это запрос на получение новых заказов
                if (requestMethod.equals("GET") && requestUrl.endsWith("/orders/new")) {
                    JSONArray jsonArray = new JSONArray(result); // Ожидаем массив заказов
                    if (jsonArray.length() == 0) {
                        textViewResponse.setText("Новых заказов нет.");
                        return;
                    }

                    StringBuilder ordersText = new StringBuilder("Новые заказы:\n\n");
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject orderObject = jsonArray.getJSONObject(i);
                        int orderId = orderObject.getInt("orderId");
                        String tableNumber = orderObject.getString("tableNumber");
                        String orderTime = orderObject.optString("orderTime", "Время не указано");

                        ordersText.append("Заказ ID: ").append(orderId).append("\n");
                        ordersText.append("Стол: ").append(tableNumber).append("\n");
                        ordersText.append("Время: ").append(orderTime).append("\n");
                        ordersText.append("Блюда:\n");

                        JSONArray itemsArray = orderObject.getJSONArray("items");
                        for (int j = 0; j < itemsArray.length(); j++) {
                            JSONObject itemObject = itemsArray.getJSONObject(j);
                            String itemName = itemObject.getString("menuItemName");
                            int quantity = itemObject.getInt("quantity");
                            ordersText.append("  - ").append(itemName).append(" x").append(quantity).append("\n");
                        }
                        ordersText.append("\n--------------------------------\n");
                    }
                    textViewResponse.setText(ordersText.toString());

                } else if (requestMethod.equals("POST") && requestUrl.endsWith("/order")) {
                    // Логика для ответа на создание заказа
                    JSONObject jsonObject = new JSONObject(result);
                    if (jsonObject.has("message") && jsonObject.has("orderId")) {
                        String serverMessage = jsonObject.getString("message");
                        int orderId = jsonObject.getInt("orderId");
                        String successMessage = serverMessage + " (ID: " + orderId + ")\n" +
                                "Стол: " + orderTableForToast + "\n" +
                                "Заказано: " + orderDescriptionForToast;
                        textViewResponse.setText(successMessage);
                        Toast.makeText(MainActivity.this, "Заказ успешно отправлен!", Toast.LENGTH_SHORT).show();
                        editTextTable.setText("");
                        for(MenuItem item : menuItemsList) {
                            item.setSelected(false);
                            item.setQuantity(1);
                        }
                        menuItemsAdapter.notifyDataSetChanged();
                    } else if (jsonObject.has("error")) {
                        textViewResponse.setText("Ошибка от сервера: " + jsonObject.getString("error"));
                    } else if (jsonObject.has("error_message")) {
                        textViewResponse.setText(jsonObject.getString("error_message"));
                    } else {
                        textViewResponse.setText("Ответ (создание заказа):\n" + jsonObject.toString(2));
                    }
                } else if (requestMethod.equals("GET") && requestUrl.endsWith("/report")) {
                    // Логика для ответа на запрос отчета
                    JSONObject jsonObject = new JSONObject(result);
                    if (jsonObject.has("error_message")) {
                        textViewResponse.setText(jsonObject.getString("error_message"));
                    } else {
                        textViewResponse.setText(jsonObject.toString(2));
                    }
                } else {
                    // Обработка других или неизвестных ответов
                    try {
                        JSONObject jsonObject = new JSONObject(result);
                        if (jsonObject.has("error_message")) {
                            textViewResponse.setText(jsonObject.getString("error_message"));
                        } else {
                            textViewResponse.setText("Неожиданный ответ:\n" + jsonObject.toString(2));
                        }
                    } catch (JSONException innerEx){
                        textViewResponse.setText("Неожиданный ответ (не JSON):\n" + result);
                    }
                }
            } catch (JSONException e) {
                textViewResponse.setText("Ответ (не JSON/ошибка парсинга):\n" + result);
                Log.e("NetworkTask", "JSONException onPostExecute for " + requestUrl + ": " + e.getMessage() + "\nData: " + result);
            }
        }
    }
}