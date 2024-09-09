import 'package:image_pdf_downloader/image_download_job_model.dart';

import 'image_pdf_downloader_platform_interface.dart';

class ImagePdfDownloader {
  Future<String?> getPlatformVersion() {
    return ImagePdfDownloaderPlatform.instance.getPlatformVersion();
  }

  Future<void> startDownload(List<ImageDownloadJobModel> jobs) {
    return ImagePdfDownloaderPlatform.instance.startDownload(jobs);
  }
}
