package com.example.campushub.services;

import java.io.IOException;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class FileUploadService {
    
    private final CloudinaryService cloudinaryService;

    /**
     * Upload single file without category (backward compatibility)
     */
    public String uploadFile(MultipartFile file) throws Exception {
        return uploadFile(file, "general");
    }
    
    /**
     * Upload multiple files without category (backward compatibility)
     */
    public List<String> uploadFiles(List<MultipartFile> files) throws Exception {
        return uploadFiles(files, "general");
    }
    
    /**
     * Upload multiple files with category
     */
    public List<String> uploadFiles(List<MultipartFile> files, String category) throws Exception {
        return cloudinaryService.uploadImages(files, category);
    }

    /**
     * Upload single file with category
     */
    public String uploadFile(MultipartFile file, String category) throws Exception {
        return cloudinaryService.uploadImage(file, category);
    }

    /**
     * Delete single file by URL
     */
    public void deleteFile(String imageUrl) throws IOException {
        try {
            cloudinaryService.deleteImageByUrl(imageUrl);
        } catch (Exception e) {
            log.error("Error deleting file: {}", imageUrl, e);
            throw new IOException("Failed to delete file: " + imageUrl, e);
        }
    }
    
    /**
     * Delete multiple files by URLs
     */
    public void deleteFiles(List<String> imageUrls) throws IOException {
        try {
            cloudinaryService.deleteImagesByUrls(imageUrls);
        } catch (Exception e) {
            log.error("Error deleting files: {}", imageUrls, e);
            throw new IOException("Failed to delete files", e);
        }
    }
}