package com.example.dipl;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Button;
import android.widget.TextView;

public class UserMenuActivity extends AppCompatActivity {

    private TextView textViewWelcomeUser;
    private Button buttonGoToCreateOrder;
    private Button buttonLogout;
    private String username;
    private Button buttonLeaveReview;
    private Button buttonModifyOrder;
    private Button buttonGoToPayment;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_menu);

        textViewWelcomeUser = findViewById(R.id.textViewWelcomeUser);
        buttonGoToCreateOrder = findViewById(R.id.buttonGoToCreateOrder);
        buttonLogout = findViewById(R.id.buttonLogout);
        buttonLeaveReview = findViewById(R.id.buttonLeaveReview);
        buttonModifyOrder = findViewById(R.id.buttonModifyOrder);
        buttonGoToPayment = findViewById(R.id.buttonGoToPayment);
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("USERNAME")) {
            username = intent.getStringExtra("USERNAME");
            textViewWelcomeUser.setText("Добро пожаловать, " + username + "!");
        } else {
            textViewWelcomeUser.setText("Добро пожаловать!");
        }

        buttonGoToCreateOrder.setOnClickListener(v -> {
            Intent createOrderIntent = new Intent(UserMenuActivity.this, MainActivity.class);
            if (username != null) {
                createOrderIntent.putExtra("USERNAME", username);
            }
            startActivity(createOrderIntent);
        });

        buttonLogout.setOnClickListener(v -> {
            Intent logoutIntent = new Intent(UserMenuActivity.this, AuthActivity.class);

            logoutIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(logoutIntent);
            finish();
        });
        buttonLeaveReview.setOnClickListener(v -> {
            Intent reviewIntent = new Intent(UserMenuActivity.this, LeaveReviewActivity.class);

            startActivity(reviewIntent);
        });
        buttonModifyOrder.setOnClickListener(v -> {
            Intent Modifyintent = new Intent(UserMenuActivity.this, SelectOrderToModifyActivity.class);
            startActivity(Modifyintent);
        });
        buttonGoToPayment.setOnClickListener(v -> {
            Intent Paymentintent = new Intent(UserMenuActivity.this, SelectOrderForPaymentActivity.class);
            startActivity(Paymentintent);
        });
    }
}