import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:image_pdf_downloader/image_pdf_downloader_method_channel.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  MethodChannelImagePdfDownloader platform = MethodChannelImagePdfDownloader();
  const MethodChannel channel = MethodChannel('image_pdf_downloader');

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(
      channel,
      (MethodCall methodCall) async {
        return '42';
      },
    );
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, null);
  });

  test('getPlatformVersion', () async {
    expect(await platform.getPlatformVersion(), '42');
  });
}
