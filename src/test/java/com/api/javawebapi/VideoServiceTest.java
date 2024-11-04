package com.api.javawebapi;

import com.api.javawebapi.model.Video;
import com.api.javawebapi.repository.VideoRepository;
import com.api.javawebapi.service.VideoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Example;
import org.springframework.http.ResponseEntity;

import jakarta.servlet.http.HttpServletRequest; // Use this instead of jakarta.servlet.http.HttpServletRequest

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

public class VideoServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private VideoRepository videoRepository;

    @InjectMocks
    private VideoService videoService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testInitiateMultipartUpload() {
        String fileName = "test_video.mp4";
        CreateMultipartUploadResponse response = CreateMultipartUploadResponse.builder()
                .uploadId("testUploadId")
                .build();

        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class))).thenReturn(response);

        String uploadId = videoService.initiateMultipartUpload(fileName);

        assertEquals("testUploadId", uploadId);
        verify(s3Client, times(1)).createMultipartUpload(any(CreateMultipartUploadRequest.class));
    }

    @Test
    void testUploadPart() {
        String uploadId = "testUploadId";
        String fileName = "test_video.mp4";
        int partNumber = 1;
        byte[] partData = new byte[1024];
        UploadPartResponse response = UploadPartResponse.builder()
                .eTag("testETag")
                .build();

        when(s3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class))).thenReturn(response);

        String eTag = videoService.uploadPart(uploadId, fileName, partNumber, partData);

        assertEquals("testETag", eTag);
        verify(s3Client, times(1)).uploadPart(any(UploadPartRequest.class), any(RequestBody.class));
    }

    @Test
    void testCompleteMultipartUpload() {
        String uploadId = "testUploadId";
        String fileName = "test_video.mp4";
        List<CompletedPart> completedParts = List.of(CompletedPart.builder().partNumber(1).eTag("testETag").build());

        CompleteMultipartUploadResponse response = CompleteMultipartUploadResponse.builder().build();

        when(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class))).thenReturn(response);
        when(videoRepository.save(any(Video.class))).thenReturn(new Video());

        assertDoesNotThrow(() -> videoService.completeMultipartUpload(uploadId, fileName, completedParts));
        verify(s3Client, times(1)).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
        verify(videoRepository, times(1)).save(any(Video.class));
    }

    @Test
    void testListVideos() {
        Video video1 = new Video();
        video1.setFileName("video1.mp4");
        Video video2 = new Video();
        video2.setFileName("video2.mp4");

        when(videoRepository.findAll((Example<Video>) any())).thenReturn(List.of(video1, video2));

        List<Video> videos = videoService.listVideos(0, 2);

        assertEquals(2, videos.size());
        assertEquals("video1.mp4", videos.get(0).getFileName());
        assertEquals("video2.mp4", videos.get(1).getFileName());
    }

    @Test
    void testDownloadVideo_withRange() {
        String fileName = "test_video.mp4";
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Range")).thenReturn("bytes=0-1023");

        // Assuming video file path and content for the test
        Path videoPath = Paths.get("path/to/videos").resolve(fileName);
        byte[] fileContent = new byte[2048]; // Mock file content

        // Mock the file read process
        try (var mock = mockStatic(Files.class)) {
            mock.when(() -> Files.readAllBytes(videoPath)).thenReturn(fileContent);

            ResponseEntity<Resource> response = videoService.downloadVideo(fileName, request);

            assertEquals(206, response.getStatusCodeValue());
            assertTrue(response.getHeaders().containsKey("Content-Range"));
        } catch (Exception e) {
            fail("Exception during test: " + e.getMessage());
        }
    }
}
