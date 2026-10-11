package com.thuliinai.demo.common.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface DocumentStorageService {
    String storeDocument(String folder, MultipartFile file) throws IOException;
    void deleteDocument(String objectKey);
}
