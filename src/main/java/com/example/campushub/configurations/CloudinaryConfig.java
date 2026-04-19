package com.example.campushub.configurations;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.cloudinary.Cloudinary;

import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "cloudinary")
@Data
public class CloudinaryConfig {
    private String cloudName;
    private String apiKey;
    private String apiSecret;

    @Bean
    @Primary
    public Cloudinary cloudinary() {
        Map<String, String> config = new HashMap<>();
        config.put("cloud_name", cloudName);
        config.put("api_key", apiKey);
        config.put("api_secret", apiSecret);
        return new Cloudinary(config);
    }
}
