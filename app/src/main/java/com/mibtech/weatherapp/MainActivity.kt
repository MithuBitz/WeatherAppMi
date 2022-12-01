package com.mibtech.weatherapp

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.core.location.LocationManagerCompat.isLocationEnabled
import com.mibtech.weatherapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private var binding: ActivityMainBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        if (!isLocationEnabled()){
            Toast.makeText(this, "Please turned on your Location Provider", Toast.LENGTH_LONG).show()
            //This will bring the location settings page directly via intent
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            Toast.makeText(this, "Your Location Provider is working", Toast.LENGTH_LONG).show()
        }
    }

    //This fun is used to see whether the Location provider is on or not
    private fun isLocationEnabled(): Boolean {
        //This provide access to the location service
        val locationManger: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        return locationManger.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManger.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }
}