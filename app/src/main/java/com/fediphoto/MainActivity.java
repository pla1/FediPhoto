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
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.appcompat.app.AppCompatActivity;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

import javax.net.ssl.HttpsURLConnection;


public class MainActivity extends AppCompatActivity {
    private final int CAMERA_REQUEST = 169;
    private final int TOKEN_REQUEST = 269;
    private final int REQUEST_PERMISSION_CAMERA = 369;
    private final int REQUEST_PERMISSION_STORAGE = 469;
    private final String DEFAULT_GPS_COORDINATES_FORMAT = "https://maps.google.com?q=%s,%s";
    private final String DEFAULT_DATE_FORMAT = "EEEE MMMM dd, yyyy hh:mm:ss a z";
    private final String TAG = this.getClass().getCanonicalName();
    private final Context context = this;
    private final Activity activity = this;
    private JsonObject createAppResults = new JsonObject();
    private String token;
    private String instance;
    private String photoFileName;
    private static final String LINE_FEED = "\r\n";

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
        buttonCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
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
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
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
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                Toast.makeText(context, "Need permission to write to external storage.", Toast.LENGTH_LONG).show();
            } else {
                // No explanation needed; request the permission
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_PERMISSION_STORAGE);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        } else {
            Log.i(TAG, "External storage permission already granted.");
        }
    }

    private File createPhotoFile() throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_").format(new Date());
        String fileName = String.format("%s_%s", Utils.getApplicationName(context), timestamp);
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        //  File storageDir = getDataDir();
        if (!storageDir.exists() && !storageDir.mkdir()) {
            Log.w(TAG, "Couldn't create photo folder: " + storageDir.getAbsolutePath());
        }
        File file = File.createTempFile(fileName, ".jpg", storageDir);
        Log.i(TAG, String.format("Photo file: %s", file.getAbsoluteFile()));
        photoFileName = file.getAbsolutePath();
        return file;
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        Log.i(TAG, String.format("Request code %d Result code %d", requestCode, resultCode));
        if (requestCode == CAMERA_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                Log.i(TAG, "Camera request returned OK.");
                File file = new File(photoFileName);
                Log.i(TAG, String.format("File %s exists %s", file.getAbsoluteFile(), file.exists()));
                WorkerUpload worker = new WorkerUpload();
                @SuppressLint("RestrictedApi")
                Data data = new Data.Builder().put(Literals.fileName.name(), file.getAbsolutePath()).build();
                worker.execute(file);
                OneTimeWorkRequest uploadWorkRequest = new OneTimeWorkRequest
                        .Builder(UploadWorker.class)
                        .setInputData(data).build();
                WorkManager.getInstance(context).enqueue(uploadWorkRequest);
            } else {
                File file = new File(photoFileName);
                boolean fileDeleted = file.delete();
                Log.i(TAG, String.format("File %s deleted %s", photoFileName, fileDeleted));
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
        grant_type, code, accounts, account, instance, text, followers, visibility, unlisted, PUBLIC, dateFormat,
        OK, Cancel, description, file, media_ids, id, status, url, longitude, latitude, gpsCoordinatesFormat, direct, fileName
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


    class WorkerPostStatus extends AsyncTask<JsonElement, Void, JsonObject> {
        private String instance;

        @Override
        protected JsonObject doInBackground(JsonElement... jsonElements) {
            JsonObject params = new JsonObject();
            JsonObject settings = Utils.getSettings(context);
            JsonElement mediaJsonElement = jsonElements[0];
            JsonArray settingsJsonArray = settings.getAsJsonArray(Literals.accounts.name());
            instance = Utils.getProperty(settingsJsonArray.get(0), Literals.instance.name());
            token = Utils.getProperty(settingsJsonArray.get(0), Literals.access_token.name());
            String visibility = Utils.getProperty(settingsJsonArray.get(0), Literals.visibility.name());
            StringBuilder status = new StringBuilder();
            status.append(Utils.getProperty(settingsJsonArray.get(0), Literals.text.name()));
            String latitude = Utils.getProperty(mediaJsonElement, Literals.latitude.name());
            String longitude = Utils.getProperty(mediaJsonElement, Literals.longitude.name());
            if (Utils.isNotBlank(latitude)) {
                String gpsCoordinatesFormat = Utils.getProperty(settingsJsonArray.get(0), Literals.gpsCoordinatesFormat.name());
                if (gpsCoordinatesFormat.split("%s").length == 2) {
                    status.append("\n").append(String.format(gpsCoordinatesFormat, latitude, longitude));
                } else {
                    status.append("\n").append(latitude).append(",").append(longitude);
                }
            }
            String dateFormat = Utils.getProperty(settingsJsonArray.get(0), Literals.dateFormat.name());
            if (Utils.isNotBlank(dateFormat)) {
                String dateDisplay = new SimpleDateFormat(dateFormat).format(new Date());
                status.append("\n").append(dateDisplay);
            }
            params.addProperty(Literals.status.name(), status.toString());
            params.addProperty(Literals.access_token.name(), Utils.getProperty(settingsJsonArray.get(0), Literals.access_token.name()));
            params.addProperty(Literals.visibility.name(), visibility);

            JsonArray mediaJsonArray = new JsonArray();
            mediaJsonArray.add(Utils.getProperty(mediaJsonElement, Literals.id.name()));
            params.add(Literals.media_ids.name(), mediaJsonArray);
            params.addProperty(Literals.client_name.name(), "FediPhoto for Android ");
            String urlString = String.format("https://%s/api/v1/statuses", instance);
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
                Log.i(TAG, String.format("Posting JSON: %s", json));
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
            String message = String.format("Status posted: %s\n", jsonObject.get(Literals.url.name()).getAsString());
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            Log.i(TAG, message);
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
                jsonObject.addProperty(Literals.gpsCoordinatesFormat.name(), DEFAULT_GPS_COORDINATES_FORMAT);
                jsonObject.addProperty(Literals.dateFormat.name(), DEFAULT_DATE_FORMAT);
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

    // TODO look at using this https://developer.android.com/topic/libraries/architecture/workmanager/basics.html
    class WorkerUpload extends AsyncTask<File, Void, JsonElement> {
        private File file;

        @Override
        protected JsonElement doInBackground(File... files) {
            JsonElement responseJsonElement = null;
            JsonObject params = new JsonObject();
            this.file = files[0];
            if (file == null) {
                Toast.makeText(context, "File is null.", Toast.LENGTH_LONG).show();
                return null;
            }
            if (!file.exists()) {
                Toast.makeText(context, String.format("File \"%s\" does not exist.", file.getAbsoluteFile()), Toast.LENGTH_LONG).show();
                return null;
            }

            // TODO  get the current settings.
            JsonObject settings = Utils.getSettings(context);
            JsonArray jsonArray = settings.getAsJsonArray(Literals.accounts.name());
            instance = Utils.getProperty(jsonArray.get(0), Literals.instance.name());
            token = Utils.getProperty(jsonArray.get(0), Literals.access_token.name());
            String fileName = file.getName();

            String boundary = new BigInteger(256, new Random()).toString();
            String urlString = String.format("https://%s/api/v1/media", instance);
            Log.i(TAG, "URL " + urlString);
            HttpsURLConnection urlConnection;
            InputStream inputStream = null;
            OutputStream outputStream = null;
            JsonObject jsonObject = null;
            PrintWriter writer = null;

            try {
                URL url = new URL(urlString);
                urlConnection = (HttpsURLConnection) url.openConnection();
                urlConnection.setRequestProperty("Cache-Control", "no-cache");
                urlConnection.setRequestProperty("User-Agent", "FediPhoto");
                urlConnection.setUseCaches(false);
                urlConnection.setRequestMethod(Literals.POST.name());
                urlConnection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
                urlConnection.setDoOutput(true);
                String json = params.toString();
                outputStream = urlConnection.getOutputStream();
                writer = new PrintWriter(new OutputStreamWriter(outputStream, "UTF-8"), true);


                writer.append("--" + boundary).append(LINE_FEED);
                writer.append("Content-Disposition: form-data; name=\"access_token\"").append(LINE_FEED);
                writer.append("Content-Type: text/plain; charset=UTF-8").append(LINE_FEED);
                writer.append(LINE_FEED);
                writer.append(token).append(LINE_FEED);
                writer.flush();


                writer.append("--" + boundary).append(LINE_FEED);
                writer.append("Content-Disposition: form-data; name=\"file\"").append(LINE_FEED);
                writer.append("Content-Type: text/plain; charset=UTF-8").append(LINE_FEED);
                writer.append(LINE_FEED);
                writer.append(fileName).append(LINE_FEED);
                writer.flush();


                writer.append("--" + boundary).append(LINE_FEED);
                writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"").append(LINE_FEED);
                writer.append("Content-Type: image/jpeg").append(LINE_FEED);
                writer.append("Content-Transfer-Encoding: binary").append(LINE_FEED);
                writer.append(LINE_FEED);
                writer.flush();


                inputStream = new FileInputStream(file);
                byte[] buffer = new byte[4096];
                int bytesRead = -1;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    //  Log.i(TAG, new String(buffer));
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
                inputStream.close();

                writer.append(LINE_FEED);
                writer.flush();


                writer.append(LINE_FEED).flush();
                writer.append("--" + boundary + "--").append(LINE_FEED);
                writer.close();

                outputStream.flush();

                int responseCode = urlConnection.getResponseCode();
                String responseCodeMessage = String.format("Response code: %d\n", responseCode);
                //  Toast.makeText(context, responseCodeMessage, Toast.LENGTH_LONG).show();
                Log.i(TAG, responseCodeMessage);
                urlConnection.setInstanceFollowRedirects(true);
                inputStream = urlConnection.getInputStream();
                InputStreamReader isr = new InputStreamReader(inputStream);
                JsonParser jsonParser = new JsonParser();
                BufferedReader reader = new BufferedReader(isr);

                String line = null;
                StringBuilder responseBody = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    responseBody.append(line);
                }
                reader.close();
                urlConnection.disconnect();
                responseJsonElement = jsonParser.parse(responseBody.toString());
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Utils.close(inputStream, outputStream, writer);
            }
            return responseJsonElement;
        }

        @Override
        protected void onPostExecute(JsonElement responseJsonElement) {
            super.onPostExecute(responseJsonElement);
            if (responseJsonElement == null) {
                Log.i(TAG, "Response JsonElement is null");
            } else {
                addCoordinates(file, responseJsonElement);
                Log.i(TAG, String.format("Response: %s", responseJsonElement.toString()));
                WorkerPostStatus worker = new WorkerPostStatus();
                worker.execute(responseJsonElement);
            }
        }
    }

    private JsonElement addCoordinates(File file, JsonElement jsonElement) {
        if (file == null || !file.exists()) {
            return jsonElement;
        }
        try {
            ExifInterface exifInterface = new ExifInterface(file.getAbsolutePath());
            float[] latLong = new float[2];
            if (exifInterface.getLatLong(latLong)) {
                jsonElement.getAsJsonObject().addProperty(Literals.latitude.name(), latLong[0]);
                jsonElement.getAsJsonObject().addProperty(Literals.longitude.name(), latLong[1]);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return jsonElement;
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
