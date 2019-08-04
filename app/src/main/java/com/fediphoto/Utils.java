package com.fediphoto;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class Utils {
    private final static String lineSeparator = System.getProperty("line.separator");
    private final static String TAG = "com.fediphoto.Utils";
    public static final String LINE_FEED = "\r\n";

    public static void main(String[] args) {

    }

    public static boolean isBlank(String s) {
        if (s == null || s.trim().length() == 0) {
            return true;
        }
        return false;
    }

    public static boolean isNotBlank(String s) {
        return !isBlank(s);

    }

    public static int getInt(String s) {
        if (isBlank(s)) {
            return 0;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            Log.i(TAG, String.format("Unable to parse \"%s\" to int.", s));
        }
        return 0;
    }

    public static JsonObject getAccountSelectedFromSettings(Context context) {
        JsonObject settings = getSettings(context);
        int accountSelectedIndex = Utils.getInt(Utils.getProperty(settings, MainActivity.Literals.accountIndexSelected.name()));
        JsonArray jsonArray = settings.getAsJsonArray(MainActivity.Literals.accounts.name());
        if (jsonArray != null && !jsonArray.isJsonNull() && jsonArray.size() > 0) {
            if (accountSelectedIndex < jsonArray.size()) {
                return jsonArray.get(accountSelectedIndex).getAsJsonObject();
            } else {
                return jsonArray.get(0).getAsJsonObject();
            }
        }
        return null;
    }
    public static JsonObject getAccountActiveFromSettings(Context context) {
        JsonObject settings = getSettings(context);
        int accountSelectedIndex = Utils.getInt(Utils.getProperty(settings, MainActivity.Literals.accountIndexActive.name()));
        JsonArray jsonArray = settings.getAsJsonArray(MainActivity.Literals.accounts.name());
        if (jsonArray != null && !jsonArray.isJsonNull() && jsonArray.size() > 0) {
            if (accountSelectedIndex < jsonArray.size()) {
                return jsonArray.get(accountSelectedIndex).getAsJsonObject();
            } else {
                return jsonArray.get(0).getAsJsonObject();
            }
        }
        return null;
    }

    public static JsonObject getStatusSelectedFromSettings(Context context) {
        JsonObject settings = getSettings(context);
        int statusSelectedIndex = Utils.getInt(Utils.getProperty(settings, MainActivity.Literals.statusIndexSelected.name()));
        JsonArray jsonArray = settings.getAsJsonArray(MainActivity.Literals.statuses.name());
        if (jsonArray != null && !jsonArray.isJsonNull() && jsonArray.size() > 0) {
            if (statusSelectedIndex < jsonArray.size()) {
                return jsonArray.get(statusSelectedIndex).getAsJsonObject();
            } else {
                return jsonArray.get(0).getAsJsonObject();
            }
        }
        return null;
    }
    public static JsonObject getStatusActiveFromSettings(Context context) {
        JsonObject settings = getSettings(context);
        int statusSelectedIndex = Utils.getInt(Utils.getProperty(settings, MainActivity.Literals.statusIndexActive.name()));
        JsonArray jsonArray = settings.getAsJsonArray(MainActivity.Literals.statuses.name());
        if (jsonArray != null && !jsonArray.isJsonNull() && jsonArray.size() > 0) {
            if (statusSelectedIndex < jsonArray.size()) {
                return jsonArray.get(statusSelectedIndex).getAsJsonObject();
            } else {
                return jsonArray.get(0).getAsJsonObject();
            }
        }
        return null;
    }

    public static void writeSettings(Context context, JsonObject jsonObject) {
        Log.i(TAG, String.format("Write settings file %s:", jsonObject.toString()));
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileOutputStream openFileOutput = context.openFileOutput("settings.json", Context.MODE_PRIVATE)) {
            openFileOutput.write(gson.toJson(jsonObject).getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void close(Object... objects) {
        for (Object object : objects) {
            if (object != null) {
                try {
                    boolean closed = false;
                    if (object instanceof java.io.BufferedOutputStream) {
                        BufferedOutputStream bufferedOutputStream = (BufferedOutputStream) object;
                        bufferedOutputStream.close();
                        closed = true;
                    }
                    if (object instanceof java.io.StringWriter) {
                        StringWriter stringWriter = (StringWriter) object;
                        stringWriter.close();
                        closed = true;
                    }

                    if (object instanceof java.io.FileReader) {
                        FileReader fileReader = (FileReader) object;
                        fileReader.close();
                        closed = true;
                    }
                    if (object instanceof java.io.BufferedReader) {
                        BufferedReader br = (BufferedReader) object;
                        br.close();
                        closed = true;
                    }
                    if (object instanceof Socket) {
                        Socket socket = (Socket) object;
                        socket.close();
                        closed = true;
                    }
                    if (object instanceof PrintStream) {
                        PrintStream printStream = (PrintStream) object;
                        printStream.close();
                        closed = true;
                    }
                    if (object instanceof ServerSocket) {
                        ServerSocket serverSocket = (ServerSocket) object;
                        serverSocket.close();
                        closed = true;
                    }
                    if (object instanceof Scanner) {
                        Scanner scanner = (Scanner) object;
                        scanner.close();
                        closed = true;
                    }
                    if (object instanceof InputStream) {
                        InputStream inputStream = (InputStream) object;
                        inputStream.close();
                        closed = true;
                    }
                    if (object instanceof OutputStream) {
                        OutputStream outputStream = (OutputStream) object;
                        outputStream.close();
                        closed = true;
                    }
                    if (object instanceof Socket) {
                        Socket socket = (Socket) object;
                        socket.close();
                        closed = true;
                    }
                    if (object instanceof PrintWriter) {
                        PrintWriter pw = (PrintWriter) object;
                        pw.close();
                        closed = true;
                    }
                    if (!closed) {
                        System.out.format("Object not closed. Object type not defined in this close method. Name: %s Stack: %s\n", object.getClass().getName(), getClassNames());
                    }
                } catch (Exception e) {
                    System.out.println(e.getLocalizedMessage());
                }
            }
        }
    }

    public static String getClassNames() {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        StringBuilder classNames = new StringBuilder();
        for (StackTraceElement e : stackTraceElements) {
            classNames.append(e.getClassName()).append(", ");
        }
        if (classNames.toString().endsWith(", ")) {
            classNames.delete(classNames.length() - 2, classNames.length());
        }
        return classNames.toString();
    }

    public static long getLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException nfe) {
            return 0;
        }
    }

    public static URL getUrl(String urlString) {
        try {
            return new URL(urlString);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return null;
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
            Log.i(TAG, String.format("settings.json not found. Returning new JsonObject. ERROR: %s", e.getLocalizedMessage()));
            return new JsonObject();
        } finally {
            close(bufferedReader);
        }
        if (sb.length() == 0) {
            return new JsonObject();
        }
        JsonParser jsonParser = new JsonParser();
        JsonElement jsonElement = jsonParser.parse(sb.toString());
        if (jsonElement == null || jsonElement.isJsonNull()) {
            return new JsonObject();
        }
        JsonObject jsonObject = jsonElement.getAsJsonObject();
        JsonArray accounts = jsonObject.getAsJsonArray(MainActivity.Literals.accounts.name());
        int accountQuantity = 0;
        if (accounts!= null && accounts.isJsonArray()) {
            accountQuantity = accounts.size();
        }
        int accountIndexSelected = Utils.getInt(Utils.getProperty(jsonObject, MainActivity.Literals.accountIndexSelected.name()));
        int accountIndexActive = Utils.getInt(Utils.getProperty(jsonObject, MainActivity.Literals.accountIndexActive.name()));
        Log.i(TAG, String.format("Account quantity %d selected %d active %d.", accountQuantity, accountIndexSelected, accountIndexActive));
        if (accountQuantity != 0 && accountQuantity < accountIndexSelected) {
            jsonObject.addProperty(MainActivity.Literals.accountIndexSelected.name(), 0);
            writeSettings(context, jsonObject);
            if (accountQuantity < accountIndexActive) {
                jsonObject.addProperty(MainActivity.Literals.accountIndexActive.name(), 0);
                writeSettings(context, jsonObject);
            }
        }
        Log.i(TAG, String.format("getSettings: %s", jsonObject.toString()));
        return jsonObject;
    }

    public static boolean isJsonObject(JsonElement jsonElement) {
        if (jsonElement == null) {
            return false;
        }
        try {
            jsonElement.getAsJsonObject();
            return true;
        } catch (java.lang.IllegalStateException e) {
            return false;
        }
    }

    public static String urlEncodeComponent(String s) {
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String getProperty(JsonElement jsonElement, String propertyName) {
        if (jsonElement == null) {
            return "";
        }
        JsonElement property = jsonElement.getAsJsonObject().get(propertyName);
        if (property != null && !property.isJsonNull()) {
            return property.getAsString();
        }
        return "";
    }

    public static String getApplicationName(Context context) {
        ApplicationInfo applicationInfo = context.getApplicationInfo();
        int stringId = applicationInfo.labelRes;
        return stringId == 0 ? applicationInfo.nonLocalizedLabel.toString() : context.getString(stringId);
    }

    public static int getAccountQuantity(Context context) {
        JsonObject settings = getSettings(context);
        if (settings != null && !settings.isJsonNull() && isJsonObject(settings)) {
            JsonArray accounts = settings.getAsJsonArray(MainActivity.Literals.accounts.name());
            if (accounts != null && !accounts.isJsonNull() && accounts.isJsonArray()) {
                return accounts.size();
            }
        }
        return 0;
    }


    public static void copyFile(File sourceFilePath, File destinationFilePath) {
        try {
            if (!sourceFilePath.exists()) {
                return;
            }
            FileChannel source;
            FileChannel destination;
            source = new FileInputStream(sourceFilePath).getChannel();
            destination = new FileOutputStream(destinationFilePath).getChannel();
            if (destination != null && source != null) {
                destination.transferFrom(source, 0, source.size());
            }
            if (source != null) {
                source.close();
            }
            if (destination != null) {
                destination.close();
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

}
