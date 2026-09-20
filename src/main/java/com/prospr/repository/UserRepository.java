package com.prospr.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.prospr.model.User;

public interface UserRepository extends MongoRepository<User, String> {

    boolean existsByEmail(String email);

    boolean existsByMobile(String mobile);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    Optional<User> findByMobile(String mobile);
}
