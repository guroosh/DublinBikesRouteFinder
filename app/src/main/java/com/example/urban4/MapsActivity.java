package com.example.urban4;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Dot;
import com.google.android.gms.maps.model.Gap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.widget.AutocompleteSupportFragment;
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, LocationListener {

    public GoogleMap mMap;
    ArrayList<Marker> markerListSource;
    ArrayList<Marker> markerListDestination;
    ArrayList<Marker> markerListStations;
    ArrayList<BikesData> array;
    String API_KEY;
    String awsLambdaAddress = "<YOUR_AWS_LAMBDA_SERVER>";
    LatLng globalCurrentLocation;
    boolean isStartCurrentLocationSet = false;
    Activity a = this;
    String previousRoute = "";
    String latestBikesData = "";
    ArrayList<Polyline> polylines = new ArrayList<>();
    Button directions, reset;
    int clickValue = 0;
    int tagOnClick = -3;
    int tagOnClick2 = -3;
    String uniqueAndroidID;
    TextView percentage;

    @Override
    protected void onCreate(Bundle savedInstanceState) throws NullPointerException {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        API_KEY = "<YOUR_API_KEY>";
        uniqueAndroidID = Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID);

        //init
        markerListSource = new ArrayList<>();
        markerListDestination = new ArrayList<>();
        createThreadGetForDublinBikes();

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        assert mapFragment != null;
        mapFragment.getMapAsync(this);

        //location setup
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    Activity#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for Activity#requestPermissions for more details.
                return;
            }
        }
        assert locationManager != null;
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);

        initializePlaces();

        percentage = findViewById(R.id.percentage);
        Button buttonShowCurrentLoc = findViewById(R.id.button_show_current_location);
        buttonShowCurrentLoc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isStartCurrentLocationSet) {
                    Toast toast = Toast.makeText(getApplicationContext(), "GPS not connected", Toast.LENGTH_SHORT);
                    View view = toast.getView();
                    view.setBackgroundColor(Color.DKGRAY);
                    TextView text = view.findViewById(android.R.id.message);
                    text.setTextColor(Color.WHITE);
                    toast.show();
                } else {
                    if (markerListDestination.size() > 0) {
                        LatLngBounds bounds;
                        LatLngBounds.Builder builder = new LatLngBounds.Builder();
                        builder.include(markerListSource.get(markerListSource.size() - 1).getPosition());
                        builder.include(markerListDestination.get(markerListDestination.size() - 1).getPosition());
                        bounds = builder.build();
                        int padding = 100; // offset from edges of the map in pixels
                        CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, padding);
                        mMap.animateCamera(cu);
                    } else {
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(globalCurrentLocation, 15.0f));
                    }
                }
            }
        });
        directions = findViewById(R.id.button_direction);
        directions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drawRoute(markerListDestination.get(markerListDestination.size() - 1));
            }
        });

        reset = findViewById(R.id.button_reset);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteOldRoute();
                createThreadGetForDublinBikes();
                for (Marker m : markerListDestination) {
                    m.remove();
                }
                markerListDestination = new ArrayList<>();
                if (globalCurrentLocation != null)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(globalCurrentLocation, 15.0f));
                directions.setVisibility(View.INVISIBLE);
                percentage.setText("");
            }
        });
    }


    private void initializePlaces() throws NullPointerException {
        Places.initialize(getApplicationContext(), API_KEY);
        PlacesClient placesClient = Places.createClient(this);
        // Initialize the AutocompleteSupportFragment.
        AutocompleteSupportFragment autocompleteFragment = (AutocompleteSupportFragment)
                getSupportFragmentManager().findFragmentById(R.id.autocomplete_fragment);

        // Specify the types of place data to return.
        assert autocompleteFragment != null;
        autocompleteFragment.setPlaceFields(Arrays.asList(Place.Field.ID, Place.Field.NAME));

        // Set up a PlaceSelectionListener to handle the response.
        autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(@NotNull Place place) {
                createThreadGetForLocation("https://maps.googleapis.com/maps/api/place/details/json?placeid=" +
                        place.getId() + "&key=" +
                        API_KEY);
            }

            @Override
            public void onError(@NotNull Status status) {
                // TODO: Handle the error.
                Log.i("TAG", "An error occurred: " + status);
            }
        });
    }

    private void createThreadGetForDublinBikes() throws NullPointerException {
        final String[] result = new String[1];
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    result[0] = getRestApi("https://api.jcdecaux.com/vls/v1/stations?contract=dublin&apiKey=9188e6afdd06025497417293fdea92c823034731");
                } finally {
                    latestBikesData = result[0];
                    array = new ArrayList<>();
                    try {
                        JSONArray standData = new JSONArray(latestBikesData);
                        for (int i = 0; i < standData.length(); i++) {
                            JSONObject object = standData.getJSONObject(i);
                            JSONObject position = object.getJSONObject("position");
                            BikesData bikesData = new BikesData(String.valueOf(object.get("address")), Double.parseDouble(String.valueOf(position.get("lng"))), Double.parseDouble(String.valueOf(position.get("lat"))), Integer.parseInt(String.valueOf(object.get("available_bikes"))), Integer.parseInt(String.valueOf(object.get("available_bike_stands"))), String.valueOf(object.get("status")), Boolean.valueOf(String.valueOf(object.get("banking"))), Integer.parseInt(String.valueOf(object.get("number"))));
                            array.add(bikesData);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    a.runOnUiThread(new Runnable() {
                        public void run() {
                            addStationMarkers();
                        }
                    });
                }
            }
        });
        thread.start();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) throws NullPointerException {
        mMap = googleMap;

        // Add a marker in Dublin and move the camera
        LatLng dublin = new LatLng(53.3498, -6.2603);
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(dublin, 15.0f));
        //Bike Stands Listener
        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                int currentIndex = (int) marker.getTag();
                if (currentIndex >= 0) {
                    clickValue++;
                    if (clickValue == 1) {
                        tagOnClick = currentIndex;
                        int index = (int) (marker.getTag());
                        if (globalCurrentLocation != null) {
                            String url = "https://maps.googleapis.com/maps/api/directions/json?" +
                                    "origin=" + globalCurrentLocation.latitude + "," + globalCurrentLocation.longitude +
                                    "&destination=" + array.get(index).getLat() + "," + array.get(index).getLng() +
                                    "&key=" + API_KEY +
                                    "&mode=walking";
                            deleteOldRoute();
                            createThreadGetForRoute(url, true);
                        } else {
                            Toast toast = Toast.makeText(getApplicationContext(), "GPS not connected", Toast.LENGTH_SHORT);
                            View view = toast.getView();
                            view.setBackgroundColor(Color.DKGRAY);
                            TextView text = view.findViewById(android.R.id.message);
                            text.setTextColor(Color.WHITE);
                            toast.show();
                        }
                    } else if (clickValue == 2) {
                        tagOnClick2 = currentIndex;
                        if (markerListDestination.size() > 0)
                            drawSpecificRoute(markerListDestination.get(markerListDestination.size() - 1), tagOnClick, tagOnClick2);
                        else
                            drawSpecificRoute(null, tagOnClick, tagOnClick2);
                        clickValue = 0;
                        tagOnClick = -3;
                        tagOnClick2 = -3;
                    } else {
                        clickValue = 0;
                    }
                    return false;
                } else {
                    if (currentIndex == -2) {
//                        drawRoute(marker);
                        //do nothing
                    } else if (currentIndex == -1) {
                        //do nothing
                    }
                    return false;
                }
            }
        });
        createDummyLocation();
    }

    private void drawSpecificRoute(Marker marker, int tagOnClick, int tagOnClick2) throws NullPointerException {
        //Getting First route
        if (globalCurrentLocation != null) {
            LatLng latLng = globalCurrentLocation;
            double lat = latLng.latitude;
            double lng = latLng.longitude;

            String url = "https://maps.googleapis.com/maps/api/directions/json?" +
                    "origin=" + lat + "," + lng +
                    "&destination=" + array.get(tagOnClick).getLat() + "," + array.get(tagOnClick).getLng() +
                    "&key=" + API_KEY +
                    "&mode=walking";
            deleteOldRoute();
            createThreadGetForRoute(url, true);

            //Getting Second route
            if (marker != null) {
                latLng = marker.getPosition();
                lat = latLng.latitude;
                lng = latLng.longitude;

                url = "https://maps.googleapis.com/maps/api/directions/json?" +
                        "destination=" + lat + "," + lng +
                        "&origin=" + array.get(tagOnClick2).getLat() + "," + array.get(tagOnClick2).getLng() +
                        "&key=" + API_KEY +
                        "&mode=walking";
                createThreadGetForRoute(url, true);
            }

            //Getting Third route
            url = "https://maps.googleapis.com/maps/api/directions/json?" +
                    "destination=" + array.get(tagOnClick2).getLat() + "," + array.get(tagOnClick2).getLng() +
                    "&origin=" + array.get(tagOnClick).getLat() + "," + array.get(tagOnClick).getLng() +
                    "&key=" + API_KEY +
                    "&mode=bicycling";
            createThreadGetForRoute(url, false);

            for (int i = 0; i < array.size(); i++) {
                if (i != tagOnClick && i != tagOnClick2) {
                    markerListStations.get(i).remove();
                } else {
                    Log.d("TAGG#", String.valueOf(clickValue) + "," + String.valueOf(i));
                }
            }
            JSONObject object = new JSONObject();
            try {
                object.put("user_id", String.valueOf(uniqueAndroidID));
                object.put("id", uniqueAndroidID + "-" + System.currentTimeMillis() / 1000);
                object.put("lat", String.valueOf(lat));
                object.put("lng", String.valueOf(lng));
                object.put("fromStand", String.valueOf(array.get(tagOnClick).getNumber()));
                object.put("toStand", String.valueOf(array.get(tagOnClick2).getNumber()));
                createThreadPostToAWS(awsLambdaAddress, object, array.get(tagOnClick).getNumber(), array.get(tagOnClick2).getNumber());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private void drawRoute(Marker marker) throws NullPointerException {
        //Getting First route
        LatLng latLng = globalCurrentLocation;
        double lat = latLng.latitude;
        double lng = latLng.longitude;
        double minDist = Double.MAX_VALUE;
        int minStationIndex = 0;
        int i2 = 0;
        for (BikesData b : array) {
            if (!b.getIsEmpty()) {
                double sLat = b.getLat();
                double sLng = b.getLng();
                double currentDist = Math.sqrt((lat - sLat) * (lat - sLat) + (lng - sLng) * (lng - sLng));
                if (currentDist < minDist) {
                    minDist = currentDist;
                    minStationIndex = i2;
                }
            }
            i2 -= -1;
        }

        String url = "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=" + lat + "," + lng +
                "&destination=" + array.get(minStationIndex).getLat() + "," + array.get(minStationIndex).getLng() +
                "&key=" + API_KEY +
                "&mode=walking";
        deleteOldRoute();
        createThreadGetForRoute(url, true);

        //Getting Second route
        latLng = marker.getPosition();
        lat = latLng.latitude;
        lng = latLng.longitude;
        minDist = Double.MAX_VALUE;
        int minStationIndex2 = 0;
        i2 = 0;
        for (BikesData b : array) {
            if (!b.getIsFull()) {
                double sLat = b.getLat();
                double sLng = b.getLng();
                double currentDist = Math.sqrt((lat - sLat) * (lat - sLat) + (lng - sLng) * (lng - sLng));
                if (currentDist < minDist) {
                    minDist = currentDist;
                    minStationIndex2 = i2;
                }
            }
            i2 -= -1;
        }
        url = "https://maps.googleapis.com/maps/api/directions/json?" +
                "destination=" + lat + "," + lng +
                "&origin=" + array.get(minStationIndex2).getLat() + "," + array.get(minStationIndex2).getLng() +
                "&key=" + API_KEY +
                "&mode=walking";
        createThreadGetForRoute(url, true);

        //Getting Third route
        url = "https://maps.googleapis.com/maps/api/directions/json?" +
                "destination=" + array.get(minStationIndex2).getLat() + "," + array.get(minStationIndex2).getLng() +
                "&origin=" + array.get(minStationIndex).getLat() + "," + array.get(minStationIndex).getLng() +
                "&key=" + API_KEY +
                "&mode=bicycling";
        createThreadGetForRoute(url, false);

        for (int i = 0; i < array.size(); i++) {
            if (i != minStationIndex && i != minStationIndex2) {
                markerListStations.get(i).remove();
            }
        }
        JSONObject object = new JSONObject();
        try {
            object.put("user_id", String.valueOf(uniqueAndroidID));
            object.put("id", uniqueAndroidID + "-" + System.currentTimeMillis() / 1000);
            object.put("lat", String.valueOf(lat));
            object.put("lng", String.valueOf(lng));
            object.put("fromStand", String.valueOf(array.get(minStationIndex).getNumber()));
            object.put("toStand", String.valueOf(array.get(minStationIndex2).getNumber()));
            createThreadPostToAWS(awsLambdaAddress, object, array.get(minStationIndex).getNumber(), array.get(minStationIndex2).getNumber());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void deleteOldRoute() throws NullPointerException {
        if (!previousRoute.equals("")) {
            for (Polyline p : polylines) {
                p.remove();
            }
            polylines.clear();
        }
    }

    private void addStationMarkers() throws NullPointerException {
        int index = 0;
        markerListStations = new ArrayList<>();
        for (BikesData b : array) {
            MarkerOptions markerOptions = new MarkerOptions().position(new LatLng(b.getLat(), b.getLng()));
            BitmapDrawable bitmapDrawable = null;
            if (b.getEmptyStandsCount() > 0 && b.getBikesCount() > 0) {
                bitmapDrawable = (BitmapDrawable) ContextCompat.getDrawable(getApplicationContext(), R.drawable.bikes_logo_50);
            } else if (b.getEmptyStandsCount() == 0) {
                bitmapDrawable = (BitmapDrawable) ContextCompat.getDrawable(getApplicationContext(), R.drawable.bikes_logo);
                b.setIsFull(true);
            } else if (b.getBikesCount() == 0) {
                bitmapDrawable = (BitmapDrawable) ContextCompat.getDrawable(getApplicationContext(), R.drawable.bikes_logo_grey);
                b.setIsEmpty(true);
            }
            assert bitmapDrawable != null;
            Bitmap bitmap = bitmapDrawable.getBitmap();
            Bitmap smallMarker = Bitmap.createScaledBitmap(bitmap, 100, 100, false);
            markerOptions.icon(BitmapDescriptorFactory.fromBitmap(smallMarker));
            Marker marker = mMap.addMarker(markerOptions);
            marker.setTitle(b.getAddress().toUpperCase());
            marker.setSnippet("Available Bikes: " + b.getBikesCount() +
                    "\nAvailable Stands: " + b.getEmptyStandsCount() +
                    "\nPayment terminal: " + (b.getIsBanking() ? "Yes" : "No") +
                    "");
            marker.setTag(index);
            index++;
            markerListStations.add(marker);
        }
        mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {

            @Override
            public View getInfoWindow(Marker arg0) {
                return null;
            }

            @Override
            public View getInfoContents(Marker marker) {

                LinearLayout info = new LinearLayout(MapsActivity.this);
                info.setOrientation(LinearLayout.VERTICAL);

                TextView title = new TextView(MapsActivity.this);
                title.setTextColor(Color.BLACK);
                title.setGravity(Gravity.CENTER);
                title.setTypeface(null, Typeface.BOLD);
                title.setText(marker.getTitle());

                TextView snippet = new TextView(MapsActivity.this);
                snippet.setTextColor(Color.GRAY);
                snippet.setText(marker.getSnippet());
                snippet.setGravity(Gravity.CENTER);
                info.addView(title);
                info.addView(snippet);

                return info;
            }
        });
    }

    private void createDummyLocation() throws NullPointerException {
        Random r = new Random();
        double lng = -6.310015 + r.nextDouble() * (-6.230852 + 6.310015);
        double lat = 53.330091 + r.nextDouble() * (53.359967 - 53.330091);
        LatLng currentLocation = new LatLng(lat, lng);
        globalCurrentLocation = currentLocation;
        Marker marker = mMap.addMarker(new MarkerOptions().position(currentLocation).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
        for (Marker m : markerListSource)
            m.remove();
        markerListSource.add(marker);
        marker.setTag(-1);
        marker.setTitle("USER LOCATION");
        if (!isStartCurrentLocationSet) {
            isStartCurrentLocationSet = true;
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15.0f));
        }
    }

    @Override
    public void onLocationChanged(Location location) throws NullPointerException {
        LatLng currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
        globalCurrentLocation = currentLocation;
        Marker marker = mMap.addMarker(new MarkerOptions().position(currentLocation).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
        for (Marker m : markerListSource)
            m.remove();
        markerListSource.add(marker);
        marker.setTag(-1);
        marker.setTitle("USER LOCATION");
        if (!isStartCurrentLocationSet) {
            isStartCurrentLocationSet = true;
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15.0f));
        }
    }

    @Override
    public void onProviderDisabled(String provider) throws NullPointerException {
    }

    @Override
    public void onProviderEnabled(String provider) throws NullPointerException {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) throws NullPointerException {
    }


    public void createThreadPostToAWS(final String url, final JSONObject object, final int start, final int end) throws NullPointerException {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    postRestApi(url, object);
                } finally {
                    createThreadGetFromAWS(url, start, end);
                }
            }
        });
        thread.start();
    }

    private void createThreadGetFromAWS(final String url, final int start, final int end) throws NullPointerException {
        final String[] response = new String[1];
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    response[0] = getRestApi(url);
                } finally {
                    try {
                        JSONArray jsonArray = new JSONArray(response[0]);
                        JSONObject jsonObject;

                        int totalCount = jsonArray.length();
                        int currentCount = 0;
                        for (int i = 0; i < jsonArray.length(); i -= -1) {
                            jsonObject = jsonArray.getJSONObject(i);
                            int local_start =  Integer.parseInt(String.valueOf(jsonObject.get("fromStand")));
                            int local_end = Integer.parseInt(String.valueOf(jsonObject.get("toStand")));
                            if (local_end == end && local_start == start) {
                                currentCount++;
                            }
                        }
                        final double percent = 100 * currentCount / totalCount;
                        DecimalFormat numberFormat = new DecimalFormat("#.00");
                        final String p = numberFormat.format(percent);
                        a.runOnUiThread(new Runnable() {
                            public void run() {
                                percentage.setText("Route popularity: " + p + "%");
                            }
                        });
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        thread.start();
    }

    private void postRestApi(String url, JSONObject object) throws NullPointerException {
        final MediaType JSON = MediaType.get("application/json; charset=utf-8");
        OkHttpClient client = new OkHttpClient();
        RequestBody body = RequestBody.create(object.toString(), JSON);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();
        try {
            client.newCall(request).execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void createThreadGetForRoute(final String url, final boolean isWalking) throws NullPointerException {
        final String[] result = new String[1];
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    result[0] = getRestApi(url);
                } finally {
//                    Log.d("response", result[0]);
                    previousRoute = result[0];
                    a.runOnUiThread(new Runnable() {
                        public void run() {
                            renderRoute(result[0], isWalking);
                        }
                    });
                }
            }
        });
        thread.start();
    }

    public void createThreadGetForLocation(final String url) throws NullPointerException {
        final String[] result = new String[1];
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    result[0] = getRestApi(url);
                } finally {
                    try {
                        JSONObject jsonObject = new JSONObject(result[0]);
                        String lat = jsonObject.getJSONObject("result").getJSONObject("geometry").getJSONObject("location").getString("lat");
                        String lng = jsonObject.getJSONObject("result").getJSONObject("geometry").getJSONObject("location").getString("lng");
                        final LatLng finalLocation = new LatLng(Double.parseDouble(lat), Double.parseDouble(lng));
//                        globalCurrentLocation = currentLocation;
                        a.runOnUiThread(new Runnable() {
                            public void run() {
                                Marker marker = mMap.addMarker(new MarkerOptions().position(finalLocation));
                                for (Marker m : markerListDestination)
                                    m.remove();
                                markerListDestination.add(marker);
                                marker.setTag(-2);
                                marker.setTitle("DESTINATION");
                                LatLngBounds bounds;
                                LatLngBounds.Builder builder = new LatLngBounds.Builder();
                                builder.include(markerListSource.get(markerListSource.size() - 1).getPosition());
                                builder.include(markerListDestination.get(markerListDestination.size() - 1).getPosition());
                                bounds = builder.build();
                                int padding = 100; // offset from edges of the map in pixels
                                CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, padding);
                                mMap.animateCamera(cu);
                                directions.setVisibility(View.VISIBLE);
                            }
                        });

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                }
            }
        });
        thread.start();
    }

    private void renderRoute(String jsonData, boolean isWalking) throws NullPointerException {
        JSONObject jObject;
        List<List<HashMap<String, String>>> routes = null;

        try {
            jObject = new JSONObject(jsonData);
            PathJSONParser parser = new PathJSONParser();
            routes = parser.parse(jObject);
        } catch (Exception e) {
            e.printStackTrace();
        }
        ArrayList<LatLng> points;
        PolylineOptions polyLineOptions = null;

        // traversing through routes
        assert routes != null;
        for (int i = 0; i < routes.size(); i++) {
            points = new ArrayList<>();
            polyLineOptions = new PolylineOptions();
            List<HashMap<String, String>> path = routes.get(i);

            for (int j = 0; j < path.size(); j++) {
                HashMap<String, String> point = path.get(j);

                double lat = Double.parseDouble(Objects.requireNonNull(point.get("lat")));
                double lng = Double.parseDouble(Objects.requireNonNull(point.get("lng")));
                LatLng position = new LatLng(lat, lng);

                points.add(position);
            }

            polyLineOptions.addAll(points);
            polyLineOptions.width(15);
            if (isWalking) {
                polyLineOptions.width(25);
                polyLineOptions.pattern(Arrays.asList(new Dot(), new Gap(20)));
            }
            polyLineOptions.color(Color.BLUE);
        }
        polylines.add(mMap.addPolyline(polyLineOptions));
    }

    public String getRestApi(String url) throws NullPointerException {

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .build();
        try {
            Response response = client.newCall(request).execute();
            return Objects.requireNonNull(response.body()).string();
        } catch (IOException e) {
            e.printStackTrace();
            return "IO-Error";
        }
    }

}


