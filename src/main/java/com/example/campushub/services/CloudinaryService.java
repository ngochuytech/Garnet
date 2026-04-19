package com.example.campushub.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class CloudinaryService {
    private final Cloudinary cloudinary;
    
    /**
     * Upload single image with category (simple version without transformation)
     */
    public String uploadImage(MultipartFile file, String category) {
        try {
            if (file == null || file.isEmpty()) {
                return null;
            }
            
            // Validate file type
            String contentType = file.getContentType();
            if (!isValidImageType(contentType)) {
                throw new RuntimeException("Chỉ chấp nhận file ảnh JPEG, PNG hoặc WebP");
            }
            
            // Validate file size (30MB)
            if (file.getSize() > 30 * 1024 * 1024) {
                throw new RuntimeException("Kích thước file không được vượt quá 30MB");
            }
            
            // Tạo map options cho upload (no transformation để tránh lỗi)
            Map<String, Object> options = new HashMap<>();
            
            // Set folder theo category
            if (category != null && !category.isEmpty()) {
                options.put("folder", "campushub/" + category);
            }
            
            log.info("Uploading file: {} to category: {} with options: {}", 
                     file.getOriginalFilename(), category, options);
            
            // Upload file without transformation
            @SuppressWarnings("unchecked")
            Map<String, Object> uploadResult = cloudinary.uploader()
                .upload(file.getBytes(), options);
            
            // Lấy URL public của ảnh
            String secureUrl = (String) uploadResult.get("secure_url");
            String publicId = (String) uploadResult.get("public_id");
            
            log.info("Upload thành công: {} - Public ID: {}", secureUrl, publicId);
            
            return secureUrl;
            
        } catch (IOException e) {
            log.error("Lỗi upload ảnh: ", e);
            throw new RuntimeException("Không thể upload ảnh", e);
        }
    }
    
    /**
     * Upload single image with size limit transformation
     */
    public String uploadImageWithResize(MultipartFile file, String category, int maxWidth, int maxHeight) {
        try {
            if (file == null || file.isEmpty()) {
                return null;
            }
            
            // Validate file type
            String contentType = file.getContentType();
            if (!isValidImageType(contentType)) {
                throw new RuntimeException("Chỉ chấp nhận file ảnh JPEG, PNG hoặc WebP");
            }
            
            // Validate file size (30MB)
            if (file.getSize() > 30 * 1024 * 1024) {
                throw new RuntimeException("Kích thước file không được vượt quá 30MB");
            }
            
            // Tạo map options cho upload
            Map<String, Object> options = new HashMap<>();
            
            // Set folder theo category
            if (category != null && !category.isEmpty()) {
                options.put("folder", "campushub/" + category);
            }
            
            // Transformation với resize (simple và safe)
            Map<String, Object> transformation = new HashMap<>();
            transformation.put("width", maxWidth);
            transformation.put("height", maxHeight);
            transformation.put("crop", "limit");
            options.put("transformation", transformation);
            
            // Upload file
            @SuppressWarnings("unchecked")
            Map<String, Object> uploadResult = cloudinary.uploader()
                .upload(file.getBytes(), options);
            
            // Lấy URL public của ảnh
            String secureUrl = (String) uploadResult.get("secure_url");
            String publicId = (String) uploadResult.get("public_id");
            
            log.info("Upload với resize thành công: {} - Public ID: {}", secureUrl, publicId);
            
            return secureUrl;
            
        } catch (IOException e) {
            log.error("Lỗi upload ảnh với resize: ", e);
            throw new RuntimeException("Không thể upload ảnh", e);
        }
    }
    
    /**
     * Upload single image without category (backward compatibility)
     */
    public String uploadImage(MultipartFile file) {
        return uploadImage(file, "general");
    }
    
    /**
     * Upload multiple images with category
     */
    public List<String> uploadImages(List<MultipartFile> files, String category) {
        List<String> uploadedUrls = new ArrayList<>();
        
        if (files == null || files.isEmpty()) {
            return uploadedUrls;
        }
        
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                String uploadedUrl = uploadImage(file, category);
                if (uploadedUrl != null) {
                    uploadedUrls.add(uploadedUrl);
                }
            }
        }
        
        return uploadedUrls;
    }
    
    /**
     * Upload multiple images without category (backward compatibility)
     */
    public List<String> uploadImages(List<MultipartFile> files) {
        return uploadImages(files, "general");
    }
    
    /**
     * Delete image by URL (extract public_id from URL)
     */
    public boolean deleteImageByUrl(String imageUrl) {
        try {
            if (imageUrl == null || imageUrl.isEmpty()) {
                return true;
            }
            
            // Extract public_id from Cloudinary URL
            String publicId = extractPublicIdFromUrl(imageUrl);
            if (publicId == null) {
                log.warn("Không thể extract public_id từ URL: {}", imageUrl);
                return false;
            }
            
            return deleteImage(publicId);
            
        } catch (Exception e) {
            log.error("Lỗi xóa ảnh từ URL: {}", imageUrl, e);
            return false;
        }
    }
    
    /**
     * Delete image by public_id
     */
    public boolean deleteImage(String publicId) {
        try {
            if (publicId == null || publicId.isEmpty()) {
                return true;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            boolean success = "ok".equals(result.get("result"));
            
            if (success) {
                log.info("Xóa ảnh thành công: {}", publicId);
            } else {
                log.warn("Xóa ảnh không thành công: {} - Result: {}", publicId, result.get("result"));
            }
            
            return success;
            
        } catch (Exception e) {
            log.error("Lỗi xóa ảnh: {}", publicId, e);
            return false;
        }
    }
    
    /**
     * Delete multiple images by URLs
     */
    public void deleteImagesByUrls(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return;
        }
        
        for (String imageUrl : imageUrls) {
            deleteImageByUrl(imageUrl);
        }
    }
    
    /**
     * Extract public_id from Cloudinary URL
     */
    private String extractPublicIdFromUrl(String url) {
        try {
            if (url == null || !url.contains("cloudinary.com")) {
                return null;
            }
            
            // Cloudinary URL format: https://res.cloudinary.com/{cloud_name}/image/upload/v{version}/{public_id}.{format}
            // or with folder: https://res.cloudinary.com/{cloud_name}/image/upload/v{version}/{folder}/{public_id}.{format}
            
            String[] parts = url.split("/");
            if (parts.length < 2) {
                return null;
            }
            
            // Find the part after "upload"
            boolean foundUpload = false;
            StringBuilder publicIdBuilder = new StringBuilder();
            
            for (int i = 0; i < parts.length; i++) {
                if ("upload".equals(parts[i])) {
                    foundUpload = true;
                    continue;
                }
                
                if (foundUpload && i < parts.length - 1) {
                    // Skip version part (starts with 'v' followed by numbers)
                    if (parts[i + 1].matches("^v\\d+$")) {
                        i++; // Skip version
                        continue;
                    }
                }
                
                if (foundUpload) {
                    if (publicIdBuilder.length() > 0) {
                        publicIdBuilder.append("/");
                    }
                    // Remove file extension from the last part
                    String part = parts[i];
                    if (i == parts.length - 1 && part.contains(".")) {
                        part = part.substring(0, part.lastIndexOf('.'));
                    }
                    publicIdBuilder.append(part);
                }
            }
            
            return publicIdBuilder.length() > 0 ? publicIdBuilder.toString() : null;
            
        } catch (Exception e) {
            log.error("Lỗi extract public_id từ URL: {}", url, e);
            return null;
        }
    }
    
    /**
     * Validate image file type
     */
    private boolean isValidImageType(String contentType) {
        return contentType != null && (
            contentType.equals("image/jpeg") ||
            contentType.equals("image/jpg") ||
            contentType.equals("image/png") ||
            contentType.equals("image/webp")
        );
    }

    /**
     * Validate video file type
     */
    private boolean isValidVideoType(String contentType) {
        return contentType != null && (
            contentType.equals("video/mp4") ||
            contentType.equals("video/mpeg") ||
            contentType.equals("video/quicktime") ||
            contentType.equals("video/x-msvideo") ||
            contentType.equals("video/webm")
        );
    }

    /**
     * Validate image or video file type
     */
    public boolean isValidMediaType(String contentType) {
        return isValidImageType(contentType) || isValidVideoType(contentType);
    }

    /**
     * Check if file is video
     */
    public boolean isVideo(String contentType) {
        return isValidVideoType(contentType);
    }

    /**
     * Upload single video with category
     */
    public String uploadVideo(MultipartFile file, String category) {
        try {
            if (file == null || file.isEmpty()) {
                return null;
            }
            
            // Validate file type
            String contentType = file.getContentType();
            if (!isValidVideoType(contentType)) {
                throw new RuntimeException("Chỉ chấp nhận file video MP4, MPEG, MOV, AVI hoặc WebM");
            }
            
            // Validate file size (100MB for video)
            if (file.getSize() > 100 * 1024 * 1024) {
                throw new RuntimeException("Kích thước video không được vượt quá 100MB");
            }
            
            // Tạo map options cho upload
            Map<String, Object> options = new HashMap<>();
            options.put("resource_type", "video");
            
            // Set folder theo category
            if (category != null && !category.isEmpty()) {
                options.put("folder", "campushub/" + category);
            }
            
            log.info("Uploading video: {} to category: {}", file.getOriginalFilename(), category);
            
            // Upload video
            @SuppressWarnings("unchecked")
            Map<String, Object> uploadResult = cloudinary.uploader()
                .upload(file.getBytes(), options);
            
            // Lấy URL public của video
            String secureUrl = (String) uploadResult.get("secure_url");
            String publicId = (String) uploadResult.get("public_id");
            
            log.info("Upload video thành công: {} - Public ID: {}", secureUrl, publicId);
            
            return secureUrl;
            
        } catch (IOException e) {
            log.error("Lỗi upload video: ", e);
            throw new RuntimeException("Không thể upload video", e);
        }
    }

    /**
     * Upload single media file (image or video) with category
     */
    public String uploadMedia(MultipartFile file, String category) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        
        String contentType = file.getContentType();
        if (isValidVideoType(contentType)) {
            return uploadVideo(file, category);
        } else if (isValidImageType(contentType)) {
            return uploadImage(file, category);
        } else {
            throw new RuntimeException("Chỉ chấp nhận file ảnh (JPEG, PNG, WebP) hoặc video (MP4, MPEG, MOV, AVI, WebM)");
        }
    }

    /**
     * Upload multiple media files (images or videos) with category
     */
    public List<String> uploadMediaFiles(List<MultipartFile> files, String category) {
        List<String> uploadedUrls = new ArrayList<>();
        
        if (files == null || files.isEmpty()) {
            return uploadedUrls;
        }
        
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                String uploadedUrl = uploadMedia(file, category);
                if (uploadedUrl != null) {
                    uploadedUrls.add(uploadedUrl);
                }
            }
        }
        
        return uploadedUrls;
    }

    /**
     * Delete video by URL
     */
    public boolean deleteVideoByUrl(String videoUrl) {
        try {
            if (videoUrl == null || videoUrl.isEmpty()) {
                return true;
            }
            
            String publicId = extractPublicIdFromUrl(videoUrl);
            if (publicId == null) {
                log.warn("Không thể extract public_id từ video URL: {}", videoUrl);
                return false;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().destroy(publicId, 
                ObjectUtils.asMap("resource_type", "video"));
            boolean success = "ok".equals(result.get("result"));
            
            if (success) {
                log.info("Xóa video thành công: {}", publicId);
            } else {
                log.warn("Xóa video không thành công: {} - Result: {}", publicId, result.get("result"));
            }
            
            return success;
            
        } catch (Exception e) {
            log.error("Lỗi xóa video: {}", videoUrl, e);
            return false;
        }
    }
}