package com.api.javawebapi.service;

import com.api.javawebapi.model.Video;
import com.api.javawebapi.repository.VideoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
public class VideoService {

    private static final Logger LOGGER = Logger.getLogger(VideoService.class.getName());

    private final VideoRepository videoRepository;
    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    public VideoService(VideoRepository videoRepository,
                        @Value("${aws.s3.endpoint}") String s3Endpoint,
                        @Value("${aws.s3.access-key}") String accessKey,
                        @Value("${aws.s3.secret-key}") String secretKey,
                        @Value("${aws.s3.region}") String region) {

        this.videoRepository = videoRepository;

        AwsBasicCredentials awsCreds = AwsBasicCredentials.create(accessKey, secretKey);
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(s3Endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
                .build();
    }

    // Method to initiate multipart upload
    public String initiateMultipartUpload(String fileName) {
        CreateMultipartUploadRequest uploadRequest = CreateMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .build();
        CreateMultipartUploadResponse response = s3Client.createMultipartUpload(uploadRequest);
        LOGGER.info("Initiated multipart upload with uploadId: " + response.uploadId());
        return response.uploadId();
    }

    // Method to upload a single part
    public String uploadPart(String uploadId, String fileName, int partNumber, byte[] partData) {
        UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .uploadId(uploadId)
                .partNumber(partNumber)
                .build();

        UploadPartResponse response = s3Client.uploadPart(uploadPartRequest, RequestBody.fromBytes(partData));
        String eTag = response.eTag();
        LOGGER.info("Uploaded part number: " + partNumber + " for uploadId: " + uploadId + " with ETag: " + eTag);
        return eTag;
    }

    // Method to complete the multipart upload
    public void completeMultipartUpload(String uploadId, String fileName, List<CompletedPart> completedParts) {
        CompleteMultipartUploadRequest completeMultipartUploadRequest = CompleteMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
                .build();
        s3Client.completeMultipartUpload(completeMultipartUploadRequest);

        Video video = new Video();
        video.setFileName(fileName);
        videoRepository.save(video);

        LOGGER.info("Completed upload for file: " + fileName);
    }

    // Method to list videos with pagination
    public List<Video> listVideos(int page, int size) {
        return videoRepository.findAll(PageRequest.of(page, size)).getContent();
    }

    // Method to handle resumable download
    public ResponseEntity<Resource> downloadVideo(String fileName, HttpServletRequest request) {
        try {
            Path videoPath = Paths.get("path/to/videos").resolve(fileName);
            if (!Files.exists(videoPath)) {
                return ResponseEntity.notFound().build();
            }

            byte[] fileBytes = Files.readAllBytes(videoPath);
            String range = request.getHeader(HttpHeaders.RANGE);

            if (range != null) {
                // Parse the range header to get start and end
                long fileLength = fileBytes.length;
                long start = Long.parseLong(range.replace("bytes=", "").split("-")[0]);
                long end = range.contains("-") && !range.endsWith("-")
                        ? Long.parseLong(range.split("-")[1])
                        : fileLength - 1;

                byte[] partialData = java.util.Arrays.copyOfRange(fileBytes, (int) start, (int) end + 1);

                HttpHeaders headers = new HttpHeaders();
                headers.add(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileLength);
                headers.add(HttpHeaders.CONTENT_LENGTH, String.valueOf(partialData.length));

                return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                        .headers(headers)
                        .body(new ByteArrayResource(partialData));
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .body(new ByteArrayResource(fileBytes));
        } catch (IOException e) {
            LOGGER.severe("Error downloading video: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
