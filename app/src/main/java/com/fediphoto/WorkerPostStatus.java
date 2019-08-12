package com.fediphoto;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.SyncStateContract;
import android.util.Log;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.exifinterface.media.ExifInterface;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

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
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

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
        Data dataInput = getInputData();
        String photoFileName = dataInput.getString(MainActivity.Literals.fileName.name());
        JsonObject params = new JsonObject();
        JsonElement account = Utils.getAccountSelectedFromSettings(context);
        JsonObject statusConfig = Utils.getStatusSelectedFromSettings(context);
        String instance = Utils.getProperty(account, MainActivity.Literals.instance.name());
        String visibility = Utils.getProperty(statusConfig, MainActivity.Literals.visibility.name());
        StringBuilder sb = new StringBuilder();
        sb.append(Utils.getProperty(statusConfig, MainActivity.Literals.text.name()));
        File file = new File(photoFileName);
        if (!file.exists()) {
            Log.i(TAG, String.format("Photo file %s does not exist.", photoFileName));
            return Result.failure();
        }
        long photoFileLastModified = file.lastModified();
        double[] latLong;
        double latitude = 0;
        double longitude = 0;
        try {
            ExifInterface exifInterface = new ExifInterface(file.getAbsolutePath());
            latLong = exifInterface.getLatLong();
            if (latLong != null) {
                latitude = latLong[0];
                longitude = latLong[1];
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.i(TAG, String.format("Latitude %f longitude %f", latitude, longitude));
        if (latitude != 0) {
            String gpsCoordinatesFormat = Utils.getProperty(statusConfig, MainActivity.Literals.gpsCoordinatesFormat.name());
            if (gpsCoordinatesFormat.split("%s").length == 2) {
                sb.append("\n").append(String.format(gpsCoordinatesFormat, latitude, longitude));
            } else {
                sb.append("\n").append(latitude).append(",").append(longitude);
            }
        }
        String dateFormat = Utils.getProperty(statusConfig, MainActivity.Literals.dateFormat.name());
        if (Utils.isNotBlank(dateFormat)) {
            if (photoFileLastModified != 0) {
                String dateDisplay = new SimpleDateFormat(dateFormat, Locale.US).format(new Date(photoFileLastModified));
                sb.append("\n").append(dateDisplay);
            }
        }
        params.addProperty(MainActivity.Literals.status.name(), sb.toString());
        params.addProperty(MainActivity.Literals.access_token.name(), Utils.getProperty(account, MainActivity.Literals.access_token.name()));
        params.addProperty(MainActivity.Literals.visibility.name(), visibility);

        JsonArray mediaJsonArray = new JsonArray();
        mediaJsonArray.add(dataInput.getString(MainActivity.Literals.id.name()));
        params.add(MainActivity.Literals.media_ids.name(), mediaJsonArray);
        params.addProperty(MainActivity.Literals.client_name.name(), "Fedi Photo for Android ");
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
            String urlForPost = Utils.getProperty(jsonObject, MainActivity.Literals.url.name());
            Data dataOutput = new Data.Builder()
                    .putString(MainActivity.Literals.url.name(), urlForPost)
                    .build();
            sendNotification("Post success", urlForPost, photoFileName);
            actionAfterPost(dataInput);
            return Result.success(dataOutput);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.retry();
        } finally {
            Utils.close(inputStream, outputStream);
        }
    }

    private void actionAfterPost(Data data) {
        String fileName = data.getString(MainActivity.Literals.fileName.name());
        File file = new File(fileName);
        if (!file.exists()) {
            Log.i(TAG, String.format("File %s not found. No action after post taken.", fileName));
            return;
        }
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String actionAfterPost = sharedPreferences.getString("actionAfterPost", "leave");
        Log.i(TAG, String.format("Action after post \"%s\" on file %s", actionAfterPost, fileName));
        if (MainActivity.Literals.delete.name().equals(actionAfterPost)) {
            boolean fileDeleted = file.delete();
            Log.i(TAG, String.format("File %s deleted %s", fileName, fileDeleted));
        }
        if (MainActivity.Literals.copy.name().equals(actionAfterPost)) {
            File fileNew = getNewFile(file);
            Utils.copyFile(file, fileNew);
            Log.i(TAG, String.format("File %s copied to %s.", fileName, fileNew.getAbsoluteFile()));
        }
        if (MainActivity.Literals.move.name().equals(actionAfterPost)) {
            File fileNew = getNewFile(file);
            Utils.copyFile(file, fileNew);
            boolean fileDeleted = file.delete();
            Log.i(TAG, String.format("File %s copied to %s and deleted %s.", fileName, fileNew.getAbsolutePath(), fileDeleted));
        }
        if (MainActivity.Literals.leave.name().equals(actionAfterPost)) {
            Log.i(TAG, String.format("File %s left in place.", fileName));
        }
    }

    private File getNewFile(File file) {
        String appNameFolder = Utils.getApplicationName(context).replaceAll(" ","_");
        File pictureFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File finalFolder = new File(pictureFolder, appNameFolder);
        finalFolder.mkdirs();
        File fileNew = new File(String.format("%s/%s", finalFolder.getAbsolutePath(), file.getName()));
        return fileNew;
    }

    private void sendNotification(String title, String urlString, String photoFileName) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(urlString));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);

        NotificationManager notificationManager = (NotificationManager)getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("default", "Default", NotificationManager.IMPORTANCE_DEFAULT);
            Objects.requireNonNull(notificationManager).createNotificationChannel(channel);
        }

        NotificationCompat.Builder notification = new NotificationCompat.Builder(getApplicationContext(), "default")
                .setContentTitle(title)
                .setContentText(urlString)
                .setContentIntent(pendingIntent)
                .setSmallIcon(R.drawable.notification_icon)
                .setLargeIcon(BitmapFactory.decodeFile(photoFileName))
                .setAutoCancel(true);
        Random random = new Random();
        int id = random.nextInt(9999 - 1000) + 1000;
        Objects.requireNonNull(notificationManager).notify(id, notification.build());
    }

}
