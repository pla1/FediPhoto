package com.fediphoto;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.net.ssl.HttpsURLConnection;

import static android.os.Environment.getExternalStoragePublicDirectory;

public class MainActivity extends AppCompatActivity {
    private final int CAMERA_REQUEST = 1239;
    private final int TOKEN_REQUEST = 2321;
    private final String TAG = this.getClass().getCanonicalName();
    private final Context context = this;
    private JsonObject createAppResults = new JsonObject();
    private String token;
    private String instance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button buttonCamera = findViewById(R.id.button_camera);
        buttonCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");
                try {
                    File file = createPhotoFile();
                    Uri photoUri = FileProvider.getUriForFile(context, "com.fediphoto.fileprovider", file);
                    Log.i(TAG, String.format("photo URI: %s", photoUri.toString()));
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                    startActivityForResult(intent, CAMERA_REQUEST);
                } catch (IOException e) {
                    Toast.makeText(context, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });

    }

    private File createPhotoFile() throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = String.format("%s_%s", Utils.getApplicationName(context) , timestamp);
        File storageDir =getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        if (!storageDir.exists() && !storageDir.mkdir())
            Log.w(TAG, "Couldn't create photo folder: " + storageDir.getAbsolutePath());
        File file = File.createTempFile(fileName, ".jpg", storageDir);
        Log.i(TAG, String.format("Photo file %s", file.getAbsoluteFile()));
        return file;
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CAMERA_REQUEST && resultCode == Activity.RESULT_OK) {
           Log.i(TAG, "Camera request returned.");
        }
        if (requestCode == TOKEN_REQUEST && resultCode == Activity.RESULT_OK) {
            token = data.getStringExtra(Literals.token.name());
            Log.i(TAG, String.format("Token \"%s\"", token));
            if (token == null || token.length() < 20) {
                String message = String.format("Token \"%s\" does not look valid. Try again.", token);
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                return;
            }
            WorkerAuthorize worker = new WorkerAuthorize();
            worker.execute(instance);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    public static enum Literals {
        client_name, redirect_uris, scopes, website, access_token, POST, urlString, authorization_code,
        token, client_id, client_secret, redirect_uri, me, exipires_in, created_at, milliseconds,
        grant_type, code, accounts, account, instance, text, followers, visibility, unlisted, PUBLIC, dateFormat
    }


    class WorkerCreateApp extends AsyncTask<String, Void, JsonObject> {
        private String instance;

        @Override
        protected JsonObject doInBackground(String... instance) {
            createAppResults = new JsonObject();
            JsonObject params = new JsonObject();
            this.instance = instance[0];
            params.addProperty(Literals.client_name.name(), "FediPhoto for Android ");
            params.addProperty(Literals.redirect_uris.name(), "fediphoto://fediphotoreturn");
            params.addProperty(Literals.scopes.name(), "read write follow push");
            params.addProperty(Literals.website.name(), "https://fediphoto.com");
            String urlString = String.format("https://%s/api/v1/apps", instance);
            Log.i(TAG, "URL " + urlString);
            HttpsURLConnection urlConnection;
            InputStream inputStream = null;
            OutputStream outputStream = null;
            JsonObject jsonObject = null;
            try {
                URL url = new URL(urlString);
                urlConnection = (HttpsURLConnection) url.openConnection();
                urlConnection.setRequestProperty("Cache-Control", "no-cache");
                urlConnection.setRequestProperty("Accept", "application/json");
                urlConnection.setRequestProperty("User-Agent", "FediPhoto");
                urlConnection.setUseCaches(false);
                urlConnection.setRequestMethod(Literals.POST.name());
                urlConnection.setRequestProperty("Content-type", "application/json; charset=UTF-8");
                urlConnection.setDoOutput(true);
                String json = params.toString();
                urlConnection.setRequestProperty("Content-length", Integer.toString(json.length()));
                outputStream = urlConnection.getOutputStream();
                outputStream.write(json.getBytes());
                outputStream.flush();
                int responseCode = urlConnection.getResponseCode();
                Log.i(TAG, String.format("Response code: %d\n", responseCode));
                urlConnection.setInstanceFollowRedirects(true);
                inputStream = urlConnection.getInputStream();
                InputStreamReader isr = new InputStreamReader(inputStream);
                Gson gson = new Gson();
                jsonObject = gson.fromJson(isr, JsonObject.class);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Utils.close(inputStream, outputStream);
            }
            return jsonObject;
        }

        @Override
        protected void onPostExecute(JsonObject jsonObject) {
            super.onPostExecute(jsonObject);
            Log.i(TAG, "OUTPUT: " + jsonObject.toString());
            createAppResults = jsonObject;
            String urlString = String.format("https://%s/oauth/authorize?scope=%s&response_type=code&redirect_uri=%s&client_id=%s",
                    instance, Utils.urlEncodeComponent("write read follow push"), Utils.urlEncodeComponent(jsonObject.get("redirect_uri").getAsString()), jsonObject.get("client_id").getAsString());
            Intent intent = new Intent(context, WebviewActivity.class);
            intent.putExtra("urlString", urlString);
            startActivityForResult(intent, TOKEN_REQUEST);

        }
    }


    class WorkerAuthorize extends AsyncTask<String, Void, JsonObject> {
        private String instance;

        @Override
        protected JsonObject doInBackground(String... instance) {
            JsonObject params = new JsonObject();
            this.instance = instance[0];
            params.addProperty(Literals.client_id.name(), createAppResults.get(Literals.client_id.name()).getAsString());
            params.addProperty(Literals.client_secret.name(), createAppResults.get(Literals.client_secret.name()).getAsString());
            params.addProperty(Literals.grant_type.name(), Literals.authorization_code.name());
            params.addProperty(Literals.code.name(), token);
            params.addProperty(Literals.redirect_uri.name(), createAppResults.get(Literals.redirect_uri.name()).getAsString());
            String urlString = String.format("https://%s/oauth/token", instance);
            Log.i(TAG, "URL " + urlString);
            HttpsURLConnection urlConnection;
            InputStream inputStream = null;
            OutputStream outputStream = null;
            JsonObject jsonObject = null;
            try {
                URL url = new URL(urlString);
                urlConnection = (HttpsURLConnection) url.openConnection();
                urlConnection.setRequestProperty("Cache-Control", "no-cache");
                urlConnection.setRequestProperty("Accept", "application/json");
                urlConnection.setRequestProperty("User-Agent", "FediPhoto");
                urlConnection.setUseCaches(false);
                urlConnection.setRequestMethod(Literals.POST.name());
                urlConnection.setRequestProperty("Content-type", "application/json; charset=UTF-8");
                urlConnection.setDoOutput(true);
                String json = params.toString();
                urlConnection.setRequestProperty("Content-length", Integer.toString(json.length()));
                outputStream = urlConnection.getOutputStream();
                outputStream.write(json.getBytes());
                outputStream.flush();
                int responseCode = urlConnection.getResponseCode();
                Log.i(TAG, String.format("Response code: %d\n", responseCode));
                urlConnection.setInstanceFollowRedirects(true);
                inputStream = urlConnection.getInputStream();
                InputStreamReader isr = new InputStreamReader(inputStream);
                Gson gson = new Gson();
                jsonObject = gson.fromJson(isr, JsonObject.class);
                JsonObject settings = Utils.getSettings(context);
                JsonArray accounts = settings.getAsJsonArray(Literals.accounts.name());
                if (accounts == null) {
                    accounts = new JsonArray();
                }
                jsonObject.addProperty(Literals.instance.name(), instance[0]);
                accounts.add(jsonObject);
                settings.add(Literals.accounts.name(), accounts);
                Utils.writeSettings(context, settings);
                Log.i(TAG, String.format("Settings after save:\n%s\n", Utils.getSettings(context).toString()));
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Utils.close(inputStream, outputStream);
            }
            return jsonObject;
        }

        @Override
        protected void onPostExecute(JsonObject jsonObject) {
            super.onPostExecute(jsonObject);
            Log.i(TAG, "OUTPUT: " + jsonObject.toString());
        }
    }



    class WorkerUpload extends AsyncTask<String, Void, JsonObject> {
        private String instance;

        @Override
        protected JsonObject doInBackground(String... instance) {
            JsonObject params = new JsonObject();
            this.instance = instance[0];

            // TODO  get the current settings.
            JsonObject settings = Utils.getSettings(context);
            String urlString = String.format("https://%s/api/v1/media", instance);
            Log.i(TAG, "URL " + urlString);
            HttpsURLConnection urlConnection;
            InputStream inputStream = null;
            OutputStream outputStream = null;
            JsonObject jsonObject = null;
            try {
                URL url = new URL(urlString);
                urlConnection = (HttpsURLConnection) url.openConnection();
                urlConnection.setRequestProperty("Cache-Control", "no-cache");
                urlConnection.setRequestProperty("Accept", "application/json");
                urlConnection.setRequestProperty("User-Agent", "FediPhoto");
                urlConnection.setUseCaches(false);
                urlConnection.setRequestMethod(Literals.POST.name());
                urlConnection.setRequestProperty("Content-type", "application/json; charset=UTF-8");
                urlConnection.setDoOutput(true);
                String json = params.toString();
                urlConnection.setRequestProperty("Content-length", Integer.toString(json.length()));
                outputStream = urlConnection.getOutputStream();
                outputStream.write(json.getBytes());
                outputStream.flush();
                int responseCode = urlConnection.getResponseCode();
                Log.i(TAG, String.format("Response code: %d\n", responseCode));
                urlConnection.setInstanceFollowRedirects(true);
                inputStream = urlConnection.getInputStream();
                InputStreamReader isr = new InputStreamReader(inputStream);
                Gson gson = new Gson();
                jsonObject = gson.fromJson(isr, JsonObject.class);

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Utils.close(inputStream, outputStream);
            }
            return jsonObject;
        }

        @Override
        protected void onPostExecute(JsonObject jsonObject) {
            super.onPostExecute(jsonObject);
            Log.i(TAG, "OUTPUT: " + jsonObject.toString());
        }
    }


    private void askForInstance() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Enter an instance name. For example: pleroma.site");
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                instance = input.getText().toString();
                String message = String.format("Instance \"%s\"", instance);
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                WorkerCreateApp workerCreateApp = new WorkerCreateApp();
                workerCreateApp.execute(instance);
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        builder.show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.accounts:
                JsonObject settings = Utils.getSettings(context);
                JsonElement accounts = settings.get(Literals.accounts.name());
                if (accounts == null || accounts.getAsJsonArray().size() == 0) {
                    askForInstance();
                } else {
                    // TODO ask for multiple choice
                    Intent intent = new Intent(context, AccountActivity.class);
                    intent.putExtra(Literals.account.name(), settings.getAsJsonArray(Literals.accounts.name()).get(0).toString());
                    startActivity(intent);
                }
                Log.i(TAG, "Accounts activity");
                return true;
            case R.id.settings:
                Log.i(TAG, "Settings menu option.");
                Intent intent = new Intent(context, SettingsActivity.class);
                startActivity(intent);

                return true;
            default:
                Log.i(TAG, "Default menu option.");
                return super.onContextItemSelected(item);
        }
    }
}
