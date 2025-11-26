package com.group6.Rental_Car.services.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StorageServiceImpl implements StorageService {

    private final S3Client s3;
    private final S3Presigner presigner;

    @Value("${longvan.s3.bucket}")
    private String bucket;

    @Value("${longvan.s3.endpoint:https://s3-hcm5-r1.longvan.net}")
    private String endpoint;

    /** Upload public-by-default → trả URL public để lưu DB */
    public String uploadPublic(String folder, MultipartFile file) throws IOException {
        String key = buildKey(folder, file.getOriginalFilename());
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .contentLength(file.getSize())
                        .acl(ObjectCannedACL.PUBLIC_READ) // thử bỏ nếu Long Vân không bật ACL
                        .build(),
                RequestBody.fromInputStream(file.getInputStream(), file.getSize())
        );

        String base = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        return base + "/" + bucket + "/" + urlEncodePath(key);
    }


    // =================================== helpers ===================================
    private String buildKey(String folder, String originalName) {
        String safeName = (originalName == null || originalName.isBlank()) ? "file"
                : originalName.replace("\\", "/");
        if (safeName.contains("/")) {
            safeName = safeName.substring(safeName.lastIndexOf('/') + 1);
        }
        return folder + "/" + UUID.randomUUID() + "/" + safeName;
    }

    private static String urlEncodePath(String key) {
        String[] parts = key.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append('/');
            sb.append(URLEncoder.encode(parts[i], StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
