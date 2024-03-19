package com.example.yadihe_springbreakchooser

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.ComponentActivity
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.Manifest
import android.media.MediaPlayer

import androidx.activity.viewModels
import androidx.core.content.ContextCompat

import androidx.lifecycle.ViewModelProvider
import java.util.Locale

class MainActivity : ComponentActivity() , SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lastAcceleration: Float = 0f
    private val shakeThreshold = 5f // Adjust as needed
    private lateinit var languageSpinner: Spinner
    private lateinit var editText: EditText
    private lateinit var speechRecognizer: SpeechRecognizer
    private var isListening=false
    private val RECORD_AUDIO_PERMISSION_REQUEST_CODE = 123
    private var mediaPlayer: MediaPlayer? = null
    private var audioID=0
    private var lg=0.0
    private var lt=0.0
    private var l=""

    val locations = listOf(
        "London, United Kingdom" to Pair(51.5074, -0.1278),
        "Madrid, Spain" to Pair(40.4168, -3.7038),
        "Paris, France" to Pair(48.8566, 2.3522),
        "Rome, Italy" to Pair(41.9028, 12.4964),
        "Beijing, China" to Pair(39.9042, 116.4074),
        "Berlin, Germany" to Pair(52.5200, 13.4050)
    )

    val formattedLocations = locations.map { (location, coordinates) ->
        val (latitude, longitude) = coordinates
        val label = location.replace(",", "_")
        "geo:$latitude,$longitude?q=$latitude,$longitude($label)"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout)
        checkMicrophonePermission()


        mediaPlayer = MediaPlayer.create(this, R.raw.english)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        languageSpinner = findViewById(R.id.languageSpinner)
        editText = findViewById(R.id.editText)
        val languages = resources.getStringArray(R.array.language_options)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        languageSpinner.adapter = adapter

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("sr","onReadyForSpeech")
                //showToast("onReadyForSpeech")
                isListening=true
            }

            override fun onBeginningOfSpeech() {
                Log.d("sr","onBeginningOfSpeech")
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d("sr","onEndOfSpeech")
            }

            override fun onError(error: Int) {}

            override fun onResults(results: Bundle?) {
                Log.d("sr","onResults")
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val selectedLanguage = languageSpinner.selectedItem as String
                    editText.setText(matches[0])
                }else{
                    showToast("nothing")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedItem = parent.getItemAtPosition(position).toString()
                Toast.makeText(applicationContext, "Selected: $selectedItem", Toast.LENGTH_SHORT).show()
                audioID=getAudioID(selectedItem)
                val selectedLanguage = languageSpinner.selectedItem.toString()
                val correspondingCityPosition = languageSpinner.getSelectedItemPosition()
                if (correspondingCityPosition != -1) {
                    val (city, coordinates) = locations[correspondingCityPosition]
                    val (latitude, longitude) = coordinates
                    l = city.replace(",", "_")
                    lt=latitude
                    lg=longitude

                }
                editText.hint="Say a phrase in $selectedLanguage"
                startSpeechRecognition()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {

            }
        }





    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    private fun gotoMap(lg:Double,lt:Double,l:String){

        val locationUri = Uri.parse("geo:$lt,$lg?q=$lt,$lg($l)")
        val mapIntent = Intent(Intent.ACTION_VIEW, locationUri)
        mapIntent.setPackage("com.google.android.apps.maps")
        if (mapIntent.resolveActivity(packageManager) != null) {
            startActivity(mapIntent)
        }else{
            Toast.makeText(this, "No Google Map!", Toast.LENGTH_SHORT).show()
            Log.d("map","no GoogleMap")
        }


    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val acceleration = calculateAcceleration(it.values[0], it.values[1], it.values[2])
                val deltaAcceleration = acceleration - lastAcceleration


                if (deltaAcceleration > shakeThreshold&&lastAcceleration!=0f) {
                    Toast.makeText(this, "Shake detected!", Toast.LENGTH_SHORT).show()
                    Log.d("sensor","Shaking...")
                    mediaPlayer = MediaPlayer.create(this, audioID)
                    mediaPlayer?.start()
                    //gotoMap(0.0,0.0)
                    gotoMap(lg,lt,l)
                }
                lastAcceleration = acceleration
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        //
    }
    private fun calculateAcceleration(x: Float, y: Float, z: Float): Float {
        return Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
    }

    private fun getAudioID(s:String):Int{
        return resources.getIdentifier(s.lowercase(), "raw", packageName)
    }

    private fun getLanguageCode(language: String): String {
        return when (language.toLowerCase(Locale.getDefault())) {
            "english" -> "en-US"
            "spanish" -> "es-ES"
            "french" -> "fr-FR"
            "italian" -> "it-IT"
            "chinese" -> "zh-CN"
            "german" -> "de-DE"

            else -> "en-US"
        }
    }
    private fun startSpeechRecognition() {
        Log.d("sr","start recognition")
        val selectedLanguage = languageSpinner.selectedItem as String
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, getLanguageCode(selectedLanguage))
        speechRecognizer.startListening(intent)
    }

    private fun checkMicrophonePermission() {
        val permissionStatus = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        if (permissionStatus == PackageManager.PERMISSION_GRANTED) {
        } else {
            requestMicrophonePermission()
        }
    }

    private fun requestMicrophonePermission() {
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            } else {
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
