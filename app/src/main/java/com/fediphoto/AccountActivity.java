package com.fediphoto;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class AccountActivity extends Activity {
    private final Context context = this;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);
        JsonParser jsonParser = new JsonParser();
        final JsonElement account = jsonParser.parse(getIntent().getStringExtra(MainActivity.Literals.account.name()));
        TextView textViewInstance = findViewById(R.id.textViewInstance);
        textViewInstance.setText(Utils.getProperty(account, MainActivity.Literals.instance.name()));
        TextView textViewUserUrl = findViewById(R.id.textViewUserUrl);
        textViewUserUrl.setText(Utils.getProperty(account, MainActivity.Literals.me.name()));
        final EditText editTextText = findViewById(R.id.editTextText);
        editTextText.setText(Utils.getProperty(account, MainActivity.Literals.text.name()));
        final RadioButton radioVisibilityDirect = findViewById(R.id.radioVisibilityDirect);
        final RadioButton radioVisibilityUnlisted = findViewById(R.id.radioVisibilityUnlisted);
        final RadioButton radioVisibilityFollowers = findViewById(R.id.radioVisibilityFollowers);
        final RadioButton radioVisibilityPublic = findViewById(R.id.radioVisibilityPublic);
        if (MainActivity.Literals.direct.name().equals(Utils.getProperty(account, MainActivity.Literals.visibility.name()))) {
            radioVisibilityDirect.setChecked(true);
        }
        if (MainActivity.Literals.followers.name().equals(Utils.getProperty(account, MainActivity.Literals.visibility.name()))) {
            radioVisibilityFollowers.setChecked(true);
        }
        if (MainActivity.Literals.unlisted.name().equals(Utils.getProperty(account, MainActivity.Literals.visibility.name()))) {
            radioVisibilityUnlisted.setChecked(true);
        }
        if (MainActivity.Literals.PUBLIC.name().equals(Utils.getProperty(account, MainActivity.Literals.visibility.name()))) {
            radioVisibilityPublic.setChecked(true);
        }
        final EditText editTextDateFormat = findViewById(R.id.editTextDateFormat);
        editTextDateFormat.setText(Utils.getProperty(account, MainActivity.Literals.dateFormat.name()));
        final EditText editTextGpsCoordinatesFormat = findViewById(R.id.editTextGpsCoordinatesFormat);
        editTextGpsCoordinatesFormat.setText(Utils.getProperty(account, MainActivity.Literals.gpsCoordinatesFormat.name()));
        final StringBuilder dateFormat = new StringBuilder();
        editTextDateFormat.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int i, KeyEvent keyEvent) {
                if (!dateFormat.toString().equals(editTextDateFormat.getText().toString())) {
                    SimpleDateFormat sdf = new SimpleDateFormat(editTextDateFormat.getText().toString(), Locale.US);
                    Toast.makeText(context, sdf.format(new Date()), Toast.LENGTH_SHORT).show();
                } else {
                    dateFormat.replace(0, dateFormat.length(), editTextDateFormat.getText().toString());
                }
                return false;
            }
        });
        Button buttonSave = findViewById(R.id.buttonSave);
        Button buttonCancel = findViewById(R.id.buttonCancel);
        buttonCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        buttonSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                JsonObject accountJsonObject = account.getAsJsonObject();
                accountJsonObject.addProperty(MainActivity.Literals.text.name(), editTextText.getText().toString());
                accountJsonObject.addProperty(MainActivity.Literals.dateFormat.name(), editTextDateFormat.getText().toString());
                accountJsonObject.addProperty(MainActivity.Literals.gpsCoordinatesFormat.name(), editTextGpsCoordinatesFormat.getText().toString());
                if (radioVisibilityDirect.isChecked()) {
                    accountJsonObject.addProperty(MainActivity.Literals.visibility.name(), MainActivity.Literals.direct.name());
                }
                if (radioVisibilityFollowers.isChecked()) {
                    accountJsonObject.addProperty(MainActivity.Literals.visibility.name(), MainActivity.Literals.followers.name());
                }
                if (radioVisibilityPublic.isChecked()) {
                    accountJsonObject.addProperty(MainActivity.Literals.visibility.name(), MainActivity.Literals.PUBLIC.name());
                }
                if (radioVisibilityUnlisted.isChecked()) {
                    accountJsonObject.addProperty(MainActivity.Literals.visibility.name(), MainActivity.Literals.unlisted.name());
                }
                // TODO replace the correct array row
                JsonObject settings = new JsonObject();
                JsonArray jsonArray = new JsonArray();
                jsonArray.add(account);
                settings.add(MainActivity.Literals.accounts.name(), jsonArray);
                Utils.writeSettings(context, settings);
                finish();
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }
}
