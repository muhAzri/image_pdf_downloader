package com.zrifapps.image_pdf_downloader

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager

import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList
import android.util.Base64
import androidx.core.content.ContextCompat
import java.nio.file.Files
import com.itextpdf.html2pdf.HtmlConverter
import java.io.OutputStream


class ImageDownloadService : Service() {

    companion object {
        private val jobQueue: Queue<ImageDownloadJob> = LinkedList()
        private var isJobRunning: Boolean = false
        private val client = OkHttpClient()
        private const val CHANNEL_ID = "DOWNLOAD_CHANNEL"
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
            val name = "Download Channel"
            val descriptionText = "Channel for download notifications"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val jobName = intent?.getStringExtra("jobName")
        val imageUrls = intent?.getStringArrayListExtra("imageUrls")


        if (jobName != null && imageUrls != null) {
            if (hasStoragePermission()) {

                val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Downloading Manhwas")
                    .setContentText("Download in progress")
                    .setSilent(true)
                    .setSmallIcon(android.R.drawable.ic_menu_report_image)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()

                startForeground(1, notification)

                val job = ImageDownloadJob(jobName, imageUrls)
                jobQueue.offer(job)
                if (!isJobRunning) {
                    processNextJob()
                }
            } else {
                requestStoragePermission()
            }
        }

        return START_STICKY
    }

    private fun processNextJob() {
        val currentJob = jobQueue.poll()
        if (currentJob != null) {
            isJobRunning = true
            downloadImages(currentJob)
        } else {
            isJobRunning = false
            stopSelf()
        }
    }

    private fun downloadImages(job: ImageDownloadJob) {
        Thread {
            val notificationId = UUID.randomUUID().hashCode() // Unique notification ID
            try {
                // Use a LinkedHashMap to preserve the insertion order
                val downloadedFiles = LinkedHashMap<String, File>()
                for (url in job.imageUrls) {
                    val file = downloadImage(url)
                    if (file != null) {
                        downloadedFiles[url] = file
                        updateNotification(notificationId, job.jobName, downloadedFiles.size, job.imageUrls.size)
                    } else {
                        showNotification(notificationId, "${job.jobName} failed to download image: $url")
                    }
                }

                // Ensure all images are downloaded in the correct order
                val orderedImageFiles = ArrayList<File>()
                for (url in job.imageUrls) {
                    downloadedFiles[url]?.let { orderedImageFiles.add(it) }
                }

                // Now that we have ordered image files, create the PDF
                if (orderedImageFiles.size == job.imageUrls.size) {
                    val pdfFile = createPdfFromImages(orderedImageFiles, job.jobName)
                    showNotification(notificationId, "${job.jobName} Downloaded Stored in: ${pdfFile?.path}")
                }
            } catch (e: Exception) {
                showNotification(notificationId, "${job.jobName} failed")
            } finally {
                isJobRunning = false
                processNextJob()
            }
        }.start()
    }

