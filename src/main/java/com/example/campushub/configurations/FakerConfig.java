package com.example.campushub.configurations;

import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.datafaker.Faker;

@Configuration
public class FakerConfig {

    @Bean
    public Faker faker() {
        // Khởi tạo Faker với Locale tiếng Việt
        return new Faker(new Locale("vi", "VN"));
    }
}
