import 'package:image_pdf_downloader/image_download_job_model.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'image_pdf_downloader_method_channel.dart';

abstract class ImagePdfDownloaderPlatform extends PlatformInterface {
  /// Constructs a ImagePdfDownloaderPlatform.
  ImagePdfDownloaderPlatform() : super(token: _token);

  static final Object _token = Object();

  static ImagePdfDownloaderPlatform _instance =
      MethodChannelImagePdfDownloader();

  /// The default instance of [ImagePdfDownloaderPlatform] to use.
  ///
  /// Defaults to [MethodChannelImagePdfDownloader].
  static ImagePdfDownloaderPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [ImagePdfDownloaderPlatform] when
  /// they register themselves.
  static set instance(ImagePdfDownloaderPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }

  Future<void> startDownload(List<ImageDownloadJobModel> jobs) async {
    throw UnimplementedError(
        'startDownload(List<ImageDownloadJobModel> jobs) has not been implemented.');
  }
}
