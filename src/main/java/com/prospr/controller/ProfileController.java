package com.prospr.controller;

import com.prospr.dto.AuthResponse;
import com.prospr.model.User;
import com.prospr.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/profile")
@CrossOrigin(origins = "${PROSPR_WEB_ORIGIN:http://localhost:3000}")
public class ProfileController {

    private final UserRepository users;
    private final PasswordEncoder passwords;

    public ProfileController(UserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    @GetMapping
    public AuthResponse.UserProfile me(@AuthenticationPrincipal String account) {
        return profile(load(account));
    }

    @PutMapping
    public AuthResponse.UserProfile update(@AuthenticationPrincipal String account, @RequestBody Map<String, String> body) {
        User user = load(account);
        if (body.get("firstName") != null) user.setFirstName(body.get("firstName").trim());
        if (body.get("lastName") != null) user.setLastName(body.get("lastName").trim());
        if (body.get("mobile") != null && !body.get("mobile").isBlank()) user.setMobile(body.get("mobile").trim());
        if (body.get("email") != null && !body.get("email").isBlank()) user.setEmail(body.get("email").trim().toLowerCase(Locale.ROOT));
        if (body.get("gender") != null) user.setGender(body.get("gender"));
        if (body.get("dob") != null && !body.get("dob").isBlank()) user.setDob(LocalDate.parse(body.get("dob")));
        user.setUpdatedAt(LocalDateTime.now());
        return profile(users.save(user));
    }

    @PutMapping("/password")
    public Map<String, String> password(@AuthenticationPrincipal String account, @RequestBody Map<String, String> body) {
        User user = load(account);
        String current = body.getOrDefault("currentPassword", "");
        String next = body.getOrDefault("newPassword", "");
        if (!passwords.matches(current, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect.");
        }
        if (next.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must be at least 8 characters.");
        }
        user.setPassword(passwords.encode(next));
        user.setUpdatedAt(LocalDateTime.now());
        users.save(user);
        return Map.of("message", "Password updated.");
    }

    private User load(String account) {
        if (account == null || account.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in is required.");
        }
        return users.findById(account).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Your account no longer exists."));
    }

    private static AuthResponse.UserProfile profile(User user) {
        return new AuthResponse.UserProfile(
                user.getId(),
                user.getMemberId(),
                user.getHouseholdId(),
                (user.getFirstName() + " " + user.getLastName()).trim(),
                user.getUsername(),
                user.getEmail(),
                user.getMobile(),
                user.getGender(),
                user.isHouseholdCreator()
        );
    }
}
