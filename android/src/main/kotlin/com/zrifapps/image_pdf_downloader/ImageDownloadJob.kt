package com.zrifapps.image_pdf_downloader

data class ImageDownloadJob(
    val jobName: String,
    val imageUrls: List<String>
)
