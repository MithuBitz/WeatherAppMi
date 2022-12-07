package com.mibtech.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

import android.location.Location
import android.location.LocationManager

import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings

import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog


import com.google.android.gms.location.*
import com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.mibtech.weatherapp.databinding.ActivityMainBinding
import com.mibtech.weatherapp.models.WeatherResponse
import com.mibtech.weatherapp.network.WeatherServices
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    private var binding: ActivityMainBinding? = null
    private lateinit var progressDialog: Dialog

    //required variable to get the latitude and longitude of current location
    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        if (!isLocationEnabled()) {
            Toast.makeText(this, "Please turned on your Location Provider", Toast.LENGTH_LONG)
                .show()
            //This will bring the location settings page directly via intent
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            Dexter.withContext(this)
                .withPermissions(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        report?.let {
                            if (report.areAllPermissionsGranted()) {
                                requestLocationData()
                            }
                            if (report.isAnyPermissionPermanentlyDenied) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "You have denied the location permission",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permission: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        showRationalDialogForPermission()
                    }

                }).onSameThread().check()
        }
    }

    //This fun is used to see whether the Location provider is on or not
    private fun isLocationEnabled(): Boolean {
        //This provide access to the location service
        val locationManger: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager

        return locationManger.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManger.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showRationalDialogForPermission() {
        AlertDialog.Builder(this)
            .setMessage("You dont grant permission which are required for this app")
            .setPositiveButton("Go To Settings") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    //This is brings to the permission settings for this app only
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    private val mLoactionCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location? = locationResult.lastLocation
            val latitude = mLastLocation?.latitude
            Log.i("Current Latitude: ", "$latitude")
            val longitude = mLastLocation?.longitude
            Log.i("Current Longitude: ", "$longitude")

            getLocationWeatherDetails(latitude!!, longitude!!)

        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData() {
        val mLocationRequest = LocationRequest.create().apply {
            priority = PRIORITY_HIGH_ACCURACY
            interval = 1000
            numUpdates = 1
        }

        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest,
            mLoactionCallback,
            Looper.myLooper()
        )

    }

    private fun getLocationWeatherDetails(latitude: Double, longitude: Double){
        if(Constants.isNetworkAvailable(this)){
            //When network is availabe then Retrofit call for api to prepare
            val retrofit: Retrofit = Retrofit.Builder().baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            //Create or prepare a service which is based on retrofit
            val service: WeatherServices = retrofit.create<WeatherServices>(WeatherServices::class.java)

            val listCall: Call<WeatherResponse> = service.getWeather(latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID)
            //Show progress dialog when retrofit feching the json result
            showProgressDialog()

            listCall.enqueue(object : Callback<WeatherResponse>{
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if (response.isSuccessful){
                        cancelProgressDialog()
                        val weatherList: WeatherResponse? = response.body()
                        setUpUI(weatherList!!)
                        Log.i("Response Result: ", "$weatherList")
                    } else {
                        val rc = response.code()
                        when(rc) {
                            400 -> {
                                Log.i("Error 400", "Bad Connection")
                            }
                            404 -> {
                                Log.i("Error 404", "Not Found")
                            }
                            else -> {
                                Log.i("Error", "Generic Error")
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Log.i("Opps!", t!!.message.toString())
                    cancelProgressDialog()
                }

            })

        }else {
            Toast.makeText(this, "Opps! you dont have Internet", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }

    private fun showProgressDialog(){
        progressDialog = Dialog(this@MainActivity)
        progressDialog.setContentView(R.layout.custom_progress_dialog)
        progressDialog.show()
    }

    private fun cancelProgressDialog(){
        if (progressDialog != null) {
            progressDialog.dismiss()
        }
    }

    //Setup the UI according to the weather response

    private fun setUpUI(weatherList: WeatherResponse){
        for(i in weatherList.weather.indices){
            Log.i("Weather Name: ", weatherList.weather.toString())

            binding?.tvMain?.text = weatherList.weather[i].main
            binding?.tvMainDescription?.text = weatherList.weather[i].description

            binding?.tvTemp?.text = "${weatherList.main.temp} ${setUnitsAccordingToCountryCode(weatherList.sys.country)}"
            binding?.tvHumidity?.text = weatherList.main.humidity.toString() + "%"

            binding?.tvSunriseTime?.text = unixTimer(weatherList.sys.sunrise)
            binding?.tvSunsetTime?.text = unixTimer(weatherList.sys.sunset)

            binding?.tvMin?.text = weatherList.main.temp_min.toString() + " min"
            binding?.tvMax?.text = weatherList.main.temp_max.toString() + " max"

            binding?.tvSpeed?.text = weatherList.wind.speed.toString()
            //binding?.tvSpeedUnit?.text

            binding?.tvName?.text = weatherList.name
            binding?.tvCountry?.text = weatherList.sys.country

            when(weatherList.weather[i].icon){
                "02n", "02d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "10d", "10n" -> binding?.ivMain?.setImageResource(R.drawable.rain)
                "11d", "11n" -> binding?.ivMain?.setImageResource(R.drawable.storm)
                "13d", "13n" -> binding?.ivMain?.setImageResource(R.drawable.snowflake)
                "01d", "01n" -> binding?.ivMain?.setImageResource(R.drawable.sunny)
            }
        }
    }

    //Function to help whether it is cellcius or farenhite
    private fun setUnitsAccordingToCountryCode(countryCode: String): String? {
        // Fahrenheit if US, Liberia, or Myanmar. Centigrade for the rest of the world
        return if (countryCode == "US" || countryCode == "LR" || countryCode == "MM") {
            "°F"
        } else {
            "°C"
        }
    }

    //Set sunrise and sunset time to human recognizable time format
    private fun unixTimer(timex: Long): String {
        val date = Date(timex * 1000L)
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

}