class BikesData {
    private String address;
    private double lng;
    private double lat;
    private int bikesCount;
    private int emptyStandsCount;
    private String status;
    private boolean banking;
    private int number;
    private boolean isEmpty = false;
    private boolean isFull = false;

    public boolean getIsEmpty() {
        return isEmpty;
    }

    public void setIsEmpty(boolean empty) {
        isEmpty = empty;
    }

    public boolean getIsFull() {
        return isFull;
    }

    public void setIsFull(boolean full) {
        isFull = full;
    }

    public BikesData(String address, double lng, double lat, int bikesCount, int emptyStandsCount, String status, boolean banking, int number) {
        this.address = address;
        this.lng = lng;
        this.lat = lat;
        this.bikesCount = bikesCount;
        this.emptyStandsCount = emptyStandsCount;
        this.status = status;
        this.banking = banking;
        this.number = number;
    }

    public String getAddress() {
        return address;
    }

    public double getLng() {
        return lng;
    }

    public double getLat() {
        return lat;
    }

    public int getBikesCount() {
        return bikesCount;
    }

    public int getEmptyStandsCount() {
        return emptyStandsCount;
    }

    public String getStatus() {
        return status;
    }

    public boolean getIsBanking() {
        return banking;
    }

    public int getNumber() {
        return number;
    }
}

// ask user for location