package com.hello.mpsit.mpsitlocation;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.os.Vibrator;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Locale;
import java.util.Random;


/**
 * <h1>MPSIT Location Manager</h1>
 *
 * The MPSIT Location is a tiny little Android program that gets the current location of the
 * mobile device using either GPS or network and sends this location via HTTP to a Mapbeats node.js
 * server backend, responsible for showing user's location, with a unique ID.
 *
 * Application offers a ListView with settings: Force GPS use and Check Network connectivity status
 * If it were to be implemented with buttons, then the application should have also implement
 * the View.onClickListener interface
 *
 * @author  Mihai Barbulescu, Mihnea Dobrescu-Balaur
 * @version 1.0
 * @since   2016-01-26
 */
public class MainActivity extends AppCompatActivity implements LocationListener {

    //Application UI
    public TextView textGeo;
    public TextView textAddr;
    public WebView webview;
    public ListView listView;

    /**
     * Class providing access to the system location services.
     */
    public LocationManager mLocationManager;
    /**
     * Class containing current geographic location of mobile device
     */
    public Location location;
    /**
     * Current location of mobile device in latitude and longitude
     */
    public double myLatitude, myLongitude;
    /**
     * Location provider: could be mobile network, GPS etc.
     */
    public static String location_provider;
    /**
     * Minimum time interval between location updates, in milliseconds
     */
    public static final int delay_ms_loc = 400;
    /**
     * Minimum distance which triggers location change (minimum distance between location updates)
     * in meters
     */
    public static final int min_dist = 1;
    /**
     * The webserver containing JSON interpretation of data sent by mobile device
     */
    public static final String ourServer = "http://ischgl.mihneadb.net:3000/beats";

    /**
     * A vibrator class used by application to trigger mobile device virbation at various events
     */
    public Vibrator vibrator;

    /**
     * Function to set location provider depending on connectivity to internet: mobile network
     * if access to internet works, otherwise use GPS sensors
     *
     * @return ture in case mobile device is connected to internet. Also a TextView regarding
     * connectivity information is set.
     */
    private boolean setLocationProviderIConnection() {
        TextView tvIsConnected = (TextView) findViewById(R.id.tvIsConnected);
        boolean conn_status = isConnected();
        if (conn_status) {
            tvIsConnected.setBackgroundColor(0xFF00CC00);
            tvIsConnected.setText("You are conncted to INTERNET");
            location_provider = LocationManager.NETWORK_PROVIDER;
        } else {
            tvIsConnected.setBackgroundColor(0xFFCC0000);
            tvIsConnected.setText("INTERNET connection error. Will try GPS, but HTTP will not send");
            location_provider = LocationManager.GPS_PROVIDER;
        }
        return conn_status;
    }

