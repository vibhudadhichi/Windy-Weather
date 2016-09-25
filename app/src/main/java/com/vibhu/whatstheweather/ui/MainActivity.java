package com.vibhu.whatstheweather.ui;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.daimajia.androidanimations.library.Techniques;
import com.daimajia.androidanimations.library.YoYo;
import com.vibhu.whatstheweather.R;
import com.vibhu.whatstheweather.datasource.WeatherFromForecastIO;
import com.vibhu.whatstheweather.datasource.WeatherSource;
import com.vibhu.whatstheweather.datasource.WeatherSourceCallback;
import com.vibhu.whatstheweather.weather.Current;
import com.vibhu.whatstheweather.weather.Forecast;

import java.util.List;
import java.util.Locale;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import io.nlopez.smartlocation.OnLocationUpdatedListener;
import io.nlopez.smartlocation.SmartLocation;
import io.nlopez.smartlocation.location.config.LocationParams;
import io.nlopez.smartlocation.location.providers.LocationGooglePlayServicesWithFallbackProvider;


public class MainActivity extends AppCompatActivity implements WeatherSourceCallback {
    public static final String TAG = MainActivity.class.getSimpleName();
    public static final String DAILY_FORECAST = "DAILY_FORECAST";
    public static final String HOURLY_FORECAST = "HOURLY_FORECAST";
    public static final String LOCATION_NAME = "LOCATION_NAME";
    private final WeatherSource mWeatherSource = new WeatherFromForecastIO(this);

    private Forecast mForecast;
    @InjectView(R.id.locationLabel) TextView mLocationLabel;
    @InjectView(R.id.timeLabel) TextView mTimeLabel;
    @InjectView(R.id.temperatureLabel) TextView mTemperatureLabel;
    @InjectView(R.id.humidityValue) TextView mHumidityValue;
    @InjectView(R.id.precipValue) TextView mPrecipValue;
    @InjectView(R.id.summaryLabel) TextView mSummaryLabel;
    @InjectView(R.id.iconImageView) ImageView mIconImageView;
    @InjectView(R.id.refreshImageView) ImageView mRefreshImageView;
    @InjectView(R.id.progressBar) ProgressBar mProgressBar;
    private double mLatitude = 20.5937;
    private double mLongitude = 78.9629;
    private String mLocationName = "India";


    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.inject(this);
        mProgressBar.setVisibility(View.INVISIBLE);
        if(mLatitude == 20.5937)
            Toast.makeText(MainActivity.this,"Please switch on your location and refresh",Toast.LENGTH_LONG).show();

        YoYo.with(Techniques.Shake)
                .duration(3500)
                .playOn(findViewById(R.id.temperatureLabel));

        refreshForecast(mRefreshImageView);
    }

    @Override
    protected void onResume() {
        super.onResume();

        //start location service
        SmartLocation
                .with(this)
                .location()
                .provider(new LocationGooglePlayServicesWithFallbackProvider(this))
                .config(LocationParams.BEST_EFFORT)
                .start(new OnLocationUpdatedListener() {
                    @Override
                    public void onLocationUpdated(Location location) {
                        mLatitude = location.getLatitude();
                        mLongitude = location.getLongitude();



                        mLocationName = getLocationName(mLatitude, mLongitude);
                        // Load forecast only if it hasn't been loaded before (ie. showing
                        // placeholder text); otherwise wait for refresh button.
                        if (mTemperatureLabel.getText().toString().equals(getString(R.string.temperature_loading))) {
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    refreshForecast(mRefreshImageView);
                                }
                            });
                        }
                    }
                });

        YoYo.with(Techniques.Shake)
                .duration(3500)
                .playOn(findViewById(R.id.temperatureLabel));

    }

    @Override
    protected void onPause() {
        super.onPause();
        SmartLocation.with(this).location().stop();
    }

    public void toggleRefresh() {
        if (mProgressBar.getVisibility() == View.INVISIBLE) {
            mProgressBar.setVisibility(View.VISIBLE);
            mRefreshImageView.setVisibility(View.INVISIBLE);
        } else {
            mProgressBar.setVisibility(View.INVISIBLE);
            mRefreshImageView.setVisibility(View.VISIBLE);
        }
        YoYo.with(Techniques.Shake)
                .duration(3500)
                .playOn(findViewById(R.id.temperatureLabel));
    }

    public void updateDisplay() {
        Current current = mForecast.getCurrent();
        mTemperatureLabel.setText(current.getTemperature() + "");
        mTimeLabel.setText("At " + current.getFormattedTime() + " it will be");
        mHumidityValue.setText(current.getHumidity() + "%");
        mPrecipValue.setText(current.getPrecipChance() + "%");
        mSummaryLabel.setText(current.getSummary());
        Drawable drawable = getResources().getDrawable((int) current.getIconId());
        mIconImageView.setImageDrawable(drawable);
        mLocationLabel.setText(mLocationName);
    }

    public void alertUserAboutError(){
        AlertDialogFragment dialog = new AlertDialogFragment();
        dialog.show(getFragmentManager(), "error_dialog");
    }

    @OnClick(R.id.refreshImageView)
    public void refreshForecast(View v) {
        mLocationLabel.setText(mLocationName);

        if (isNetworkAvailable()) {
            toggleRefresh();
            mWeatherSource.getForecast(mLatitude, mLongitude);
        } else {
            Toast.makeText(this, getString(R.string.network_unavailable), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onSuccess(Forecast forecast) {
        mForecast = forecast;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                toggleRefresh();
                updateDisplay();
            }
        });
    }

    @Override
    public void onFailure(Exception e) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                toggleRefresh();
                alertUserAboutError();
            }
        });
    }

    /**
     * Get the name of the city at the given map coordinates.
     *
     * @param latitude Latitude of the location.
     * @param longitude Longitude of the location.
     * @return The localized name of the city.  If a geocoder isn't implemented on the device,
     * returns "Not Available". If the geocoder is implemented but fails to get an address,
     * returns "Not Found".
     */
    public String getLocationName(final double latitude, final double longitude)  {

        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    Geocoder geocoder = new Geocoder(getApplicationContext(), Locale.ENGLISH);
                    try {
                        List<Address> geoResults = geocoder.getFromLocation(latitude,longitude,1);
                        while (geoResults.size()==0) {
                            geoResults = geocoder.getFromLocation(latitude,longitude,1);
                            Log.i("Looping","0");
                        }
                        if (geoResults.size()>0) {
                            Address addr = geoResults.get(0);
                            mLocationName = addr.getLocality();
                            Log.i("City",mLocationName);
                        }
                    } catch (Exception e) {
                        Log.i("Here","Here");
                    }


                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        thread.start();


        return mLocationName;
    }

    @OnClick(R.id.dailyButton)
    public void startDailyActivity(View view) {
        if (mForecast != null) {
            Intent intent = new Intent(this, DailyForecastActivity.class);
            intent.putExtra(DAILY_FORECAST, mForecast.getDailyForecast());
            intent.putExtra(LOCATION_NAME, mLocationName);
            startActivity(intent);
        }
    }

    @OnClick(R.id.hourlyButton)
    public void startHourlyActivity(View view){
        if (mForecast != null) {
            Intent intent = new Intent(this, HourlyForecastActivity.class);
            intent.putExtra(HOURLY_FORECAST, mForecast.getHourlyForecast());
            startActivity(intent);
        }
    }

    public boolean isNetworkAvailable() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = manager.getActiveNetworkInfo();
        boolean isAvailable = false;
        if (networkInfo != null && networkInfo.isConnected()) {
            isAvailable = true;
        }
        return isAvailable;
    }
}
