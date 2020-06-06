package com.fediphoto;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;


public class AccountActivity extends AppCompatActivity {
    private final Context context = this;
    private final String TAG = this.getClass().getCanonicalName();
    private JsonElement account;
    private CheckBox checkBoxActiveAccount;
    private int accountIndexSelected;
    private JsonObject settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);
        settings = Utils.getSettings(context);
        account = Utils.getAccountSelectedFromSettings(context);
        TextView textViewInstance = findViewById(R.id.textViewInstance);
        textViewInstance.setText(Utils.getProperty(account, MainActivity.Literals.instance.name()));
        TextView textViewUserUrl = findViewById(R.id.textViewUserUrl);
        textViewUserUrl.setText(Utils.getProperty(account, MainActivity.Literals.me.name()));
        checkBoxActiveAccount = findViewById(R.id.checkBoxAccountActive);
        int accountIndexActive = Utils.getInt(Utils.getProperty(settings, MainActivity.Literals.accountIndexActive.name()));
        accountIndexSelected = Utils.getInt(Utils.getProperty(settings, MainActivity.Literals.accountIndexSelected.name()));
        checkBoxActiveAccount.setChecked(accountIndexActive == accountIndexSelected);
    }

    private void save() {
        if (account == null) {
            return;
        }
        if (checkBoxActiveAccount.isChecked()) {
            settings.addProperty(MainActivity.Literals.accountIndexActive.name(), accountIndexSelected);
        }
        Utils.writeSettings(context, settings);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "onStop");
        save();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
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
                    Log.i(TAG, getString(R.string.no_account_to_remove));
                    Toast.makeText(context, R.string.no_account_to_remove, Toast.LENGTH_LONG).show();
                    return true;
                }
                accounts.remove(accountIndexSelected);
                if (accounts.size() > 0) {
                    settings.add(MainActivity.Literals.accounts.name(), accounts);
                    settings.addProperty(MainActivity.Literals.accountIndexActive.name(), 0);
                    settings.addProperty(MainActivity.Literals.accountIndexSelected.name(), 0);
                    Utils.writeSettings(context, settings);
                    account = null;
                    Toast.makeText(context, R.string.account_removed_account_0_is_active, Toast.LENGTH_LONG).show();
                } else {
                    settings.remove(MainActivity.Literals.accounts.name());
                    settings.remove(MainActivity.Literals.accountIndexActive.name());
                    settings.remove(MainActivity.Literals.accountIndexSelected.name());
                    Utils.writeSettings(context, settings);
                    setResult(MainActivity.REQUEST_ACCOUNT_RETURN, intent);
                    finish();
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
