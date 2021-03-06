package com.thewizrd.simpleweather.setup;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.support.wearable.view.AcceptDenyDialog;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.wear.widget.drawer.WearableDrawerLayout;
import androidx.wear.widget.drawer.WearableDrawerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.tasks.CancellationToken;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.thewizrd.shared_resources.AsyncTask;
import com.thewizrd.shared_resources.Constants;
import com.thewizrd.shared_resources.controls.LocationQueryViewModel;
import com.thewizrd.shared_resources.locationdata.LocationData;
import com.thewizrd.shared_resources.utils.CommonActions;
import com.thewizrd.shared_resources.utils.JSONParser;
import com.thewizrd.shared_resources.utils.Logger;
import com.thewizrd.shared_resources.utils.Settings;
import com.thewizrd.shared_resources.utils.StringUtils;
import com.thewizrd.shared_resources.utils.WeatherException;
import com.thewizrd.shared_resources.wearable.WearableDataSync;
import com.thewizrd.shared_resources.wearable.WearableHelper;
import com.thewizrd.shared_resources.weatherdata.Forecasts;
import com.thewizrd.shared_resources.weatherdata.HourlyForecast;
import com.thewizrd.shared_resources.weatherdata.HourlyForecasts;
import com.thewizrd.shared_resources.weatherdata.Weather;
import com.thewizrd.shared_resources.weatherdata.WeatherAPI;
import com.thewizrd.shared_resources.weatherdata.WeatherManager;
import com.thewizrd.simpleweather.R;
import com.thewizrd.simpleweather.databinding.ActivitySetupBinding;
import com.thewizrd.simpleweather.fragments.LocationSearchFragment;
import com.thewizrd.simpleweather.helpers.AcceptDenyDialogBuilder;
import com.thewizrd.simpleweather.main.MainActivity;
import com.thewizrd.simpleweather.preferences.SettingsActivity;

