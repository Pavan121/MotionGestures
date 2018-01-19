package com.funlearn.motiongesturesapp;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import GREProtocol.Greapi;

import static android.Manifest.permission.INTERNET;

public class MainActivity extends AppCompatActivity  implements SensorEventListener {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_INTERNET = 0;
    private ArrayAdapter<String> gesturesListAdapter = null;
    private ListView recognizedGesturesList;
    private ToggleButton toggleButton = null;
    private WebSocket webSocket;
    private SocketAdapter socketAdapter = new SocketAdapter();
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private List<Greapi.Acceleration> accelerationList = new ArrayList<>();
    private int index = 0;
    private String currentSessionId = null;
    private String strAPIKEY="";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sharedPreferences=getSharedPreferences(SH_TAG,MODE_PRIVATE);
        strAPIKEY=getStrAPIKEY();
        gesturesListAdapter = new ArrayAdapter<>(this, R.layout.gesture_item);
        recognizedGesturesList = findViewById(R.id.recognizedGesturesList);
        recognizedGesturesList.setAdapter(gesturesListAdapter);
        toggleButton = findViewById(R.id.test_toggle);
        toggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                if (checked ) {
                    if(strAPIKEY.length()<10)
                    {
                        addApiKey();
                    }
                    else
                      connectAndStartListening();
                } else {

                    disconnectAndStopListening();
                }
            }
        });
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        boolean canConnect = mayConnectToInternet();
        toggleButton.setEnabled(canConnect);
        if(strAPIKEY==null || strAPIKEY.length()==0)
        {
            addApiKey();
        }
    }

    private void showTaost(String strMsg)
    {
        Toast.makeText(this, strMsg, Toast.LENGTH_SHORT).show();
    }

    private void disconnectAndStopListening() {
        try {
            sensorManager.unregisterListener(this, accelerometer);
            webSocket.sendClose();
            webSocket.disconnect();
            webSocket.removeListener(socketAdapter);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

    }

    private void connectAndStartListening() {
        try {
            webSocket = new WebSocketFactory().createSocket("wss://sdk.motiongestures.com/recognition?api_key="+strAPIKEY);
            webSocket.addListener(socketAdapter);
            currentSessionId = UUID.randomUUID().toString();
            index = 0;
            webSocket.connectAsynchronously();
        } catch (IOException e) {
            Log.e(TAG, "Cannot create socket connection", e);
        }
    }

    @Override
    protected void onPause() {
        toggleButton.setChecked(false);
        super.onPause();
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        float x = sensorEvent.values[0];
        float y = sensorEvent.values[1];
        float z = sensorEvent.values[2];
        accelerationList.add(Greapi.Acceleration.newBuilder().setX(x).setY(y).setZ(z).setIndex(index).build());
        index++;
        if (accelerationList.size() >= 100) {
            try {
                Greapi.AccelerationMessage accelerationMessage = Greapi.AccelerationMessage.newBuilder().setId(currentSessionId).addAllAccelerations(accelerationList).build();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                accelerationMessage.writeTo(outputStream);
                webSocket.sendBinary(outputStream.toByteArray());
                accelerationList.clear();
            } catch (IOException ex) {
                Log.e(TAG, "Error sending acceleration data to the server", ex);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }

    private boolean mayConnectToInternet() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Log.d(TAG, "We can connect to the internet");
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(INTERNET) == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "We can connect to the internet");
                return true;
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{INTERNET}, REQUEST_INTERNET);
        }
        Log.d(TAG, "Cannot connect to the internet");
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_INTERNET) {
            toggleButton.setEnabled(grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED);
        }
    }

    public void onChangeApi(View view) {
        addApiKey();
    }

    private final class SocketAdapter extends WebSocketAdapter {
        @Override
        public void onBinaryMessage(WebSocket websocket, byte[] binary) throws Exception {
            try {
                ByteArrayInputStream inputStream = new ByteArrayInputStream(binary);
                final Greapi.RecognitionResponse recognitionResponse = Greapi.RecognitionResponse.parseFrom(inputStream);
                if (recognitionResponse.getStatus() == Greapi.Status.GestureEnd) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            gesturesListAdapter.add("Recognized gesture " + recognitionResponse.getName() + " with label " + recognitionResponse.getLabel() +  " conf " + (recognitionResponse.getConfidence()*100));
                           // toggleButton.setChecked(false);
                            Log.d("data",""+recognitionResponse.getConfidence());

                        }
                    });
                } else {
                    Log.d(TAG, "Received recognition response with status " + recognitionResponse.getStatus());
                }
            } catch (IOException ex) {
                Log.e(TAG, "Error deserializing the recognition response", ex);
            }
        }

        @Override
        public void onConnected(WebSocket websocket, Map<String, List<String>> headers) throws Exception {
            super.onConnected(websocket, headers);
            Log.d(TAG, "Connected to server");
            sensorManager.registerListener(MainActivity.this, accelerometer, 10_000);//10_000
        }
    }


   /* public void onConnected(WebSocket websocket, Map<String, List<String>> headers) throws Exception {
        Log.d(TAG, "Connected to server");
        sensorManager.registerListener(MainActivity.this, accelerometer, 10_000);
    }*/

   private void addApiKey()
   {
       AlertDialog.Builder builder = new AlertDialog.Builder(this);
       builder.setTitle("API Key");

// Set up the input
       final EditText input = new EditText(this);
// Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
       input.setInputType(InputType.TYPE_CLASS_TEXT);
       builder.setView(input);

// Set up the buttons
       builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
           @Override
           public void onClick(DialogInterface dialog, int which) {
               if(input.getText().toString().trim().length()>10) {
                   strAPIKEY = input.getText().toString().trim();
                   storeApiKEy(strAPIKEY);
                   disconnectAndStopListening();
                   toggleButton.setEnabled(true);
               }
               else
                   Toast.makeText(MainActivity.this,"API key is too short",Toast.LENGTH_SHORT).show();
           }
       });
       builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
           @Override
           public void onClick(DialogInterface dialog, int which) {
               dialog.cancel();
           }
       });

       builder.show();

   }

   private SharedPreferences sharedPreferences;
   private static final String KEY_API="api_key";
   private static final String SH_TAG="prefappdata";
   private void storeApiKEy(String strAPIKEY)
   {
       SharedPreferences.Editor prefeditor=sharedPreferences.edit();
       prefeditor.putString(KEY_API,strAPIKEY);
       prefeditor.commit();
   }

   private void removeAPIkey()
   {
       SharedPreferences.Editor prefeditor=sharedPreferences.edit();
       prefeditor.putString(KEY_API,"");
       prefeditor.commit();
   }

   private String getStrAPIKEY()
   {
      return sharedPreferences.getString(KEY_API,"");
   }
}
