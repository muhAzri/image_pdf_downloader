package com.zrifapps.image_pdf_downloader

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList
import android.util.Base64
import androidx.annotation.RequiresApi
import java.nio.file.Files
import com.itextpdf.html2pdf.HtmlConverter



class ImageDownloadService : Service() {

    companion object {
        private val jobQueue: Queue<ImageDownloadJob> = LinkedList()
        private var isJobRunning: Boolean = false
        private val client = OkHttpClient()
        private const val CHANNEL_ID = "DOWNLOAD_CHANNEL"
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d("ImageDownloadService", "Service bound")
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("ImageDownloadService", "Service created")
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Download Channel"
            val descriptionText = "Channel for download notifications"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d("ImageDownloadService", "Notification channel created: $CHANNEL_ID")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val jobName = intent?.getStringExtra("jobName")
        val imageUrls = intent?.getStringArrayListExtra("imageUrls")

        Log.d("ImageDownloadService", "Service started with jobName: $jobName and ${imageUrls?.size ?: 0} imageUrls")

        if (jobName != null && imageUrls != null) {
            if (hasStoragePermission()) {
                Log.d("ImageDownloadService", "Storage permission granted")

                val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Downloading Images")
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
                Log.d("ImageDownloadService", "Storage permission denied")
                requestStoragePermission()
            }
        } else {
            Log.d("ImageDownloadService", "JobName or imageUrls is null")
        }

        return START_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun processNextJob() {
        Log.d("ImageDownloadService", "Processing next job in queue")
        val currentJob = jobQueue.poll()
        if (currentJob != null) {
            isJobRunning = true
            downloadImages(currentJob)
        } else {
            isJobRunning = false
            Log.d("ImageDownloadService", "No more jobs, stopping service")
            stopSelf()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun downloadImages(job: ImageDownloadJob) {
        Log.d("ImageDownloadService", "Starting download for job: ${job.jobName}")
        Thread {
            val notificationId = UUID.randomUUID().hashCode() // Unique notification ID
            try {
                // Use a LinkedHashMap to preserve the insertion order
                val downloadedFiles = LinkedHashMap<String, File>()
                for (url in job.imageUrls) {
                    Log.d("ImageDownloadService", "Downloading image: $url")
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
                    showNotification(notificationId, "${job.jobName} PDF created: ${pdfFile.path}")
                }
            } catch (e: Exception) {
                Log.e("ImageDownloadService", "Error downloading images", e)
                showNotification(notificationId, "${job.jobName} failed")
            } finally {
                isJobRunning = false
                processNextJob()
            }
        }.start()
    }

    private fun downloadImage(imageUrl: String): File? {
        Log.d("ImageDownloadService", "Downloading image: $imageUrl")
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
            Log.e("ImageDownloadService", "Download error: $imageUrl", e)
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createPdfFromImages(imageFiles: List<File>, jobName: String): File {
        // Define the temporary HTML file path
        val tempHtmlFile = File.createTempFile("temp_$jobName", ".html")

        val htmlContent = StringBuilder()
        htmlContent.append("<html><body style='margin: 0;'>")

        // Calculate total height for the PDF page
        var totalHeight = 0f
        val imageHeights = mutableListOf<Float>()

        for (imageFile in imageFiles) {
            // Load the image and get its dimensions
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            val imageHeight = bitmap.height.toFloat()
            val imageWidth = bitmap.width.toFloat()

            val imageData = Files.readAllBytes(imageFile.toPath())
            val encodedImage = Base64.encodeToString(imageData, Base64.DEFAULT)
            val imageType = Files.probeContentType(imageFile.toPath())?.split("/")?.get(1) ?: "png"

            // Append image height to total height and store individual height
            totalHeight += imageHeight
            imageHeights.add(imageHeight)

            // Add image to HTML content
            val imgHtml = "<img src='data:image/$imageType;base64,$encodedImage' style='display: block; width: ${imageWidth}px; height: ${imageHeight}px;' />"
            htmlContent.append(imgHtml)
        }

        // Close the HTML tags
        htmlContent.append("</body></html>")
        tempHtmlFile.writeText(htmlContent.toString())

        // Define the PDF file path
        val pdfFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "$jobName.pdf")

        try {
            // Create a PdfWriter instance
            val writer = PdfWriter(FileOutputStream(pdfFile))

            // Create a PdfDocument instance
            val pdfDocument = PdfDocument(writer)

            // Set the PDF page size to accommodate all images
            pdfDocument.defaultPageSize = com.itextpdf.kernel.geom.PageSize(PageSize.A4.width, totalHeight)

            // Create a Document instance
            val document = Document(pdfDocument)

            // Convert HTML to PDF
            HtmlConverter.convertToPdf(tempHtmlFile.inputStream(), pdfDocument)

            // Close the document
            document.close()

            // Delete the temporary HTML file
            tempHtmlFile.delete()

        } catch (e: IOException) {
            Log.e("PdfConversionService", "Error creating PDF: ${e.message}")
        } catch (e: Exception) {
            Log.e("PdfConversionService", "Error creating PDF: ${e.message}")
        }

        return pdfFile
    }

    private fun showNotification(notificationId: Int, status: String) {
        Log.d("ImageDownloadService", "Showing notification: $status")
        val notificationManager = NotificationManagerCompat.from(this)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Image Download Status")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_menu_report_image)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true) // Auto-cancel the notification when tapped
            .setSilent(true)
            .build()
        notificationManager.notify(notificationId, notification)
    }

    private fun updateNotification(notificationId: Int, jobName: String, current: Int, total: Int) {
        Log.d("ImageDownloadService", "Updating notification for $jobName: $current of $total")

        val progress = (current.toFloat() / total * 100).toInt()

        val notificationManager = NotificationManagerCompat.from(this)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading Images for $jobName")
//            .setContentText("Progress: $current of $total")
            .setSmallIcon(android.R.drawable.ic_menu_report_image)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setProgress(100, progress, false) // Set the progress bar with max 100 and current progress
            .build()

        notificationManager.notify(notificationId, notification)
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
        Log.d("ImageDownloadService", "Has storage permission: $hasPermission")
        return hasPermission
    }

    private fun requestStoragePermission() {
        Log.d("ImageDownloadService", "Requesting storage permission")
        val intent = Intent("com.zrifapps.image_pdf_downloader.PERMISSION_REQUEST")
        sendBroadcast(intent) // Notify that permission is required
    }


}
