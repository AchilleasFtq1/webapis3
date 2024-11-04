package com.api.javawebapi.controller;

import com.api.javawebapi.service.VideoService;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/videos")
public class VideoController {

    @Autowired
    private VideoService videoService;

    // Endpoint to initiate multipart upload
    @PostMapping("/upload/initiate")
    public ResponseEntity<String> initiateUpload(@RequestParam String fileName) {
        String uploadId = videoService.initiateMultipartUpload(fileName);
        return ResponseEntity.ok(uploadId);
    }

    // Endpoint to upload individual parts of the video
    @PostMapping("/upload/part")
    public ResponseEntity<String> uploadPart(@RequestParam String uploadId,
                                             @RequestParam String fileName,
                                             @RequestParam int partNumber,
                                             @RequestBody byte[] partData) {
        String eTag = videoService.uploadPart(uploadId, fileName, partNumber, partData);
        return ResponseEntity.ok(eTag); // Return ETag for the uploaded part
    }

    // Endpoint to complete the multipart upload
    @PostMapping("/upload/complete")
    public ResponseEntity<Void> completeUpload(@RequestParam String uploadId,
                                               @RequestParam String fileName,
                                               @RequestBody List<CompletedPartDto> parts) {
        List<software.amazon.awssdk.services.s3.model.CompletedPart> completedParts = parts.stream()
                .map(part -> software.amazon.awssdk.services.s3.model.CompletedPart.builder()
                        .partNumber(part.getPartNumber())
                        .eTag(part.getETag())
                        .build())
                .collect(Collectors.toList());

        videoService.completeMultipartUpload(uploadId, fileName, completedParts);
        return ResponseEntity.ok().build();
    }

    // Endpoint to list videos with pagination
    @GetMapping("/list")
    public ResponseEntity<List<VideoDto>> listVideos(@RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "10") int size) {
        List<VideoDto> videos = videoService.listVideos(page, size)
                .stream()
                .map(video -> new VideoDto(video.getId(), video.getFileName(), video.getUploadDate()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(videos);
    }

    // Endpoint to download video with resumable support
    @GetMapping("/download/{fileName}")
    public ResponseEntity<Resource> downloadVideo(@PathVariable String fileName, HttpServletRequest request) {
        try {
            return videoService.downloadVideo(fileName, request);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // DTO for CompletedPart
    public static class CompletedPartDto {
        // Getters and Setters
        @Setter
        @Getter
        private int partNumber;
        private String eTag;

        public String getETag() {
            return eTag;
        }

        public void setETag(String eTag) {
            this.eTag = eTag;
        }
    }

    // DTO for Video metadata
    public static class VideoDto {
        private Long id;
        private String fileName;
        private String uploadDate;

        // Constructor
        public VideoDto(Long id, String fileName, String uploadDate) {
            this.id = id;
            this.fileName = fileName;
            this.uploadDate = uploadDate;
        }

        // Getters and Setters
        public Long getId() {
            return id;
        }

        public String getFileName() {
            return fileName;
        }

        public String getUploadDate() {
            return uploadDate;
        }
    }
}
