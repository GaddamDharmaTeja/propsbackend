package com.prospr.service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.prospr.dto.CreateUserRequest;
import com.prospr.model.User;
import com.prospr.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User createUser(CreateUserRequest request) {

        request.setEmail(request.getEmail().trim().toLowerCase(Locale.ROOT));

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        if (userRepository.existsByMobile(request.getMobile())) {
            throw new RuntimeException("Mobile number already registered");
        }

        User user = new User();

        user.setId(UUID.randomUUID().toString());

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setMobile(request.getMobile());
        user.setEmail(request.getEmail());
        user.setDob(request.getDob());
        user.setGender(request.getGender());
        String baseUsername = request.getUsername() == null || request.getUsername().isBlank()
                ? request.getEmail().substring(0, request.getEmail().indexOf('@')) : request.getUsername();
        String username = baseUsername.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "");
        if (username.length() < 3) username = "member" + user.getId().substring(0, 6);
        if (userRepository.existsByUsername(username)) throw new RuntimeException("Username already registered");
        user.setUsername(username);
        user.setHouseholdId(user.getId());
        user.setMemberId(user.getId());
        user.setHouseholdCreator(true);

        // NEVER store plain-text password
        user.setPassword(
                passwordEncoder.encode(request.getPassword())
        );

        LocalDateTime now = LocalDateTime.now();

        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        return userRepository.save(user);
    }
}
