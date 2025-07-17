package com.example.dipl; 

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

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

public class AuthActivity extends AppCompatActivity {


    public static final String BASE_URL = "http://10.0.2.2:5000/api";

    private EditText editTextUsername, editTextPassword;
    private Button buttonLogin, buttonRegister;
    private TextView textViewResponse;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        editTextUsername = findViewById(R.id.editTextAuthUsername1);
        editTextPassword = findViewById(R.id.editTextAuthPassword2);
        buttonLogin = findViewById(R.id.buttonLogin3);
        buttonRegister = findViewById(R.id.buttonRegister4);
        textViewResponse = findViewById(R.id.textViewAuthResponse5);

        buttonLogin.setOnClickListener(v -> attemptAuth(false)); // false для логина
        buttonRegister.setOnClickListener(v -> attemptAuth(true)); // true для регистрации
    }

    private void attemptAuth(boolean isRegister) {
        String username = editTextUsername.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            textViewResponse.setText("Имя пользователя и пароль не могут быть пустыми.");
            return;
        }
        if (isRegister && password.length() < 4) {
            textViewResponse.setText("Пароль должен быть не менее 4 символов для регистрации.");
            return;
        }


        JSONObject credentials = new JSONObject();
        try {
            credentials.put("username", username);
            credentials.put("password", password);
        } catch (JSONException e) {
            Log.e("AuthActivity", "JSONException: " + e.getMessage());
            textViewResponse.setText("Ошибка создания JSON.");
            return;
        }

        String url = isRegister ? BASE_URL + "/register" : BASE_URL + "/login";
        new AuthTask(isRegister, username).execute(url, credentials.toString());
    }

    private class AuthTask extends AsyncTask<String, Void, String> {
        private boolean isRegisterAttempt;
        private String attemptedUsername; // Чтобы передать имя пользователя в MainActivity

        AuthTask(boolean isRegister, String username) {
            this.isRegisterAttempt = isRegister;
            this.attemptedUsername = username;
        }

        @Override
        protected String doInBackground(String... params) {
            String urlString = params[0];
            String jsonData = params[1];
            HttpURLConnection urlConnection = null;
            String responseString = null;

            try {
                URL url = new URL(urlString);
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("POST");
                urlConnection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                urlConnection.setRequestProperty("Accept", "application/json");
                urlConnection.setConnectTimeout(15000);
                urlConnection.setReadTimeout(10000);
                urlConnection.setDoOutput(true);

                OutputStream os = urlConnection.getOutputStream();
                byte[] input = jsonData.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
                os.close();

                int responseCode = urlConnection.getResponseCode();
                InputStream inputStream = (responseCode >= 200 && responseCode < 300) ?
                        urlConnection.getInputStream() : urlConnection.getErrorStream();

                if (inputStream != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();
                    responseString = sb.toString();
                } else {
                    responseString = "{\"error\":\"Нет ответа от сервера, код: " + responseCode + "\"}";
                }
                Log.d("AuthTask", "Response (" + responseCode + "): " + responseString);

            } catch (IOException e) {
                Log.e("AuthTask", "IOException: " + e.getMessage());
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
            super.onPostExecute(result);
            textViewResponse.setText(""); // Очищаем предыдущие ошибки/сообщения

            if (result == null) {
                textViewResponse.setText("Ошибка: нет ответа от сервера.");
                return;
            }

            try {
                JSONObject jsonResponse = new JSONObject(result);
                if (jsonResponse.has("message")) { // Успешный ответ
                    String message = jsonResponse.getString("message");
                    Toast.makeText(AuthActivity.this, message, Toast.LENGTH_LONG).show();


                    Intent intent = new Intent(AuthActivity.this, UserMenuActivity.class);
                    intent.putExtra("USERNAME", attemptedUsername); // Передаем имя пользователя
                    startActivity(intent);
                    finish(); // Закрываем AuthActivity

                } else if (jsonResponse.has("error")) {
                    String error = jsonResponse.getString("error");
                    textViewResponse.setText("Ошибка: " + error);
                } else {
                    textViewResponse.setText("Неизвестный ответ от сервера.");
                }
            } catch (JSONException e) {
                Log.e("AuthTask", "JSONException onPostExecute: " + e.getMessage() + "\nData: " + result);
                textViewResponse.setText("Ошибка парсинга ответа: " + result);
            }
        }
    }
}