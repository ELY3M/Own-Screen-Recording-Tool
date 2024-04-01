package own.ownscreenrecordtool

import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.hbisoft.hbrecorder.Constants.MAX_FILE_SIZE_REACHED_ERROR
import com.hbisoft.hbrecorder.Constants.SETTINGS_ERROR
import com.hbisoft.hbrecorder.HBRecorder
import com.hbisoft.hbrecorder.HBRecorderListener
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class MainActivity : Activity(), HBRecorderListener {
    val TAG = "elys-screen"
    val FOLDER_NAME = "elys-screen"

    //Permissions
    private val SCREEN_RECORD_REQUEST_CODE = 777
    private val PERMISSION_REQ_ID_RECORD_AUDIO = 22
    private val PERMISSION_REQ_POST_NOTIFICATIONS = 33
    private val PERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE = PERMISSION_REQ_ID_RECORD_AUDIO + 1
    private var hasPermissions = false

    //Declare HBRecorder
    private var hbRecorder: HBRecorder? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            //Init HBRecorder
            hbRecorder = HBRecorder(this, this)

            //When the user returns to the application, some UI changes might be necessary,
            //check if recording is in progress and make changes accordingly
            if (hbRecorder!!.isBusyRecording()) {
                return
            }
        }

        val button = findViewById<Button>(R.id.button)
        val buttonstop = findViewById<Button>(R.id.buttonstop)

        button.setOnClickListener() {
      // Toast.makeText(this, "pressed!!!!!!", Toast.LENGTH_LONG).show()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                //first check if permissions was granted
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS, PERMISSION_REQ_POST_NOTIFICATIONS) && checkSelfPermission(Manifest.permission.RECORD_AUDIO, PERMISSION_REQ_ID_RECORD_AUDIO)) {
                        hasPermissions = true
                    }
                }
                else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO, PERMISSION_REQ_ID_RECORD_AUDIO)) {
                        hasPermissions = true
                    }
                } else {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO, PERMISSION_REQ_ID_RECORD_AUDIO) && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, PERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE)) {
                        hasPermissions = true
                    }
                }

                if (hasPermissions) {
                    //check if recording is in progress
                    //and stop it if it is
                    if (hbRecorder!!.isBusyRecording()) {
                        hbRecorder!!.stopScreenRecording()
                    }
                    //else start recording
                    else {
                        startRecordingScreen()
                    }
                }
            } else {
                showLongToast("This library requires API 21>")
            }

            buttonstop.setOnClickListener() {
                hbRecorder!!.stopScreenRecording()
            }




        }




    }



















    private fun createFolder() {
        val f1 = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), FOLDER_NAME)
        if (!f1.exists()) {
            if (f1.mkdirs()) {
                Log.i(TAG, "created")
            }
        }
    }

    //Check if permissions was granted
    private fun checkSelfPermission(permission: String, requestCode: Int): Boolean {
        if (ContextCompat.checkSelfPermission(
                this,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
            return false
        }
        return true
    }

    //Handle permissions
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQ_POST_NOTIFICATIONS -> if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkSelfPermission(
                    Manifest.permission.RECORD_AUDIO,
                    PERMISSION_REQ_ID_RECORD_AUDIO
                )
            } else {
                hasPermissions = false
                showLongToast("No permission for " + Manifest.permission.POST_NOTIFICATIONS)
            }

            PERMISSION_REQ_ID_RECORD_AUDIO -> if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkSelfPermission(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    PERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE
                )
            } else {
                hasPermissions = false
                showLongToast("No permission for " + Manifest.permission.RECORD_AUDIO)
            }

            PERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                hasPermissions = true
                startRecordingScreen()
            } else {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    hasPermissions = true
                    //Permissions was provided
                    //Start screen recording
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        startRecordingScreen()
                    }
                } else {
                    hasPermissions = false
                    showLongToast("No permission for " + Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }

            else -> {}
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (requestCode == SCREEN_RECORD_REQUEST_CODE) {
                if (resultCode == RESULT_OK) {
                    //Set file path or Uri depending on SDK version
                    setOutputPath()
                    //Start screen recording
                    hbRecorder!!.startScreenRecording(data, resultCode)
                }
            }
        }
    }

    //For Android 10> we will pass a Uri to HBRecorder
    //This is not necessary - You can still use getExternalStoragePublicDirectory
    //But then you will have to add android:requestLegacyExternalStorage="true" in your Manifest
    //IT IS IMPORTANT TO SET THE FILE NAME THE SAME AS THE NAME YOU USE FOR TITLE AND DISPLAY_NAME
    var resolver: ContentResolver? = null
    var contentValues: ContentValues? = null
    var mUri: Uri? = null
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private fun setOutputPath() {
        val filename = generateFileName()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver = contentResolver
            contentValues = ContentValues()
            contentValues!!.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/" + FOLDER_NAME)
            contentValues!!.put(MediaStore.Video.Media.TITLE, filename)
            contentValues!!.put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            contentValues!!.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            mUri = resolver?.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            //FILE NAME SHOULD BE THE SAME
            hbRecorder!!.fileName = filename
            hbRecorder!!.setOutputUri(mUri)
        } else {
            createFolder()
            hbRecorder!!.setOutputPath(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                    .toString() + "/"+FOLDER_NAME
            )
        }
    }

    //Generate a timestamp to be used as a file name
    private fun generateFileName(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault())
        val curDate = Date(System.currentTimeMillis())
        return formatter.format(curDate).replace(" ", "")
    }

    //Show Toast
    private fun showLongToast(msg: String) {
        Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()
    }