import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class SetupActivity extends FragmentActivity implements MenuItem.OnMenuItemClickListener {

    private ActivitySetupBinding binding;
    
    private FusedLocationProviderClient mFusedLocationClient;
    private Location mLocation;
    private LocationCallback mLocCallback;
    private LocationListener mLocListnr;
    private static final int PERMISSION_LOCATION_REQUEST_CODE = 0;

    /**
     * Tracks the status of the location updates request.
     */
    private boolean mRequestingLocationUpdates;

    private CancellationTokenSource cts;

    private WeatherManager wm;

    private static final int REQUEST_CODE_SYNC_ACTIVITY = 10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivitySetupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        wm = WeatherManager.getInstance();

        // Set default API to HERE
        if (!Settings.isWeatherLoaded()) {
            Settings.setAPI(WeatherAPI.HERE);
            wm.updateAPI();

            if (wm.isKeyRequired() && StringUtils.isNullOrWhitespace(wm.getAPIKey())) {
                // If (internal) key doesn't exist, fallback to Yahoo
                Settings.setAPI(WeatherAPI.YAHOO);
                wm.updateAPI();
                Settings.setPersonalKey(true);
                Settings.setKeyVerified(false);
            } else {
                // If key exists, go ahead
                Settings.setPersonalKey(false);
                Settings.setKeyVerified(true);
            }
        }

        // Controls
        binding.searchButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.search_fragment_container, new LocationSearchFragment())
                        .commit();

                binding.bottomActionDrawer.getController().closeDrawer();
                binding.bottomActionDrawer.setIsLocked(true);
            }
        });
        binding.locationButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                fetchGeoLocation();
            }
        });

        binding.activitySetup.setDrawerStateCallback(new WearableDrawerLayout.DrawerStateCallback() {
            @Override
            public void onDrawerOpened(WearableDrawerLayout layout, WearableDrawerView drawerView) {
                super.onDrawerOpened(layout, drawerView);
                drawerView.requestFocus();
            }

            @Override
            public void onDrawerClosed(WearableDrawerLayout layout, WearableDrawerView drawerView) {
                super.onDrawerClosed(layout, drawerView);
                drawerView.clearFocus();
            }
        });

        binding.bottomActionDrawer.setOnMenuItemClickListener(this);
        binding.bottomActionDrawer.setLockedWhenClosed(true);
        binding.bottomActionDrawer.setPeekOnScrollDownEnabled(true);
        binding.bottomActionDrawer.getController().peekDrawer();
        binding.progressBar.setVisibility(View.GONE);

        if (WearableHelper.isGooglePlayServicesInstalled()) {
            mFusedLocationClient = new FusedLocationProviderClient(this);
            mLocCallback = new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    if (locationResult == null)
                        mLocation = null;
                    else
                        mLocation = locationResult.getLastLocation();

                    if (mLocation == null) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                enableControls(true);
                                Toast.makeText(SetupActivity.this, R.string.error_retrieve_location, Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        fetchGeoLocation();
                    }

                    stopLocationUpdates();
                }

                @Override
                public void onLocationAvailability(LocationAvailability locationAvailability) {
                    new AsyncTask<Void>().await(new Callable<Void>() {
                        @Override
                        public Void call() {
                            try {
                                return Tasks.await(mFusedLocationClient.flushLocations());
                            } catch (ExecutionException | InterruptedException e) {
                                Logger.writeLine(Log.ERROR, e);
                            }

                            return null;
                        }
                    });

                    if (!locationAvailability.isLocationAvailable()) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                stopLocationUpdates();
                                enableControls(true);
                                Toast.makeText(SetupActivity.this, R.string.error_retrieve_location, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            };
        } else {
            mLocListnr = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    mLocation = location;
                    fetchGeoLocation();
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {

                }

                @Override
                public void onProviderEnabled(String provider) {

                }

                @Override
                public void onProviderDisabled(String provider) {

                }
            };
        }

        mRequestingLocationUpdates = false;
    }

    /**
     * Removes location updates from the FusedLocationApi.
     */
    private void stopLocationUpdates() {
        if (!mRequestingLocationUpdates) {
            Logger.writeLine(Log.DEBUG, "SetupActivity: stopLocationUpdates: updates never requested, no-op.");
            return;
        }

        // It is a good practice to remove location requests when the activity is in a paused or
        // stopped state. Doing so helps battery performance and is especially
        // recommended in applications that request frequent location updates.
        mFusedLocationClient.removeLocationUpdates(mLocCallback)
                .addOnCompleteListener(this, new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        mRequestingLocationUpdates = false;
                    }
                });
    }

    private void ctsCancel() {
        if (cts != null) cts.cancel();
        cts = new CancellationTokenSource();
    }

    @Override
    protected void onPause() {
        ctsCancel();
        super.onPause();
        // Remove location updates to save battery.
        stopLocationUpdates();
    }

    @Override
    protected void onDestroy() {
        ctsCancel();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (getSupportFragmentManager().findFragmentById(R.id.search_fragment_container) instanceof LocationSearchFragment) {
            LocationSearchFragment mSearchFragment = (LocationSearchFragment) getSupportFragmentManager().findFragmentById(R.id.search_fragment_container);
            mSearchFragment.setUserVisibleHint(false);

            getSupportFragmentManager().beginTransaction()
                    .remove(mSearchFragment)
                    .commitAllowingStateLoss();

            enableControls(true);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                break;
            case R.id.menu_setupfromphone:
                AcceptDenyDialog alertBuilder = new AcceptDenyDialogBuilder(this, new AcceptDenyDialog.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == DialogInterface.BUTTON_POSITIVE) {
                            startActivityForResult(new Intent(SetupActivity.this, SetupSyncActivity.class), REQUEST_CODE_SYNC_ACTIVITY);
                        }
                    }
                }).setMessage(R.string.prompt_confirmsetup)
                        .show();
                break;
            default:
                break;
        }

        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_CODE_SYNC_ACTIVITY:
                if (resultCode == RESULT_OK) {
                    if (Settings.getHomeData() != null) {
                        Settings.setDataSync(WearableDataSync.DEVICEONLY);
                        Settings.setWeatherLoaded(true);
                        // Start WeatherNow Activity
                        startActivity(new Intent(this, MainActivity.class));
                        finishAffinity();
                    }
                }
                break;
            default:
                break;
        }
    }

    private void enableControls(final boolean enable) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                binding.searchButton.setEnabled(enable);
                binding.locationButton.setEnabled(enable);
                if (enable) {
                    binding.bottomActionDrawer.getController().peekDrawer();
                    binding.progressBar.setVisibility(View.GONE);
                } else {
                    binding.bottomActionDrawer.getController().closeDrawer();
                    binding.bottomActionDrawer.setIsLocked(true);
                    binding.progressBar.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void fetchGeoLocation() {
        binding.locationButton.setEnabled(false);

        // Show loading bar
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                enableControls(false);
            }
        });

        AsyncTask.run(new Runnable() {
            @Override
            public void run() {
                try {
                    if (mLocation != null) {
                        LocationQueryViewModel view = null;

                        // Cancel other tasks
                        ctsCancel();
                        CancellationToken ctsToken = cts.getToken();

                        if (ctsToken.isCancellationRequested()) throw new InterruptedException();

                        // Show loading bar
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                enableControls(false);
                            }
                        });

                        if (ctsToken.isCancellationRequested()) throw new InterruptedException();

                        view = wm.getLocation(mLocation);

                        if (StringUtils.isNullOrWhitespace(view.getLocationQuery()))
                            view = new LocationQueryViewModel();

                        if (StringUtils.isNullOrWhitespace(view.getLocationQuery())) {
                            enableControls(true);
                            return;
                        }

                        if (Settings.usePersonalKey() && StringUtils.isNullOrWhitespace(Settings.getAPIKEY()) && wm.isKeyRequired()) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(getApplicationContext(), R.string.werror_invalidkey, Toast.LENGTH_SHORT).show();
                                }
                            });
                            enableControls(true);
                            return;
                        }

                        if (ctsToken.isCancellationRequested()) throw new InterruptedException();

                        String country_code = view.getLocationCountry();
                        if (!StringUtils.isNullOrWhitespace(country_code))
                            country_code = country_code.toLowerCase();

                        if (WeatherAPI.NWS.equals(Settings.getAPI()) && !("usa".equals(country_code) || "us".equals(country_code))) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(getApplicationContext(), R.string.error_message_weather_us_only, Toast.LENGTH_SHORT).show();
                                }
                            });
                            enableControls(true);
                            return;
                        }

                        // Get Weather Data
                        LocationData location = new LocationData(view, mLocation);
                        if (!location.isValid()) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(getApplicationContext(), getString(R.string.werror_noweather), Toast.LENGTH_SHORT).show();
                                }
                            });
                            enableControls(true);
                            return;
                        }

                        if (ctsToken.isCancellationRequested()) throw new InterruptedException();

                        Weather weather = Settings.getWeatherData(location.getQuery());
                        if (weather == null) {
                            if (ctsToken.isCancellationRequested())
                                throw new InterruptedException();

                            try {
                                TaskCompletionSource<Weather> tcs = new TaskCompletionSource<>(ctsToken);
                                tcs.setResult(wm.getWeather(location));
                                weather = Tasks.await(tcs.getTask());
                            } catch (final WeatherException wEx) {
                                weather = null;
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(getApplicationContext(), wEx.getMessage(), Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        }

                        if (weather == null) {
                            enableControls(true);
                            return;
                        }

                        if (ctsToken.isCancellationRequested()) throw new InterruptedException();

                        // We got our data so disable controls just in case
                        enableControls(false);

                        // Save weather data
                        Settings.saveHomeData(location);
                        if (wm.supportsAlerts() && weather.getWeatherAlerts() != null)
                            Settings.saveWeatherAlerts(location, weather.getWeatherAlerts());
                        Settings.saveWeatherData(weather);
                        Settings.saveWeatherForecasts(new Forecasts(weather.getQuery(), weather.getForecast(), weather.getTxtForecast()));
                        final Weather finalWeather = weather;
                        Settings.saveWeatherForecasts(location.getQuery(), weather.getHrForecast() == null ? null :
                                Collections2.transform(weather.getHrForecast(), new Function<HourlyForecast, HourlyForecasts>() {
                                    @NullableDecl
                                    @Override
                                    public HourlyForecasts apply(@NullableDecl HourlyForecast input) {
                                        return new HourlyForecasts(finalWeather.getQuery(), input);
                                    }
                                }));

                        // If we're changing locations, trigger an update
                        if (Settings.isWeatherLoaded()) {
                            LocalBroadcastManager.getInstance(getApplicationContext())
                                    .sendBroadcast(new Intent(CommonActions.ACTION_WEATHER_SENDLOCATIONUPDATE));
                        }

                        Settings.setFollowGPS(true);
                        Settings.setWeatherLoaded(true);

                        // Start WeatherNow Activity with weather data
                        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                        intent.putExtra(Constants.KEY_DATA, JSONParser.serializer(location, LocationData.class));

                        startActivity(intent);
                        finishAffinity();
                    } else {
                        updateLocation();
                    }
                } catch (Exception e) {
                    // Restore controls
                    enableControls(true);
                    Settings.setFollowGPS(false);
                    Settings.setWeatherLoaded(false);
                }
            }
        });
    }

    private void updateLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSION_LOCATION_REQUEST_CODE);
            return;
        }

        Location location = null;

        LocationManager locMan = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (locMan == null || !LocationManagerCompat.isLocationEnabled(locMan)) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(SetupActivity.this, R.string.error_enable_location_services, Toast.LENGTH_LONG).show();
                }
            });

            // Disable GPS feature if location is not enabled
            enableControls(true);
            Settings.setFollowGPS(false);
            return;
        }

        if (WearableHelper.isGooglePlayServicesInstalled()) {
            location = new AsyncTask<Location>().await(new Callable<Location>() {
                @SuppressLint("MissingPermission")
                @Override
                public Location call() {
                    Location result = null;
                    try {
                        result = Tasks.await(mFusedLocationClient.getLastLocation(), 10, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        Logger.writeLine(Log.ERROR, e);
                    }
                    return result;
                }
            });

            /**
             * Request start of location updates. Does nothing if
             * updates have already been requested.
             */
            if (location == null && !mRequestingLocationUpdates) {
                final LocationRequest mLocationRequest = new LocationRequest();
                mLocationRequest.setNumUpdates(1);
                mLocationRequest.setInterval(10000);
                mLocationRequest.setFastestInterval(1000);
                mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
                mRequestingLocationUpdates = true;
                mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocCallback, Looper.getMainLooper());
            }
        } else {
            boolean isGPSEnabled = locMan.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean isNetEnabled = locMan.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            if (isGPSEnabled) {
                location = locMan.getLastKnownLocation(LocationManager.GPS_PROVIDER);

                if (location == null)
                    location = locMan.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

                if (location == null)
                    locMan.requestSingleUpdate(LocationManager.GPS_PROVIDER, mLocListnr, Looper.getMainLooper());
            } else if (isNetEnabled) {
                location = locMan.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

                if (location == null)
                    locMan.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, mLocListnr, Looper.getMainLooper());
            } else {
                enableControls(true);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(SetupActivity.this, R.string.error_retrieve_location, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }

        if (location != null && !mRequestingLocationUpdates) {
            mLocation = location;
            fetchGeoLocation();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_LOCATION_REQUEST_CODE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay!
                    // Do the task you need to do.
                    fetchGeoLocation();
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    enableControls(true);
                    Toast.makeText(this, R.string.error_location_denied, Toast.LENGTH_SHORT).show();
                }
                return;
            }
        }
    }
}