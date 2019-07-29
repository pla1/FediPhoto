package com.fediphoto;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.MainThread;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class AccountActivity extends AppCompatActivity {
    private final Context context = this;
    private final String TAG = this.getClass().getCanonicalName();
    private JsonElement account;
    private RadioButton radioVisibilityDirect;
    private RadioButton radioVisibilityUnlisted;
    private RadioButton radioVisibilityFollowers;
    private RadioButton radioVisibilityPublic;
    private EditText editTextText;
    private EditText editTextDateFormat;
    private EditText editTextGpsCoordinatesFormat;
    private CheckBox checkBoxActiveAccount;
    private int accountIndexActive;
    private int accountIndexSelected;
    private JsonObject settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);
        settings = Utils.getSettings(context);
        account = Utils.getAccountFromSettings(context);
        TextView textViewInstance = findViewById(R.id.textViewInstance);
        textViewInstance.setText(Utils.getProperty(account, MainActivity.Literals.instance.name()));
        TextView textViewUserUrl = findViewById(R.id.textViewUserUrl);
        textViewUserUrl.setText(Utils.getProperty(account, MainActivity.Literals.me.name()));
        editTextText = findViewById(R.id.editTextText);
        editTextText.setText(Utils.getProperty(account, MainActivity.Literals.text.name()));
        radioVisibilityDirect = findViewById(R.id.radioVisibilityDirect);
        radioVisibilityUnlisted = findViewById(R.id.radioVisibilityUnlisted);
        radioVisibilityFollowers = findViewById(R.id.radioVisibilityFollowers);
        radioVisibilityPublic = findViewById(R.id.radioVisibilityPublic);
        checkBoxActiveAccount = findViewById(R.id.checkBoxAccountActive);
        accountIndexActive = Utils.getInt(Utils.getProperty(settings, MainActivity.Literals.accountIndexActive.name()));
        accountIndexSelected = Utils.getInt(Utils.getProperty(settings, MainActivity.Literals.accountIndexSelected.name()));
        checkBoxActiveAccount.setChecked(accountIndexActive == accountIndexSelected);
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
        editTextDateFormat = findViewById(R.id.editTextDateFormat);
        editTextDateFormat.setText(Utils.getProperty(account, MainActivity.Literals.dateFormat.name()));
        editTextGpsCoordinatesFormat = findViewById(R.id.editTextGpsCoordinatesFormat);
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
    }

    private void save() {
        if (account == null) {
            return;
        }
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
        if (checkBoxActiveAccount.isChecked()) {
            settings.addProperty(MainActivity.Literals.accountIndexActive.name(), accountIndexSelected);
        }
        JsonArray accounts = settings.getAsJsonArray(MainActivity.Literals.accounts.name());
        if (accounts == null || accounts.isJsonNull() || accounts.size() == 0) {
            accounts = new JsonArray();
            accounts.add(accountJsonObject);
            settings.add(MainActivity.Literals.accounts.name(), accounts);
        } else {
            Log.i(TAG, String.format("Save account at selected index: %d", accountIndexSelected));
            accounts.set(accountIndexSelected, accountJsonObject);
        }
        Utils.writeSettings(context, settings);
        finish();

    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        save();
    }

    @Override
    protected void onPause() {
        super.onPause();
        save();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu_account, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent = new Intent(context, MainActivity.class);
        switch (item.getItemId()) {
            case R.id.add_account:
                Log.i(TAG, "Add account.");
                setResult(MainActivity.REQUEST_ACCOUNT_RETURN, intent);
                finish();
                return true;
            case R.id.remove_account:
                Log.i(TAG, "Remove account.");
                JsonArray accounts = settings.getAsJsonArray(MainActivity.Literals.accounts.name());
                if (accounts == null || accounts.isJsonNull() || accounts.size() == 0) {
                    Log.i(TAG, "No account to remove.");
                    Toast.makeText(context, "No account to remove.", Toast.LENGTH_LONG).show();
                } else {
                    accounts.remove(accountIndexSelected);
                    settings.add(MainActivity.Literals.accounts.name(), accounts);
                    settings.addProperty(MainActivity.Literals.accountIndexActive.name(), 0);
                    settings.addProperty(MainActivity.Literals.accountIndexSelected.name(), 0);
                    Utils.writeSettings(context, settings);
                    account = null;
                }
                setResult(MainActivity.RESULT_OK, intent);
                finish();
                return true;
            default:
                Log.i(TAG, "Default menu option.");
                return super.onContextItemSelected(item);

        }

    }
}
