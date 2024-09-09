import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:image_pdf_downloader/image_download_job_model.dart';

import 'image_pdf_downloader_platform_interface.dart';

/// An implementation of [ImagePdfDownloaderPlatform] that uses method channels.
class MethodChannelImagePdfDownloader extends ImagePdfDownloaderPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('image_pdf_downloader');

  @override
  Future<String?> getPlatformVersion() async {
    final version =
        await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }

  @override
  Future<void> startDownload(List<ImageDownloadJobModel> jobs) async {
    try {
      final jobMaps = jobs
          .map((job) => {
                'jobName': job.jobName,
                'imageUrls': job.imageUrl,
              })
          .toList();

      await methodChannel
          .invokeMethod('startDownload', {'downloadJobs': jobMaps});
    } on PlatformException catch (e) {
      throw ('Failed to start download: ${e.message}');
    }
  }
}
