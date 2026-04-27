package com.youdash.service;

import org.springframework.web.multipart.MultipartFile;

public interface ImageStorageService {
    String uploadBannerImage(MultipartFile file);
}