    private fun downloadImage(imageUrl: String): File? {
        return try {
            val request = Request.Builder().url(imageUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("Failed to download image")

                val file = File(cacheDir, UUID.randomUUID().toString() + ".jpg")
                val fos = FileOutputStream(file)
                fos.use { output ->
                    response.body?.byteStream()?.use { input ->
                        input.copyTo(output)
                    }
                }
                file
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun createPdfFromImages(imageFiles: List<File>, jobName: String): Uri? {
        val resolver = applicationContext.contentResolver
        val pdfUri: Uri?

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Use MediaStore API for Android 29 and above
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$jobName.pdf")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            pdfUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        } else {
            // For Android below 29, write directly to the Downloads directory
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val pdfFile = File(downloadsDir, "$jobName.pdf")
            pdfUri = Uri.fromFile(pdfFile)

            try {
                pdfFile.createNewFile()
            } catch (e: IOException) {
                Log.e("PdfConversionService", "Failed to create PDF file: ${e.message}", e)
                return null
            }
        }

        pdfUri?.let {
            try {
                resolver.openOutputStream(it)?.use { outputStream ->
                    generateSinglePagePdfWithStretchedWidth(outputStream, imageFiles)
                }
            } catch (e: IOException) {
                Log.e("PdfConversionService", "Failed to write PDF: ${e.message}", e)
            }
        } ?: run {
            Log.e("PdfConversionService", "Failed to create PDF URI")
        }

        return pdfUri
    }

    private fun generateSinglePagePdfWithStretchedWidth(outputStream: OutputStream, imageFiles: List<File>) {
        val htmlContent = StringBuilder()
        htmlContent.append("<html><body style='margin: 0; padding: 0;'>")

        val pdfPageWidth = PageSize.A4.width // Set the target PDF page width (A4 width)
        var totalHeight = 0f

        // Iterate over images, scaling each one to fit the PDF width and adjusting its height accordingly
        for (imageFile in imageFiles) {
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            val originalImageWidth = bitmap.width.toFloat()
            val originalImageHeight = bitmap.height.toFloat()

            // Calculate the new height by preserving the aspect ratio
            val scaledHeight = (pdfPageWidth / originalImageWidth) * originalImageHeight
            totalHeight += scaledHeight

            val imageData = Files.readAllBytes(imageFile.toPath())
            val encodedImage = Base64.encodeToString(imageData, Base64.DEFAULT)
            val imageType = Files.probeContentType(imageFile.toPath())?.split("/")?.get(1) ?: "png"

            // Add the scaled image to the HTML content
            val imgHtml = "<img src='data:image/$imageType;base64,$encodedImage' style='display: block; width: ${pdfPageWidth}px; height: ${scaledHeight}px;' />"
            htmlContent.append(imgHtml)
        }

        htmlContent.append("</body></html>")

        try {
            val writer = PdfWriter(outputStream)
            val pdfDocument = PdfDocument(writer)
            val customPageSize = PageSize(pdfPageWidth, totalHeight)
            pdfDocument.defaultPageSize = customPageSize
            val document = Document(pdfDocument)

            HtmlConverter.convertToPdf(htmlContent.toString().byteInputStream(), pdfDocument)
            document.close()

        } catch (e: IOException) {
            Log.e("PdfConversionService", "Error creating PDF: ${e.message}", e)
        } catch (e: Exception) {
            Log.e("PdfConversionService", "Error creating PDF: ${e.message}", e)
        }
    }





    // Add this function to check if the notification permission is granted
    private fun checkNotificationPermission(): Boolean {
        // For Android 13+ (API level 33), check if POST_NOTIFICATIONS permission is granted
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            // For Android versions below 13, no need to check POST_NOTIFICATIONS permission
            true
        }
    }

    private fun showNotification(notificationId: Int, status: String) {
        // Check if the permission is granted
        if (!checkNotificationPermission()) {
            // Optionally, you can request permission here if needed
            Log.e("Notification", "Permission not granted")
            return
        }

        val notificationManager = NotificationManagerCompat.from(this)

        val smallIconResId = getLauncherIconResId()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Download Status")
            .setContentText(status)
            .setSmallIcon(smallIconResId) // Set the launcher icon as the small icon
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true) // Auto-cancel the notification when tapped
            .setSilent(true)
            .build()

        try {
            notificationManager.notify(notificationId, notification)
        } catch (e: SecurityException) {
            Log.e("Notification", "SecurityException: Notification permission is missing", e)
        }
    }

    private fun updateNotification(notificationId: Int, jobName: String, current: Int, total: Int) {
        // Check if the permission is granted
        if (!checkNotificationPermission()) {
            Log.e("Notification", "Permission not granted")
            return
        }

        val progress = (current.toFloat() / total * 100).toInt()

        val notificationManager = NotificationManagerCompat.from(this)

        // Get the app's launcher icon dynamically
        val smallIconResId = getLauncherIconResId()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading $jobName")
            .setSmallIcon(smallIconResId) // Set the launcher icon as the small icon
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setProgress(100, progress, false) // Set the progress bar with max 100 and current progress
            .build()

        try {
            notificationManager.notify(notificationId, notification)
        } catch (e: SecurityException) {
            Log.e("Notification", "SecurityException: Notification permission is missing", e)
        }
    }


    private fun getLauncherIconResId(): Int {
        // Get the application's launcher icon dynamically
        val packageManager = this.packageManager
        val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
        return applicationInfo.icon // This gets the icon resource ID
    }



    private fun hasStoragePermission(): Boolean {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()  // For Android 11+
        } else {
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
        return hasPermission
    }

    private fun requestStoragePermission() {
        val intent = Intent("com.zrifapps.image_pdf_downloader.PERMISSION_REQUEST")
        sendBroadcast(intent) // Notify that permission is required
    }


}
