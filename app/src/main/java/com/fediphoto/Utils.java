package com.fediphoto;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;


import java.io.BufferedOutputStream;
import java.io.BufferedReader;
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
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

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
            e.printStackTrace();
        } finally {
            close(bufferedReader);
        }
        JsonParser jsonParser = new JsonParser();
        return jsonParser.parse(sb.toString()).getAsJsonObject();
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
            return URLEncoder.encode(s, "UTF-8");
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

}
