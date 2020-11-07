package com.fediphoto;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

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
    private final Context context;

    public WorkerPostStatus(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        this.context = context;
    }


    @NonNull
    @Override
    public Result doWork() {
        Data dataInput = getInputData();
        String photoFileName = dataInput.getString(MainActivity.Literals.fileName.name());
        if (Utils.isBlank(photoFileName)) {
            Toast.makeText(context, R.string.photo_file_name_blank, Toast.LENGTH_LONG).show();
            return Result.failure();
        }
        JsonObject params = new JsonObject();
        JsonElement account = Utils.getAccountSelectedFromSettings(context);
        Log.i(TAG, String.format("Selected account from settings: %s", account));
        JsonObject statusConfig = Utils.getStatusSelectedFromSettings(context);
        String instance = Utils.getProperty(account, MainActivity.Literals.instance.name());
        String visibility = Utils.getProperty(statusConfig, MainActivity.Literals.visibility.name());
        String threading = Utils.getProperty(statusConfig, MainActivity.Literals.threading.name());
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
        boolean isDebuggable = (0 != (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE));
        if (isDebuggable) {
            latitude = 19.677821;
            longitude = -155.596364;
            Log.i(TAG, String.format("Debug mode with hard coded lat and long %.3f %.3f", latitude, longitude));
        }
        Log.i(TAG, String.format("Latitude %f longitude %f", latitude, longitude));
        if (latitude != 0) {
            String gpsCoordinatesFormat = Utils.getProperty(statusConfig, MainActivity.Literals.gpsCoordinatesFormat.name());
            if (gpsCoordinatesFormat.split("%").length == 3) {
                sb.append("\n").append(String.format(Locale.US, gpsCoordinatesFormat, latitude, longitude));
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
        params.addProperty(MainActivity.Literals.client_name.name(), context.getString(R.string.fedi_photo_for_android));
        String threadingId = Utils.getProperty(statusConfig, MainActivity.Literals.threadingId.name());
        String threadingDate = Utils.getProperty(statusConfig, MainActivity.Literals.threadingDate.name());
        if (Utils.isNotBlank(threadingId)) {
            if ((MainActivity.Literals.daily.name().equals(threading) && Utils.getDateYyyymmdd().equals(threadingDate))
                    || MainActivity.Literals.always.name().equals(threading)) {
                params.addProperty(MainActivity.Literals.in_reply_to_id.name(), threadingId);
                Log.i(TAG, String.format("%s threading ID: %s set to in_reply_to_id.", threading, threadingId));
            }
        }
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
            String token = Utils.getProperty(account, MainActivity.Literals.access_token.name());
            String authorization = String.format("Bearer %s", token);
            urlConnection.setRequestProperty("Authorization", authorization);
            String json = params.toString();
            Log.i(TAG, String.format("Posting JSON: %s", json));
       //     urlConnection.setRequestProperty("Content-length", Integer.toString(json.length()));
            outputStream = urlConnection.getOutputStream();
            outputStream.write(json.getBytes());
            outputStream.flush();
            int responseCode = urlConnection.getResponseCode();
            Log.i(TAG, String.format("Response code: %d\n", responseCode));
            urlConnection.setInstanceFollowRedirects(true);
            inputStream = urlConnection.getInputStream();
            InputStreamReader isr = new InputStreamReader(inputStream);
            Gson gson = new Gson();
            JsonObject jsonObjectFromPost = gson.fromJson(isr, JsonObject.class);
            Utils.log(TAG, String.format("Output: %s", jsonObjectFromPost.toString()));
            String urlForPost = Utils.getProperty(jsonObjectFromPost, MainActivity.Literals.url.name());
            Data dataOutput = new Data.Builder()
                    .putString(MainActivity.Literals.url.name(), urlForPost)
                    .build();
            sendNotification(context.getString(R.string.post_success), urlForPost, photoFileName);
            mediaActionAfterPost(dataInput);
            threadingMaintenanceAfterPost(jsonObjectFromPost);
            return Result.success(dataOutput);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.retry();
        } finally {
            Utils.close(inputStream, outputStream);
        }
    }

    private void threadingMaintenanceAfterPost(JsonObject jsonObjectFromPost) {
        JsonObject statusConfig = Utils.getStatusSelectedFromSettings(context);
        String threading = Utils.getProperty(statusConfig, MainActivity.Literals.threading.name());
        String id = Utils.getProperty(jsonObjectFromPost, MainActivity.Literals.id.name());
        Log.i(TAG, String.format("Threading: %s ID of post: %s", threading, id));
        String threadingId = Utils.getProperty(statusConfig, MainActivity.Literals.threadingId.name());
        String threadingDate = Utils.getProperty(statusConfig, MainActivity.Literals.threadingDate.name());
        if (threading.equals(MainActivity.Literals.always.name())) {
            if (Utils.isBlank(threadingId)) {
                statusConfig.addProperty(MainActivity.Literals.threadingId.name(), id);
                statusConfig.addProperty(MainActivity.Literals.threadingDate.name(), Utils.getDateYyyymmdd());
            }
        }
        if (threading.equals(MainActivity.Literals.daily.name())) {
            if (Utils.isBlank(threadingId)) {
                statusConfig.addProperty(MainActivity.Literals.threadingId.name(), id);
                statusConfig.addProperty(MainActivity.Literals.threadingDate.name(), Utils.getDateYyyymmdd());
            } else {
                if (!Utils.getDateYyyymmdd().equals(threadingDate)) {
                    statusConfig.addProperty(MainActivity.Literals.threadingId.name(), id);
                    statusConfig.addProperty(MainActivity.Literals.threadingDate.name(), Utils.getDateYyyymmdd());
                }
            }
        }
        if (threading.equals(MainActivity.Literals.never.name())) {
            statusConfig.remove(MainActivity.Literals.threadingId.name());
            statusConfig.remove(MainActivity.Literals.threadingDate.name());
        }
        Utils.saveStatusSelectedToSettings(context, statusConfig);
    }

    private void mediaActionAfterPost(Data data) {
        String fileName = data.getString(MainActivity.Literals.fileName.name());
        if (Utils.isBlank(fileName)) {
            Log.i(TAG, "File name if blank. No action after post taken.");
            return;
        }
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
        String appNameFolder = Utils.getApplicationName(context).replaceAll(" ", "_");
        File pictureFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File finalFolder = new File(pictureFolder, appNameFolder);
        finalFolder.mkdirs();
        return new File(String.format("%s/%s", finalFolder.getAbsolutePath(), file.getName()));
    }

    private void sendNotification(String title, String urlString, String photoFileName) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(urlString));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);

        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("default", "Default", NotificationManager.IMPORTANCE_DEFAULT);
            Objects.requireNonNull(notificationManager).createNotificationChannel(channel);
        }

        NotificationCompat.Builder notification = new NotificationCompat.Builder(getApplicationContext(), "default")
                .setContentTitle(title)
                .setContentText(urlString)
                .setContentIntent(pendingIntent)
                .setSmallIcon(R.drawable.fediphoto_foreground)
                .setLargeIcon(BitmapFactory.decodeFile(photoFileName))
                .setAutoCancel(true);
        Random random = new Random();
        int id = random.nextInt(9999 - 1000) + 1000;
        Objects.requireNonNull(notificationManager).notify(id, notification.build());
    }

}