    /**
     * This is the function which is called upon starting MainActivity
     * In our application this will show: the status of the connectivity to Internet
     * a map with current location and GPS coordinates together with full address decoded
     * using geocoding
     *
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // if these aren't called, HTTP requests won't work. Fuck Android, no documentation of these
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //Get webview of the webserver we connect to :) but with the map plot :)
        webview = (WebView) findViewById(R.id.webview);
        webview.getSettings().setJavaScriptEnabled(true);

        //Get listview
        listView = (ListView) findViewById(R.id.list);
        // Defined Array values to show in ListView
        String[] values = new String[] {
                "Check Internet connection",
                "Force use of GPS",
                "Start Browser"};
        // Define a new Adapter
        // First parameter - Context
        // Second parameter - Layout for the row
        // Third parameter - ID of the TextView to which the data is written
        // Forth - the Array of data
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_2, android.R.id.text1, values);
        listView.setAdapter(adapter);

        // ListView Item Click Listener
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                // ListView Clicked item index
                int itemPosition = position;
                // ListView Clicked item value
                String itemValue = (String) listView.getItemAtPosition(position);

                //Handle user click
                handleListViewClicks(position);
            }
        });

        //Application text views
        textGeo = (TextView) findViewById(R.id.geo);
        textAddr = (TextView) findViewById(R.id.geoaddr);

        setLocationProviderIConnection();

        //get current location of mobile device
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        mLocationManager.requestLocationUpdates(location_provider, delay_ms_loc, min_dist, this);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            Log.wtf("LOCATION", "Permission was not granted by user. Exiting");
            return;
        }

        location = mLocationManager.getLastKnownLocation(location_provider);

        if (location != null) {
            // Do something with the recent location fix otherwise wait for the update below
            myLongitude = location.getLongitude();
            myLatitude = location.getLatitude();
            textGeo.setText("Latitude, Longitude : " +
                    String.valueOf(myLatitude) + "," + String.valueOf(myLongitude));
            geoDecodeAddr(myLatitude, myLongitude);
            webview.loadUrl("https://maps.googleapis.com/maps/api/staticmap?center=" +
                    String.valueOf(myLatitude) + "," + String.valueOf(myLongitude) +
                    "&zoom=16&size=400x280&maptype=roadmap&markers=color:red%7Clabel:C%7C" +
                    String.valueOf(myLatitude) + "," + String.valueOf(myLongitude));
        } else {
            Log.d("NO_LOCATION", "Location impossible to be retrieved now");
            textAddr.setText("NO_LOCATION - Location impossible to be retrieved ...");
        }

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // call AsynTask to perform network operation on separate thread
                if (setLocationProviderIConnection()) {
                    new HttpAsyncTask().execute(ourServer);
                    Snackbar.make(view, "Check page http://ischgl.mihneadb.net:3000/",
                                  Snackbar.LENGTH_LONG).setAction("Action", null).show();
                } else {
                    Snackbar.make(view, "No internet connection detected",
                                  Snackbar.LENGTH_LONG).setAction("Action", null).show();
                }
            }
        });
    }

    /**
     * Function to get decoding of address based on latitude and longitude.
     * A TextView is set in application with the decoded address
     *
     * @param lat Latitude (double)
     * @param lng Longitude (double)
     */
    public void geoDecodeAddr(double lat, double lng) {
        Geocoder geocoder;
        List<Address> addresses;
        geocoder = new Geocoder(this, Locale.getDefault());

        try {
            // Here third param represent max location result to returned,
            // by documents it recommended 1 to 5
            addresses = geocoder.getFromLocation(lat, lng, 5);
            String address = addresses.get(0).getAddressLine(0);
            // If any additional address line present than only,
            // check with max available address lines by getMaxAddressLineIndex()
            String city = addresses.get(0).getLocality();
            String country = addresses.get(0).getCountryName();
            String subLocality = addresses.get(0).getSubLocality(); // Only if available else return NULL
            if (subLocality != null)
                textAddr.setText(address + ", " + subLocality + ", " + city + ", " + country);
            else
                textAddr.setText(address + ", " + city + ", " + country);
        } catch (IOException e) {
            Log.e("GEOCODER", "Geocoder failed\n");
            e.printStackTrace();
        }
    }

    /**
     * Function which checks, using connectivity manager, whether the mobile device
     * is connected or not to the internet using wi-fi or mobile network
     *
     * @return boolean true if connected, false otherwise
     */
    public boolean isConnected() {
        ConnectivityManager connMgr = (ConnectivityManager)
                getSystemService(Activity.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected())
            return true;
        else
            return false;
    }

