package com.fediphoto;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import com.google.gson.JsonObject;

import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {
    private final int CAMERA_REQUEST = 1239;
    private final String TAG = this.getClass().getCanonicalName();
    private final Context context = this;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button button = findViewById(R.id.button_camera);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");
                startActivityForResult(intent, CAMERA_REQUEST);
            }
        });

    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CAMERA_REQUEST  && resultCode == Activity.RESULT_OK) {
            Bitmap image = (Bitmap) data.getExtras().get("data");
            if (image == null) {
                Log.i(TAG, "Image is null.");
            } else {
                Log.i(TAG, String.format("Image byte count: %d", image.getByteCount()));
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
          switch (item.getItemId()) {
              case R.id.accounts:
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
