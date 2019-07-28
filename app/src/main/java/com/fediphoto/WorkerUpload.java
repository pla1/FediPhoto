package com.fediphoto;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

import javax.net.ssl.HttpsURLConnection;

public class WorkerUpload extends Worker {
    private final String TAG = this.getClass().getCanonicalName();
    private Context context;

    public WorkerUpload(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        this.context = context;
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.i(TAG, String.format("WorkerUpload test started %s", new Date()));
        Data data = getInputData();
        String fileName = data.getString(MainActivity.Literals.fileName.name());
        File file = new File(fileName);
        Log.i(TAG, String.format("File name %s file exists %s", fileName, file.exists()));
        JsonObject params = new JsonObject();
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
        JsonArray jsonArray = settings.getAsJsonArray(MainActivity.Literals.accounts.name());
        String instance = Utils.getProperty(jsonArray.get(0), MainActivity.Literals.instance.name());
        String token = Utils.getProperty(jsonArray.get(0), MainActivity.Literals.access_token.name());
        String boundary = new BigInteger(256, new Random()).toString();
        String urlString = String.format("https://%s/api/v1/media", instance);
        Log.i(TAG, "URL " + urlString);
        HttpsURLConnection urlConnection;
        InputStream inputStream = null;
        OutputStream outputStream = null;
        PrintWriter writer = null;

        try {
            URL url = new URL(urlString);
            urlConnection = (HttpsURLConnection) url.openConnection();
            urlConnection.setRequestProperty("Cache-Control", "no-cache");
            urlConnection.setRequestProperty("User-Agent", "FediPhoto");
            urlConnection.setUseCaches(false);
            urlConnection.setRequestMethod(MainActivity.Literals.POST.name());
            urlConnection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
            urlConnection.setDoOutput(true);
            outputStream = urlConnection.getOutputStream();
            writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), true);

            writer.append("--").append(boundary).append(Utils.LINE_FEED);
            writer.append("Content-Disposition: form-data; name=\"access_token\"").append(Utils.LINE_FEED);
            writer.append("Content-Type: text/plain; charset=UTF-8").append(Utils.LINE_FEED);
            writer.append(Utils.LINE_FEED);
            writer.append(token).append(Utils.LINE_FEED);
            writer.flush();

            writer.append("--").append(boundary).append(Utils.LINE_FEED);
            writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"");
            writer.append(file.getName()).append("\"").append(Utils.LINE_FEED);
            writer.append("Content-Type: image/jpeg").append(Utils.LINE_FEED);
            writer.append("Content-Transfer-Encoding: binary").append(Utils.LINE_FEED);
            writer.append(Utils.LINE_FEED);
            writer.flush();

            inputStream = new FileInputStream(file);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
            inputStream.close();

            writer.append(Utils.LINE_FEED);
            writer.flush();


            writer.append(Utils.LINE_FEED).flush();
            writer.append("--").append(boundary).append("--").append(Utils.LINE_FEED);
            writer.close();

            outputStream.flush();

            int responseCode = urlConnection.getResponseCode();
            String responseCodeMessage = String.format(Locale.US, "Response code: %d\n", responseCode);
            Log.i(TAG, responseCodeMessage);
            urlConnection.setInstanceFollowRedirects(true);
            inputStream = urlConnection.getInputStream();
            InputStreamReader isr = new InputStreamReader(inputStream);
            JsonParser jsonParser = new JsonParser();
            BufferedReader reader = new BufferedReader(isr);

            String line;
            StringBuilder responseBody = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                responseBody.append(line);
            }
            reader.close();
            urlConnection.disconnect();
            JsonElement jsonElement = jsonParser.parse(responseBody.toString());
            Log.i(TAG, String.format("Output from upload: %s", jsonElement));
            Data outputData = new Data.Builder()
                    .putString(MainActivity.Literals.id.name(), Utils.getProperty(jsonElement, MainActivity.Literals.id.name()))
                    .build();
            return Result.success(outputData);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.retry();
        } finally {
            Utils.close(inputStream, outputStream, writer);
        }

    }
}