/////
// Called when recording starts
override fun HBRecorderOnStart() {
    Log.e(TAG, "HBRecorderOnStart called")
}

    //Listener for when the recording is saved successfully
    //This will be called after the file was created
    override fun HBRecorderOnComplete() {
        showLongToast("Saved Successfully")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            //Update gallery depending on SDK Level
            if (hbRecorder!!.wasUriSet()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    updateGalleryUri()
                } else {
                    refreshGalleryFile()
                }
            } else {
                refreshGalleryFile()
            }
        }
    }

    // Called when error occurs
    override fun HBRecorderOnError(errorCode: Int, reason: String?) {
        // Error 38 happens when
        // - the selected video encoder is not supported
        // - the output format is not supported
        // - if another app is using the microphone

        //It is best to use device default
        if (errorCode == SETTINGS_ERROR) {
            showLongToast(getString(R.string.settings_not_supported_message))
        } else if (errorCode == MAX_FILE_SIZE_REACHED_ERROR) {
            showLongToast(getString(R.string.max_file_size_reached_message))
        } else {
            showLongToast(getString(R.string.general_recording_error_message))
            Log.e(TAG, reason!!)
        }
    }

    // Called when recording has been paused
    override fun HBRecorderOnPause() {
        // Called when recording was paused
    }

    // Calld when recording has resumed
    override fun HBRecorderOnResume() {
        // Called when recording was resumed
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private fun refreshGalleryFile() {
        MediaScannerConnection.scanFile(
            this, arrayOf(hbRecorder!!.filePath), null
        ) { path, uri ->
            Log.i(TAG, "Scanned $path:")
            Log.i(TAG, "-> uri=$uri")
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private fun updateGalleryUri() {
        contentValues!!.clear()
        contentValues!!.put(MediaStore.Video.Media.IS_PENDING, 0)
        contentResolver.update(mUri!!, contentValues, null, null)
    }

    //Start recording screen
    //It is important to call it like this
    //hbRecorder.startScreenRecording(data); should only be called in onActivityResult
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private fun startRecordingScreen() {
            val mediaProjectionManager =
                getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val permissionIntent = mediaProjectionManager?.createScreenCaptureIntent()
            startActivityForResult(permissionIntent, SCREEN_RECORD_REQUEST_CODE)
    }










}

