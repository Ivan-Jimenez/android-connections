package com.ivan.connections;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;

public class MainActivity extends AppCompatActivity {
    // Default timer values
    private static int     timeoutConnection = 5000;
    private static int     timeoutSocket     = 5000;
    private static Integer timeoutReachable  = 5000;
    private static Integer updateInterval    = 10000;


    // Array List definition;
    //
    // pingType (int) 0=isReachable 1=httpclient
    // connName (Str) Name of the connection to test
    // ConnURL  (Str) URL or IP address of the connection
    // Response (Str) Response back from ICMP or HTTP
    ArrayList<Integer> pingType = new ArrayList<>();
    ArrayList<String>  connName = new ArrayList<>();
    ArrayList<String>  connURL  = new ArrayList<>();
    ArrayList<String>  response = new ArrayList<>();

    private static JSONArray connectionFileJson = null;
    private static String connectionFileTxt = "";

    private ImageView image;

    // Green and red color values
    private static String[] textColor = new String[] {"#5d9356", "#ff0000"};

    // Array of TextViews which be used to dinamically build the result screen, maximum 20.
    private static TextView[][] pingTextView = new TextView[20][3];

    // Status Thread
    Thread statusThread;
    boolean bStatusThreadStop;

    // Clock Thread
    Thread clockThread;
    boolean clockThreadStop;