    /**
     * Function which makes HTTP request to server to place a pinpoint on the location
     * determined by the mobile device. The HTTP request is of type application/json
     *
     * @param url Address to which mobile device should connect for sending location
     * @param obj a JSON containing latitude, longitude and a unique identifier
     * @return
     */
    public String postData(String url, JSONObject obj) {

        String json = "";
        InputStream inputStream = null;
        String result = "";

        try {
            // create HttpClient
            HttpClient httpclient = new DefaultHttpClient();
            HttpPost httpPost = new HttpPost(url);

            json = obj.toString();

            // set json to StringEntity
            StringEntity se = new StringEntity(json);
            httpPost.setEntity(se);
            httpPost.setHeader("Accept", "application/json");
            httpPost.setHeader("Content-type", "application/json");

            HttpResponse httpResponse = httpclient.execute(httpPost);
            inputStream = httpResponse.getEntity().getContent();

            if (inputStream != null)
                result = convertInputStreamToString(inputStream);
            else
                result = "Did not work!";

        } catch (Exception e) {
            //Log.d("HTTP PROBLEM", "Exception: " + e.toString());
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Function responsible for handling the clicks performed by the user on the settings ListView
     *
     * @param pos an integer identifying position (index) in ListView. If 0, it means that Internet
     *            connectivity was required, if 1 it means that user wants to force use of GPS sensors
     */
    public void handleListViewClicks(int pos) {
        switch (pos) {
            case 0:
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED &&
                        ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                                != PackageManager.PERMISSION_GRANTED) {
                    Log.wtf("LOCATION", "Permission was not granted by user. Exiting");
                    return;
                }
                if (setLocationProviderIConnection() == false) {
                    location_provider = LocationManager.GPS_PROVIDER;
                    mLocationManager.requestLocationUpdates(location_provider, delay_ms_loc, min_dist, this);
                    Toast.makeText(getBaseContext(), "GPS Set", Toast.LENGTH_LONG).show();
                } else {
                    location_provider = LocationManager.NETWORK_PROVIDER;
                    Toast.makeText(getBaseContext(), "Network used for loc", Toast.LENGTH_LONG).show();
                }

                break;

            case 1:
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED &&
                        ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                                != PackageManager.PERMISSION_GRANTED) {
                    Log.wtf("LOCATION", "Permission was not granted by user. Exiting");
                    return;
                }
                location_provider = LocationManager.GPS_PROVIDER;
                mLocationManager.requestLocationUpdates(location_provider, delay_ms_loc, min_dist, this);
                Toast.makeText(getBaseContext(), "GPS Set", Toast.LENGTH_LONG).show();
                vibrator =  (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                vibrator.vibrate(200);
                break;
            case 2:
                Intent i = new Intent(android.content.Intent.ACTION_VIEW, Uri.parse("http://ischgl.mihneadb.net:3000/"));
                startActivity(i);
            default:
                Log.wtf("ERROR", "handleListViewClicks() : We should not be here");
                return;
        }

    }

    private class HttpAsyncTask extends AsyncTask<String, Void, String> {

        int rand_num;

        @Override
        protected String doInBackground(String... urls) {
            JSONObject locJson = new JSONObject();
            Random r = new Random();
            rand_num = r.nextInt(65536);

            try {
                locJson.accumulate("lat", myLatitude);
                locJson.accumulate("lng", myLongitude);
                locJson.accumulate("id", rand_num);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            postData(urls[0], locJson);
            return "works";
        }
        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(String result) {
            Toast.makeText(getBaseContext(), "Data Sent with number " + String.valueOf(rand_num),
                    Toast.LENGTH_LONG).show();
            vibrator =  (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            vibrator.vibrate(200);
        }
    }

    /**
     * Helper function to convert a readable source of bytes (e.g. a HTTP response) to a string
     *
     * @param inputStream A readable source of bytes
     * @return Its corresponding string
     */
    private String convertInputStreamToString(InputStream inputStream) {
        BufferedReader bufferedReader = new BufferedReader( new InputStreamReader(inputStream));
        String line = "";
        String result = "";
        try {
            while((line = bufferedReader.readLine()) != null)
                result += line;
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Function used to specify the options menu for MainActivity. It is responsible for
     * initializing the contents of the Activity's standard options menu.
     *
     * @param menu input menu to be inflated
     * @return always true
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    /**
     * Function to handle what happens when user clicks on an item from the menu
     *
     * @param item pressed by user
     * @return true in case user presses Settings
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Toast.makeText(getBaseContext(), "Settings button was pushed", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(this, SettingsActivity.class);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Function that handles events when location of the mobile device is changed
     *
     * @param location Class containing geographic location of mobile device
     */
    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {
            Log.d("Location Changed", location.getLatitude() + " and " + location.getLongitude());
            myLongitude  = location.getLongitude();
            myLatitude = location.getLatitude();
            textGeo.setText("LOC CHANGED : Latitude, Longitude : " +
                    String.valueOf(myLatitude) + "," +
                    String.valueOf(myLongitude));
            geoDecodeAddr(myLatitude, myLongitude);
            webview.loadUrl("https://maps.googleapis.com/maps/api/staticmap?center=" +
                    String.valueOf(myLatitude) + "," + String.valueOf(myLongitude) +
                    "&zoom=16&size=400x280&maptype=roadmap&markers=color:red%7Clabel:C%7C" +
                    String.valueOf(myLatitude) + "," + String.valueOf(myLongitude));
        } else {
            Log.d("NO_NEW_LOCATION", "No new location in onLocationChanged()");
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.i("LOCATION", "Status was changed");
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.i("LOCATION", "Provider is enabled");
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.i("LOCATION", "Provider is disabled");
    }
}
