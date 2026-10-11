package com.thuliinai.demo.common.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
public class R2DocumentStorageService implements DocumentStorageService {

    private static final Logger log = LoggerFactory.getLogger(R2DocumentStorageService.class);

    private final S3Client r2Client;
    private final String bucket;

    public R2DocumentStorageService(S3Client r2Client, @Value("${r2.bucket}") String bucket) {
        this.r2Client = r2Client;
        this.bucket = bucket;
    }

    @Override
    public String storeDocument(String folder, MultipartFile file) throws IOException {
        String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        String extension = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) {
            extension = original.substring(dot);
        }

        String objectKey = folder + "/" + UUID.randomUUID() + extension;

        r2Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .contentType(file.getContentType())
                        .build(),
                RequestBody.fromInputStream(file.getInputStream(), file.getSize())
        );

        log.info("Stored file '{}' into R2 bucket '{}' with key '{}'", original, bucket, objectKey);
        return objectKey;
    }

    @Override
    public void deleteDocument(String objectKey) {
        try {
            r2Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
            log.info("Deleted document '{}' from R2 bucket '{}'", objectKey, bucket);
        } catch (Exception e) {
            log.warn("Failed to delete document '{}' from R2 bucket '{}': {}", objectKey, bucket, e.getMessage());
        }
    }
}
