// ignore_for_file: use_build_context_synchronously

import 'package:flutter/material.dart';
import 'package:image_pdf_downloader/image_download_job_model.dart';
import 'package:image_pdf_downloader/image_pdf_downloader.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final ImagePdfDownloader _imagePdfDownloaderPlugin = ImagePdfDownloader();

  @override
  void initState() {
    super.initState();
  }

  // Method to start batch download
  void _startBatchDownload() async {
    final jobs = [
      ImageDownloadJobModel(
        jobName: 'DEMON PRINCE',
        imageUrl: [
          "https://img.manhwaindo.id/uploads/manga-images/t/the-demon-prince-goes-to-the-academy/chapter-80/1-66cc737f6ffaf.jpg",
          "https://img.manhwaindo.id/uploads/manga-images/t/the-demon-prince-goes-to-the-academy/chapter-80/2-66cc7380c76ab.jpg",
          "https://img.manhwaindo.id/uploads/manga-images/t/the-demon-prince-goes-to-the-academy/chapter-80/3-66cc73822a5fa.jpg",
          "https://img.manhwaindo.id/uploads/manga-images/t/the-demon-prince-goes-to-the-academy/chapter-80/4-66cc7383f1900.jpg",
          "https://img.manhwaindo.id/uploads/manga-images/t/the-demon-prince-goes-to-the-academy/chapter-80/5-66cc73858b354.jpg",
          "https://img.manhwaindo.id/uploads/manga-images/t/the-demon-prince-goes-to-the-academy/chapter-80/6-66cc73874bc3f.jpg",
          "https://img.manhwaindo.id/uploads/manga-images/t/the-demon-prince-goes-to-the-academy/chapter-80/7-66cc738907b80.jpg",
          "https://img.manhwaindo.id/uploads/manga-images/t/the-demon-prince-goes-to-the-academy/chapter-80/8-66cc738a73f19.jpg",
          "https://img.manhwaindo.id/uploads/manga-images/t/the-demon-prince-goes-to-the-academy/chapter-80/9-66cc738bf03eb.jpg",
          "https://img.manhwaindo.id/uploads/manga-images/t/the-demon-prince-goes-to-the-academy/chapter-80/10-66cc738d7437f.jpg",
          "https://img.manhwaindo.id/uploads/manga-images/t/the-demon-prince-goes-to-the-academy/chapter-80/11-66cc738f3a946.jpg",
          "https://img.manhwaindo.id/uploads/manga-images/t/the-demon-prince-goes-to-the-academy/chapter-80/12-66cc739083b54.jpg",
          "https://img.manhwaindo.id/uploads/manga-images/t/the-demon-prince-goes-to-the-academy/chapter-80/13-66cc739210c60.jpg",
          "https://img.manhwaindo.id/uploads/manga-images/t/the-demon-prince-goes-to-the-academy/chapter-80/14-66cc73938a2ac.jpg",
          "https://img.manhwaindo.id/uploads/manga-images/t/the-demon-prince-goes-to-the-academy/chapter-80/15-66cc7394dbbea.jpg",
          "https://img.manhwaindo.id/uploads/manga-images/t/the-demon-prince-goes-to-the-academy/chapter-80/16-66cc73963c56c.jpg",
          "https://img.manhwaindo.id/uploads/manga-images/t/the-demon-prince-goes-to-the-academy/chapter-80/17-66cc73980e8cc.jpg",
          "https://img.manhwaindo.id/uploads/manga-images/t/the-demon-prince-goes-to-the-academy/chapter-80/18-66cc739965928.jpg",
          "https://img.manhwaindo.id/uploads/manga-images/t/the-demon-prince-goes-to-the-academy/chapter-80/19-66cc739b84079.jpg",
          "https://img.manhwaindo.id/uploads/manga-images/t/the-demon-prince-goes-to-the-academy/chapter-80/20-66cc739cd9102.jpg",
          "https://img.manhwaindo.id/uploads/manga-images/t/the-demon-prince-goes-to-the-academy/chapter-80/21-66cc739e5eb15.jpg",
          "https://img.manhwaindo.id/ads/Manhwaland Banner-min.jpg"
        ],
      ),
      ImageDownloadJobModel(
        jobName: 'Job2',
        imageUrl: [
          'https://img.manhwaindo.id/uploads/manga-images/a/academys-genius-swordmaster-id/chapter-65/1-66dc85f2c0a2d.jpg',
          'https://img.manhwaindo.id/uploads/manga-images/a/academys-genius-swordmaster-id/chapter-65/1-66dc85f2c0a2d.jpg',
        ],
      ),
    ];

    try {
      await _imagePdfDownloaderPlugin.startDownload(jobs);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Batch download started')),
      );
    } catch (error) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Error: $error')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: Center(
          child: ElevatedButton(
            onPressed: _startBatchDownload,
            child: const Text('Start Batch Download'),
          ),
        ),
      ),
    );
  }
}
