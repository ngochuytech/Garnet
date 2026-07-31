package com.example.campushub.services;

import java.util.Date;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MediaStorageService {
    private final AmazonS3 s3Client;
    
    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    public String generatePresignedUrl(String originalFilename, String folderName){
        String extension = "";
        int dotIndex = originalFilename.lastIndexOf(".");
        if (dotIndex >= 0) {
            extension = originalFilename.substring(dotIndex);
        }
        
        String uniqueFileName = folderName + "/" + UUID.randomUUID().toString() + extension;

        // Set expiration 15 minutes from now
        Date expiration = new Date();
        long expTimeMillis = expiration.getTime() + (1000 * 60 * 15);
        expiration.setTime(expTimeMillis);

        GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(bucketName, uniqueFileName)
            .withMethod(HttpMethod.PUT)
            .withExpiration(expiration);
        
        return s3Client.generatePresignedUrl(generatePresignedUrlRequest).toString();
    }
}
