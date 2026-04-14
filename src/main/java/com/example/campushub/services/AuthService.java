package com.example.campushub.services;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.campushub.components.JwtTokenProvider;
import com.example.campushub.dtos.auth.LoginDTO;
import com.example.campushub.dtos.auth.RegisterDTO;
import com.example.campushub.exceptions.DataNotFoundException;
import com.example.campushub.models.jpa.User;
import com.example.campushub.repositories.jpa.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final JwtTokenProvider jwtTokenProvider;

    public void register(RegisterDTO registerDTO) throws Exception {
        if(userRepository.existsByEmail(registerDTO.getEmail())) {
            throw new Exception("Email already exists");
        }

        User newUser = User.builder()
            .fullName(registerDTO.getFullname())
            .email(registerDTO.getEmail())
            .password(passwordEncoder.encode(registerDTO.getPassword()))
            .build();

        userRepository.save(newUser);
    }

    public String login(LoginDTO loginDTO) throws Exception {
        User user = userRepository.findByEmail(loginDTO.getEmail())
            .orElseThrow(() -> new DataNotFoundException("Invalid email or password"));

        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new DataNotFoundException("Invalid email or password");
        }

        return jwtTokenProvider.generateToken(user);
    }
}
