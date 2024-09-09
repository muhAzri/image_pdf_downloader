package com.zrifapps.image_pdf_downloader

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.annotation.NonNull
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.zrifapps.image_pdf_downloader.ImageDownloadJob
import com.zrifapps.image_pdf_downloader.ImageDownloadService
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

class ImagePdfDownloaderPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {

    private lateinit var channel: MethodChannel
    private lateinit var binding: FlutterPlugin.FlutterPluginBinding
    private var activity: Activity? = null
    private val permissionRequestCode = 1001

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "image_pdf_downloader")
        channel.setMethodCallHandler(this)
        binding = flutterPluginBinding
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onMethodCall(call: MethodCall, result: Result) {

        when (call.method) {
            "startDownload" -> {
                val downloadJobs = call.argument<List<Map<String, Any>>>("downloadJobs")

                if (downloadJobs != null) {

                    if (hasStoragePermission()) {

                        // Parse the list of maps to a list of DownloadJob objects
                        val jobs = downloadJobs.mapNotNull { jobMap ->
                            val jobName = jobMap["jobName"] as? String
                            val imageUrls = jobMap["imageUrls"] as? List<String>
                            if (jobName != null && imageUrls != null) {
                                ImageDownloadJob(jobName, imageUrls)
                            } else {
                                null
                            }
                        }

                        jobs.forEach { job ->
                            startDownloadService(job.jobName, ArrayList(job.imageUrls))
                        }
                        result.success("Batch download started")
                    } else {
                        requestStoragePermission()
                        result.error("PERMISSION_DENIED", "Storage permission denied", null)
                    }
                } else {
                    result.error("INVALID_ARGUMENT", "Missing or invalid downloadJobs", null)
                }
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    private fun startDownloadService(jobName: String, imageUrls: ArrayList<String>) {
        val intent = Intent(binding.applicationContext, ImageDownloadService::class.java)
        intent.putExtra("jobName", jobName)
        intent.putStringArrayListExtra("imageUrls", imageUrls)
        ContextCompat.startForegroundService(binding.applicationContext, intent)
    }

    private fun hasStoragePermission(): Boolean {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ActivityCompat.checkSelfPermission(
                activity!!,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
        return hasPermission
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity?.startActivity(intent)
        } else {
            ActivityCompat.requestPermissions(
                activity!!,
                arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                permissionRequestCode
            )
        }
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        this.activity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {
        this.activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        this.activity = binding.activity
    }

    override fun onDetachedFromActivity() {
        this.activity = null
    }
}
