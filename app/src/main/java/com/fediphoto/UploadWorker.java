package com.fediphoto;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.util.Date;

public class UploadWorker extends Worker {
    private final String TAG = this.getClass().getCanonicalName();

    public UploadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.i(TAG, String.format("UploadWorker test started %s", new Date()));
        Data data = getInputData();
        String fileName = data.getString(MainActivity.Literals.fileName.name());
        File file = new File(fileName);
        Log.i(TAG, String.format("File name %s file exists %s", fileName, file.exists()));
        try {
            Thread.sleep(1000 * 60);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Log.i(TAG, String.format("UploadWorker test finished %s", new Date()));
        return Result.success();
    }
}
