package com.fediphoto;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;

public class WorkerPostStatus extends Worker {

    private final String TAG = this.getClass().getCanonicalName();
    private Context context;

    public WorkerPostStatus(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        this.context = context;
    }


    @NonNull
    @Override
    public Result doWork() {
        Data data = getInputData();
        JsonObject params = new JsonObject();
        JsonElement account= Utils.getAccountFromSettings(context);
        String instance = Utils.getProperty(account, MainActivity.Literals.instance.name());
        String visibility = Utils.getProperty(account, MainActivity.Literals.visibility.name());
        StringBuilder status = new StringBuilder();
        status.append(Utils.getProperty(account, MainActivity.Literals.text.name()));
        double latitude = data.getDouble(MainActivity.Literals.latitude.name(), 0);
        double longitude = data.getDouble(MainActivity.Literals.longitude.name(), 0);
        if (latitude != 0) {
            String gpsCoordinatesFormat = Utils.getProperty(account, MainActivity.Literals.gpsCoordinatesFormat.name());
            if (gpsCoordinatesFormat.split("%s").length == 2) {
                status.append("\n").append(String.format(gpsCoordinatesFormat, latitude, longitude));
            } else {
                status.append("\n").append(latitude).append(",").append(longitude);
            }
        }
        String dateFormat = Utils.getProperty(account, MainActivity.Literals.dateFormat.name());
        if (Utils.isNotBlank(dateFormat)) {
            long milliseconds = data.getLong(MainActivity.Literals.milliseconds.name(), 0);
            if (milliseconds != 0) {
                String dateDisplay = new SimpleDateFormat(dateFormat, Locale.US).format(new Date(milliseconds));
                status.append("\n").append(dateDisplay);
            }
        }
        params.addProperty(MainActivity.Literals.status.name(), status.toString());
        params.addProperty(MainActivity.Literals.access_token.name(), Utils.getProperty(account, MainActivity.Literals.access_token.name()));
        params.addProperty(MainActivity.Literals.visibility.name(), visibility);

        JsonArray mediaJsonArray = new JsonArray();
        mediaJsonArray.add(data.getString(MainActivity.Literals.id.name()));
        params.add(MainActivity.Literals.media_ids.name(), mediaJsonArray);
        params.addProperty(MainActivity.Literals.client_name.name(), "FediPhoto for Android ");
        String urlString = String.format("https://%s/api/v1/statuses", instance);
        Log.i(TAG, "URL " + urlString);
        HttpsURLConnection urlConnection;
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            URL url = new URL(urlString);
            urlConnection = (HttpsURLConnection) url.openConnection();
            urlConnection.setRequestProperty("Cache-Control", "no-cache");
            urlConnection.setRequestProperty("Accept", "application/json");
            urlConnection.setRequestProperty("User-Agent", "FediPhoto");
            urlConnection.setUseCaches(false);
            urlConnection.setRequestMethod(MainActivity.Literals.POST.name());
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
            JsonObject jsonObject = gson.fromJson(isr, JsonObject.class);
            Log.i(TAG, String.format("Output: %s", jsonObject.toString()));
            Data outputData = new Data.Builder()
                    .putString(MainActivity.Literals.url.name(), Utils.getProperty(jsonObject, MainActivity.Literals.url.name()))
                    .build();
            return Result.success(outputData);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.retry();
        } finally {
            Utils.close(inputStream, outputStream);
        }
    }

}
