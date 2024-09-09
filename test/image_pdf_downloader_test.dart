import 'package:flutter_test/flutter_test.dart';
import 'package:image_pdf_downloader/image_pdf_downloader.dart';
import 'package:image_pdf_downloader/image_pdf_downloader_platform_interface.dart';
import 'package:image_pdf_downloader/image_pdf_downloader_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockImagePdfDownloaderPlatform
    with MockPlatformInterfaceMixin
    implements ImagePdfDownloaderPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final ImagePdfDownloaderPlatform initialPlatform = ImagePdfDownloaderPlatform.instance;

  test('$MethodChannelImagePdfDownloader is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelImagePdfDownloader>());
  });

  test('getPlatformVersion', () async {
    ImagePdfDownloader imagePdfDownloaderPlugin = ImagePdfDownloader();
    MockImagePdfDownloaderPlatform fakePlatform = MockImagePdfDownloaderPlatform();
    ImagePdfDownloaderPlatform.instance = fakePlatform;

    expect(await imagePdfDownloaderPlugin.getPlatformVersion(), '42');
  });
}