    @Override
    protected void onResume () {
        super.onResume();
        IntentFilter filter = new IntentFilter(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        this.registerReceiver(wifiStatusReceiver, filter);
    }

    @Override
    protected void onPause () {
        this.unregisterReceiver(wifiStatusReceiver);
        super.onPause();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // FIXME: This doesn't work. Different version of Android maybe?
        // Setup the ActionBar and spiner in the ActionBar
        //getActionBar().setDisplayHomeAsUpEnabled(true);
        //getActionBar().setSubtitle("Practical Android");
        //getActionBar().setTitle("fuck!");

        setContentView(R.layout.activity_main);

        pingType.clear();
        connName.clear();
        connURL.clear();
        response.clear();

        // Read the JSON file
        try {
            Resources res = getResources();
            InputStream inputStream = res.openRawResource(R.raw.connectionfile);

            byte[] b = new byte[inputStream.available()];
            inputStream.read(b);
            connectionFileTxt = new String(b);
        } catch (IOException e) { e.printStackTrace(); }

        // Build the ArrayLists
        try {
            connectionFileJson = new JSONArray(connectionFileTxt);
            for (int i = 0; i < connectionFileJson.length(); i++) {
                int type = (Integer) jsonGetter(connectionFileJson.getJSONArray(i), "type");
                pingType.add(type);

                String cname = jsonGetter(connectionFileJson.getJSONArray(i), "name").toString();
                connName.add(cname);

                String url = jsonGetter(connectionFileJson.getJSONArray(i), "url").toString();
                connURL.add(url);

                String res = jsonGetter(connectionFileJson.getJSONArray(i), "res").toString();
                response.add(res);
            }
        } catch (JSONException e) { e.printStackTrace(); }

        // Create and run status thread
        createAndRunStatusThread(this);

        // Create and run clock thread
        createAndRunClockThread(this);
    }

    private void updateConnectionStatus () {
        // Update the wi-fi status
        image = (ImageView) findViewById(R.id.image1);
        image.setBackgroundResource(R.drawable.presence_invisible);

        if (checkInternetConnection()) image.setBackgroundResource(R.drawable.presence_online);
        else image.setBackgroundResource(R.drawable.presence_busy);

        // Grab the LinearLayout where we will dynamically add LL for the ping wor list
        LinearLayout pingLinearLayout = findViewById(R.id.insertPings);
        pingLinearLayout.removeAllViews();

        // Set a LayoutParams for the new Layouts we will add for the ping work list items satatus
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(5, 5, 5, 5);
        layoutParams.gravity = Gravity.CENTER;

        // LayoutParams for the TextViews
        final float WIDE = this.getResources().getDisplayMetrics().widthPixels;
        int valueWide = (int) (WIDE * 0.30f);   // Set columns for 1/3 screen width
        LinearLayout.LayoutParams layoutParamsTV = new LinearLayout.LayoutParams(valueWide, LinearLayout.LayoutParams.WRAP_CONTENT);
        layoutParamsTV.setMargins(5, 5,5, 5);
        layoutParamsTV.gravity = Gravity.CENTER;

        // Setup a screen proportional font size
        DisplayMetrics displayMetrics = new DisplayMetrics();
        this.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int fontSize = (int) (WIDE / 36.0f / (displayMetrics.scaledDensity)); // 36 is arbitrary and gives approximately 70 chars across a 7" tablet

        // Loop through the work list, fire off a ping for each item based on the type
        for (int i = 0; i < pingType.size(); i++) {
            // Create the new horizontal linearlayout for this item i
            LinearLayout newLL;
            newLL = new LinearLayout(MainActivity.this);
            newLL.setLayoutParams(layoutParams);
            newLL.setOrientation(LinearLayout.HORIZONTAL);
            newLL.setHorizontalGravity(Gravity.CENTER);
            pingLinearLayout.addView(newLL, i);

            pingTextView[i][0] = new TextView(MainActivity.this);
            pingTextView[i][0].setText(connName.get(i));
            pingTextView[i][0].setTextSize(fontSize);
            newLL.addView(pingTextView[i][0], 0, layoutParamsTV);

            pingTextView[i][1] = new TextView(MainActivity.this);
            pingTextView[i][1].setText(connURL.get(i));
            pingTextView[i][1].setTextSize(fontSize);
            newLL.addView(pingTextView[i][1], 1, layoutParamsTV);

            pingTextView[i][2] = new TextView(MainActivity.this);
            pingTextView[i][2].setText(response.get(i));
            pingTextView[i][2].setTextSize(fontSize);
            newLL.addView(pingTextView[i][2], 2, layoutParamsTV);

            if (pingType.get(i) == 0)
                // Send the ping with ICMP
                new pingICMP(connURL.get(i), i).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

            if (pingType.get(i) == 1)
                // Send the ping with HTTP
                new pingHTTP(connURL.get(i), i).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }

        // Update the refresh time
        TextView textRefr = (TextView) findViewById(R.id.txtUpdate);
        textRefr.setText(GetTime());
    }

    private void updateClock () {
        TextView textTime = (TextView) findViewById(R.id.txtTime);
        textTime.setText(GetTime());
    }

    // Check for connectivity using ICMP
    public class pingICMP extends AsyncTask<Void, String, Integer> {
        private String ip1;
        private boolean code;
        private int item;
        private InetAddress in1;

        public pingICMP (String ip, int i) {
            this.ip1 = ip;
            this.in1 = null;
            this.item = i;
            this.code = false;
        }

        @Override
        protected Integer doInBackground (Void ...params) {
            try { in1 = InetAddress.getByName(ip1); }
            catch (Exception e) { code = false; }

            try {
                if (in1.isReachable(timeoutReachable)) code = true;
                else code = false;
            } catch (Exception e) { code = false; }

            return 1;
        }

        @Override
        protected void onPostExecute (Integer result) {
            if (code) {
                pingTextView[item][2].setText("Accesible");
                pingTextView[item][2].setTextColor(Color.parseColor(textColor[0])); // Green
            } else {
                pingTextView[item][2].setText("Inaccesible");
                pingTextView[item][2].setTextColor(Color.parseColor(textColor[1])); // Red
            }
        }
    }

    // Check for connectivity using HTTP
    private class pingHTTP extends AsyncTask<Void, String, Integer> {
        private String urlString;
        private boolean pingSuccess;
        private int item;
        private int status;

        private pingHTTP (String ip, int i) {
            this.pingSuccess = false;
            this.item = i;
            this.urlString = ip;
        }

        @Override
        protected Integer doInBackground (Void ...params) {
            try {
                URL url = new URL(urlString);
                HttpURLConnection httpConnection = (HttpURLConnection) url.openConnection();
                httpConnection.setAllowUserInteraction(false);
                httpConnection.setInstanceFollowRedirects(false);
                httpConnection.setRequestMethod("GET");
                httpConnection.connect();

                status = httpConnection.getResponseCode();
                // Check for successful status code = 200 or 204
                if ((status == HttpURLConnection.HTTP_OK) || (status == HttpURLConnection.HTTP_NO_CONTENT))
                    pingSuccess = true;
            } catch (Exception e) { pingSuccess = false; }
            return 1;
        }

        protected void onPostExecute (Integer result) {
            if (pingSuccess) {
                pingTextView[item][2].setText("Código de Estado: " + status);
                pingTextView[item][2].setTextColor(Color.parseColor(textColor[0])); // Green
            } else {
                pingTextView[item][2].setText("Código de Estado: " + status);
                pingTextView[item][2].setTextColor(Color.parseColor(textColor[1])); // Red
            }
        }
    }

    private boolean checkInternetConnection () {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();

        TextView connType  = (TextView) findViewById(R.id.txtType);
        TextView connAvail = (TextView) findViewById(R.id.txtAvail);
        TextView connConn  = (TextView) findViewById(R.id.textConn);

        connType.setText("DESCONOCIDA");

        if ((connectivityManager != null) && (networkInfo != null)) {
            // Update the connection type
            if (networkInfo.getTypeName().equalsIgnoreCase("WIFI"))
                connType.setText("WIFI");

            if (networkInfo.getTypeName().equalsIgnoreCase("MOBILE"))
                connType.setText("DATOS");

            // Update the connection status
            if (networkInfo.isAvailable()) {
                connAvail.setText(getString(R.string.available));
                connAvail.setTextColor(Color.parseColor(textColor[0])); // Green

                if (networkInfo.isConnected()) {
                    connConn.setText("Conectado");
                    connConn.setTextColor(Color.parseColor(textColor[0])); // Green
                    return true;
                } else  {
                    connConn.setText("Desconectado");
                    connConn.setTextColor(Color.parseColor(textColor[1]));  // Red
                    return false;
                }
            } else {
                connAvail.setText("No Disponible");
                connAvail.setTextColor(Color.parseColor(textColor[1])); // Red
                return false;
            }
        } else {
            connType.setText("DESCONOCIDO");

            connConn.setText("Desconectado");
            connConn.setTextColor(Color.parseColor(textColor[1]));  // Red

            connAvail.setText("No Disponible");
            connAvail.setTextColor(Color.parseColor(textColor[1])); // Red

            return false;
        }
    }

    public void createAndRunStatusThread (final Activity activity) {
        bStatusThreadStop = false;
        statusThread = new Thread( new Runnable() {
                    @Override
                    public void run() {
                        while (!bStatusThreadStop) {
                            try {
                                activity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        updateConnectionStatus();
                                    }
                                });
                                Thread.sleep(updateInterval);
                            } catch (InterruptedException e) {
                                bStatusThreadStop = true;
                                messageBox(activity, "Exception in status thread: " + e.toString() +
                                    " - " + e.getMessage(), "createAndRunStatusThread Error");
                            }
                        }
                    }
                }
        );
        statusThread.start();
    }

    public void createAndRunClockThread (final Activity activity) {
        clockThreadStop = false;
        clockThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!clockThreadStop) {
                    try {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateClock();
                            }
                        });
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        bStatusThreadStop = true;
                        messageBox(activity, "Exception in clock thread: " + e.toString() +
                                " - " + e.getMessage(), "createAndRunClockThread error");
                    }
                }
            }
        });
        clockThread.start();
    }

    public void messageBox (final Context context, final String message, final String title) {
        this.runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        final AlertDialog alertDialog = new AlertDialog.Builder(context).create();
                        alertDialog.setTitle(title);
                        alertDialog.setIcon(android.R.drawable.stat_sys_warning);
                        alertDialog.setMessage(message);
                        alertDialog.setCancelable(false);
                        alertDialog.setButton("Volver", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                alertDialog.cancel();
                            }
                        });
                        alertDialog.show();
                    }
                }
        );
    }

    BroadcastReceiver wifiStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            TextView textBR = findViewById(R.id.txtBroadcastReceiver);
            SupplicantState supplicantState;
            WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            supplicantState = wifiInfo.getSupplicantState();

            if (supplicantState.equals(SupplicantState.COMPLETED)) {
                // Wifi connected
                textBR.setText("Conectado");
                textBR.setTextColor(Color.parseColor(textColor[0])); // Green
            } else if (supplicantState.equals(SupplicantState.SCANNING)) {
                // No wifi so give an update
                textBR.setText("Escaneando");
                textBR.setTextColor(Color.parseColor(textColor[1])); // Red
            } else if (supplicantState.equals(SupplicantState.DISCONNECTED)) {
                // Wifi not connected
                textBR.setText("Desconectado");
                textBR.setTextColor(Color.parseColor(textColor[1])); // Red
            }
            checkInternetConnection();
        }
    };

    public static String GetTime () {
        Date dt = new Date();
        Integer hours = dt.getHours();
        String formathr = String.format("%02d", hours);
        Integer minutes = dt.getMinutes();
        String forarmin = String.format("%02d", minutes);
        Integer seconds = dt.getSeconds();
        String formatsec = String.format("%02d", seconds);
        String time = formathr + ":" + forarmin + ":" + formatsec;
        return time;
    }

    private Object jsonGetter (JSONArray json, String key) {
        Object value = null;
        for (int i = 0; i < json.length(); i++) {
            try {
                JSONObject obj = json.getJSONObject(i);
                if (obj.has(key)) value = obj.get(key);
            } catch (JSONException e ) { Log.v("jsonGetter Exception", e.toString()); }
        }
        return value;
    }

    @Override
    public boolean onCreateOptionsMenu (Menu menu) {
        new MenuInflater(this).inflate(R.menu.actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected (MenuItem item) {
        if (item.getItemId() == R.id.exit) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
