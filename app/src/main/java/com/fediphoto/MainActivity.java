package com.fediphoto;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import androidx.exifinterface.media.ExifInterface;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.lifecycle.Observer;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;


public class MainActivity extends AppCompatActivity {
    private final int CAMERA_REQUEST = 169;
    private final int TOKEN_REQUEST = 269;
    private final int REQUEST_PERMISSION_CAMERA = 369;
    private final int REQUEST_PERMISSION_STORAGE = 469;
    private final int REQUEST_ACCOUNT = 569;
    private final int REQUEST_STATUS = 769;
    public static final int REQUEST_ACCOUNT_RETURN = 669;
    public static final String CHECKMARK = "✔";
    private final String TAG = this.getClass().getCanonicalName();
    private final Context context = this;
    private final Activity activity = this;
    private JsonObject createAppResults = new JsonObject();
    private String token;
    private String instance;
    private String photoFileName;

    private void dispatchTakePictureIntent() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            try {
                File file = createPhotoFile();
                Uri photoUri = FileProvider.getUriForFile(context, "com.fediphoto.fileprovider", file);
                //Uri photoUri = Uri.fromFile(file);
                Log.i(TAG, String.format("photo URI: %s", photoUri.toString()));
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                startActivityForResult(intent, CAMERA_REQUEST);
            } catch (IOException e) {
                Toast.makeText(context, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                e.printStackTrace();
            }
        } else {
            Log.i(TAG, "Camera activity missing.");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button buttonCamera = findViewById(R.id.button_camera);
        JsonObject account = Utils.getAccountSelectedFromSettings(context);
        JsonObject status = Utils.getStatusSelectedFromSettings(context);
        if (account != null && status != null && Utils.getAccountActiveFromSettings(context).get(Literals.me.name()) != null) {
            String buttonCameraText = String.format("Press for camera\n%s\n%s",
                    Utils.getAccountActiveFromSettings(context).get(Literals.me.name()).getAsString(),
                    Utils.getStatusActiveFromSettings(context).get(Literals.label.name()).getAsString());
            buttonCamera.setText(buttonCameraText);
        }
        buttonCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (Utils.getAccountQuantity(context) == 0) {
                    askForInstance();
                    return;
                }
                dispatchTakePictureIntent();
            }
        });
        checkPermissionCamera();
        checkPermissionStorage();
        JsonObject settings = Utils.getSettings(context);
        boolean cameraOnStart = false;
        if (!Utils.isJsonObject(settings)
                || settings.getAsJsonArray(Literals.accounts.name()) == null
                || settings.getAsJsonArray(Literals.accounts.name()).size() == 0) {
            askForInstance();
        } else {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            cameraOnStart = sharedPreferences.getBoolean(getString(R.string.camera_on_start), false);
        }
        Log.i(TAG, String.format("Camera on start setting: %s", cameraOnStart));
        if (cameraOnStart) {
            dispatchTakePictureIntent();
        }
        workerStatus(Literals.worker_tag_media_upload.name());
        workerStatus(Literals.worker_tag_post_status.name());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PERMISSION_CAMERA: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(context, "Permission to camera granted. Take a photo.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(context, "Permission to camera denied.", Toast.LENGTH_LONG).show();
                }
                return;
            }
            case REQUEST_PERMISSION_STORAGE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(context, "Permission to write to external storage granted. Take a photo.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(context, "Permission to write to external storage denied.", Toast.LENGTH_LONG).show();
                }
                return;
            }
        }
    }

    private void checkPermissionCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity,
                    Manifest.permission.CAMERA)) {
                Toast.makeText(context, "Need permission to the camera.", Toast.LENGTH_LONG).show();
            } else {
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.CAMERA},
                        REQUEST_PERMISSION_CAMERA);
            }
        } else {
            Log.i(TAG, "Camera permission already granted.");
        }
    }

    private void checkPermissionStorage() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                Toast.makeText(context, "Need permission to write to external storage.", Toast.LENGTH_LONG).show();
            } else {
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_PERMISSION_STORAGE);
            }
        } else {
            Log.i(TAG, "External storage permission already granted.");
        }
    }

    private File createPhotoFile() throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_", Locale.US).format(new Date());
        String fileName = String.format("%s_%s", Utils.getApplicationName(context).replaceAll(" ", "_"), timestamp);
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        //  File storageDir = getDataDir();
        if (storageDir == null || (!storageDir.exists() && !storageDir.mkdir())) {
            Log.w(TAG, "Couldn't create photo folder: " + storageDir.getAbsolutePath());
        }
        File file = File.createTempFile(fileName, ".jpg", storageDir);
        Log.i(TAG, String.format("Photo file: %s", file.getAbsoluteFile()));
        photoFileName = file.getAbsolutePath();
        return file;
    }

    private void submitWorkerUpload() {
        File file = new File(photoFileName);
        Log.i(TAG, String.format("File %s exists %s", file.getAbsoluteFile(), file.exists()));
        @SuppressLint("RestrictedApi")
        Data data = new Data.Builder()
                .put(Literals.fileName.name(), file.getAbsolutePath())
                .build();
        final OneTimeWorkRequest uploadWorkRequest = new OneTimeWorkRequest
                .Builder(com.fediphoto.WorkerUpload.class)
                .addTag(Literals.worker_tag_media_upload.name())
                .setInputData(data).build();
        WorkContinuation workContinuation = WorkManager.getInstance(context).beginWith(uploadWorkRequest);
        OneTimeWorkRequest postStatusWorkRequest = new OneTimeWorkRequest
                .Builder(com.fediphoto.WorkerPostStatus.class)
                .addTag(Literals.worker_tag_post_status.name())
                .setInputData(data).build();
        workContinuation = workContinuation.then(postStatusWorkRequest);
        workContinuation.enqueue();
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        Log.i(TAG, String.format("Request code %d Result code %d", requestCode, resultCode));
        if (requestCode == CAMERA_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                Log.i(TAG, "Camera request returned OK.");
                submitWorkerUpload();
            } else {
                if (photoFileName != null) {
                    File file = new File(photoFileName);
                    boolean fileDeleted = file.delete();
                    Log.i(TAG, String.format("File %s deleted %s", photoFileName, fileDeleted));
                }
            }
        }
        if (requestCode == TOKEN_REQUEST && resultCode == Activity.RESULT_OK) {
            token = intent.getStringExtra(Literals.token.name());
            Log.i(TAG, String.format("Token \"%s\"", token));
            if (token == null || token.length() < 20) {
                String message = String.format("Token \"%s\" does not look valid. Try again.", token);
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                return;
            }
            WorkerAuthorize worker = new WorkerAuthorize();
            worker.execute(instance);
        }
        if (requestCode == REQUEST_ACCOUNT) {
            if (resultCode == REQUEST_ACCOUNT_RETURN) {
                askForInstance();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    public enum Literals {
        client_name, redirect_uris, scopes, website, access_token, POST, urlString, authorization_code,
        token, client_id, client_secret, redirect_uri, me, exipires_in, created_at, milliseconds,
        grant_type, code, accounts, account, instance, text, followers, visibility, unlisted, PUBLIC, dateFormat,
        OK, Cancel, description, file, media_ids, id, status, url, longitude, latitude, gpsCoordinatesFormat, direct, fileName,
        accountIndexSelected, accountIndexActive, statuses, label, statusIndexActive, statusIndexSelected,
        leave, copy, move, delete, display_name, username, acct, worker_tag_media_upload, worker_tag_post_status
    }


    class WorkerCreateApp extends AsyncTask<String, Void, JsonObject> {
        private String instance;

        @Override
        protected JsonObject doInBackground(String... instance) {
            createAppResults = new JsonObject();
            JsonObject params = new JsonObject();
            this.instance = instance[0];
            params.addProperty(Literals.client_name.name(), "Fedi Photo for Android ");
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

        @Override
        protected JsonObject doInBackground(String... instance) {
            JsonObject params = new JsonObject();
            params.addProperty(Literals.client_id.name(), createAppResults.get(Literals.client_id.name()).getAsString());
            params.addProperty(Literals.client_secret.name(), createAppResults.get(Literals.client_secret.name()).getAsString());
            params.addProperty(Literals.grant_type.name(), Literals.authorization_code.name());
            params.addProperty(Literals.code.name(), token);
            params.addProperty(Literals.redirect_uri.name(), createAppResults.get(Literals.redirect_uri.name()).getAsString());
            String urlString = String.format("https://%s/oauth/token", instance[0]);
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
                Log.i(TAG, String.format("JSON from oauth/token %s", jsonObject.toString()));
                JsonObject settings = Utils.getSettings(context);
                JsonArray accounts = settings.getAsJsonArray(Literals.accounts.name());
                if (accounts == null) {
                    accounts = new JsonArray();
                }
                jsonObject.addProperty(Literals.instance.name(), instance[0]);
                urlString = String.format("https://%s/api/v1/accounts/verify_credentials", instance[0]);
                JsonObject verifyCredentials = getJsonObject(urlString, Utils.getProperty(jsonObject, Literals.access_token.name()));
                if (verifyCredentials != null && Utils.isJsonObject(verifyCredentials)) {
                    jsonObject.addProperty(Literals.me.name(), Utils.getProperty(verifyCredentials, Literals.url.name()));
                    jsonObject.addProperty(Literals.display_name.name(), Utils.getProperty(verifyCredentials, Literals.display_name.name()));
                    jsonObject.addProperty(Literals.username.name(), Utils.getProperty(verifyCredentials, Literals.username.name()));
                    jsonObject.addProperty(Literals.acct.name(), Utils.getProperty(verifyCredentials, Literals.acct.name()));
                }
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
            JsonObject settings = Utils.getSettings(context);
            if (settings.get(Literals.statuses.name()) == null) {
                Toast.makeText(context, "Account created. Setup a status configuration.", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(context, StatusConfigActivity.class);
                startActivity(intent);
            }
        }
    }

    private JsonObject getJsonObject(String urlString, String token) {
        Log.i(TAG, String.format("getJsonObject %s token %s.", urlString, token));
        URL url = Utils.getUrl(urlString);
        HttpsURLConnection urlConnection;
        try {
            urlConnection = (HttpsURLConnection) url.openConnection();
            String authorization = String.format("Bearer %s", token);
            urlConnection.setRequestProperty("Authorization", authorization);
            InputStream is = urlConnection.getInputStream();
            InputStreamReader isr = new InputStreamReader(is);
            Gson gson = new Gson();
            return gson.fromJson(isr, JsonObject.class);
        } catch (IOException e) {
            e.printStackTrace();
            Log.w(TAG, String.format("Error in getJsonObject URL %s ERROR %s.", urlString, e.getLocalizedMessage()));
        }
        return null;
    }


    private void askForInstance() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Enter an instance name. For example: pleroma.site");
        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);
        builder.setPositiveButton(Literals.OK.name(), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                instance = input.getText().toString();
                String message = String.format("Instance \"%s\"", instance);
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                WorkerCreateApp workerCreateApp = new WorkerCreateApp();
                workerCreateApp.execute(instance);
            }
        });
        builder.setNegativeButton(Literals.Cancel.name(), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        builder.show();
    }

    private void multipleChoiceAccount() {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setTitle("Select account...");
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        JsonObject settings = Utils.getSettings(context);
        JsonElement accounts = settings.get(Literals.accounts.name());
        JsonArray jsonArray = accounts.getAsJsonArray();
        int index = 0;
        for (JsonElement jsonElement : jsonArray) {
            String me = Utils.getProperty(jsonElement, Literals.me.name());
            String checkMark = "";
            if (index == Utils.getInt(Utils.getProperty(settings, Literals.accountIndexActive.name()))) {
                checkMark = CHECKMARK;
            }
            adapter.add(String.format(Locale.US, "%s %d %s", checkMark, index++, me));
        }
        alertDialog.setAdapter(adapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                JsonObject settings = Utils.getSettings(context);
                settings.addProperty(Literals.accountIndexSelected.name(), which);
                Utils.writeSettings(context, settings);
                String selectedItem = adapter.getItem(which);
                Log.i(TAG, "Selected instance: " + selectedItem);
                Intent intent = new Intent(context, AccountActivity.class);
                startActivityForResult(intent, REQUEST_ACCOUNT);
            }
        });
        alertDialog.show();

    }

    private void multipleChoiceStatuses() {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setTitle("Select status...");
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        JsonObject settings = Utils.getSettings(context);
        JsonElement statuses = settings.get(Literals.statuses.name());
        JsonArray jsonArray = statuses.getAsJsonArray();
        int index = 0;
        for (JsonElement jsonElement : jsonArray) {
            String label = Utils.getProperty(jsonElement, Literals.label.name());
            String checkmark = "";
            if (index == Utils.getInt(Utils.getProperty(settings, Literals.statusIndexActive.name()))) {
                checkmark = CHECKMARK;
            }
            adapter.add(String.format(Locale.US, "%s %d %s", checkmark, index++, label));
        }
        alertDialog.setAdapter(adapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                JsonObject settings = Utils.getSettings(context);
                settings.addProperty(Literals.statusIndexSelected.name(), which);
                Utils.writeSettings(context, settings);
                String selectedItem = adapter.getItem(which);
                Log.i(TAG, "Selected status: " + selectedItem);
                Intent intent = new Intent(context, StatusConfigActivity.class);
                startActivityForResult(intent, REQUEST_STATUS);
            }
        });
        alertDialog.show();

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        JsonObject settings = Utils.getSettings(context);
        JsonElement accounts = settings.get(Literals.accounts.name());
        JsonElement statuses = settings.get(Literals.statuses.name());
        Intent intent = null;
        switch (item.getItemId()) {
            case R.id.accounts:
                if (accounts == null || accounts.getAsJsonArray().size() == 0) {
                    askForInstance();
                } else {
                    // TODO ask for multiple choice
                    if (accounts.getAsJsonArray().size() > 1) {
                        multipleChoiceAccount();
                    } else {
                        intent = new Intent(context, AccountActivity.class);
                        startActivityForResult(intent, REQUEST_ACCOUNT);
                    }
                }
                Log.i(TAG, "Accounts activity");
                return true;
            case R.id.settings:
                Log.i(TAG, "Settings menu option.");
                intent = new Intent(context, SettingsActivity.class);
                startActivity(intent);
                return true;
            case R.id.status_config:
                if (statuses != null && !statuses.isJsonNull() && statuses.getAsJsonArray().size() > 1) {
                    multipleChoiceStatuses();
                } else {
                    intent = new Intent(context, StatusConfigActivity.class);
                    startActivityForResult(intent, REQUEST_STATUS);
                }
                Log.i(TAG, "Statuses activity");
                return true;
            default:
                Log.i(TAG, "Default menu option.");
                return super.onContextItemSelected(item);
        }
    }

    private void workerStatus(String tag) {
        ListenableFuture<List<WorkInfo>> workInfoList = WorkManager.getInstance(context).getWorkInfosByTag(tag);
        try {
            List<WorkInfo> workInfos = workInfoList.get(5, TimeUnit.SECONDS);
            Log.i(TAG, String.format("%d worker info quantity. Worker Info Tag %s", workInfos.size(), tag));
            for (WorkInfo workInfo : workInfos) {
                Log.i(TAG, String.format("Worker info %s. Worker Info Tag %s", workInfo.toString(), tag));
            }
        } catch (Exception e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
    }

}
