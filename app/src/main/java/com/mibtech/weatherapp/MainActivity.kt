package com.mibtech.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

import android.location.Location
import android.location.LocationManager

import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings

import android.util.Log
import android.widget.Toast
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


class MainActivity : AppCompatActivity() {

    private var binding: ActivityMainBinding? = null

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

            listCall.enqueue(object : Callback<WeatherResponse>{
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if (response.isSuccessful){
                        val weatherList: WeatherResponse? = response.body()
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
}