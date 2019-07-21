package com.fediphoto;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;


import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class Utils {
    private final static String lineSeparator = System.getProperty("line.separator");
    private final static String TAG = "com.fediphoto.Utils";

    public static void main(String[] args) {

    }

    public static void writeSettings(Context context, JsonObject jsonObject) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileOutputStream openFileOutput = context.openFileOutput("settings.json", Context.MODE_PRIVATE)) {
            openFileOutput.write(gson.toJson(jsonObject).getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void close(Object object) {
        if (object == null) {
            return;
        }
        try {
            if (object instanceof BufferedReader) {
                BufferedReader bufferedReader = (BufferedReader) object;
                bufferedReader.close();
                return;
            }
            Log.i(TAG, "Object not closed. Object type %s has not been defined in the Utils.close method.");
        } catch (Exception e) {

        }
    }

    public static JsonObject getSettings(Context context) {
        StringBuilder sb = new StringBuilder();
        BufferedReader bufferedReader = null;
        try {
            FileInputStream fileInputStream = context.openFileInput("settings.json");
            bufferedReader = new BufferedReader(new InputStreamReader(fileInputStream));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                sb.append(line);
                sb.append(lineSeparator);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close(bufferedReader);
        }
        JsonParser jsonParser = new JsonParser();
        return jsonParser.parse(sb.toString()).getAsJsonObject();
    }
}
