package com.leaping.portfolio_app.auth.repository;

import com.leaping.portfolio_app.auth.model.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserRepository {

    private final Map<String, User> users = new HashMap<>();
    private final PasswordEncoder passwordEncoder;

    public UserRepository(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
        initializeTestUsers();
    }

    private void initializeTestUsers() {
        // Initialize with test users
        User user1 = new User(
                UUID.randomUUID().toString(),
                "alice@example.com",
                passwordEncoder.encode("password123"),
                "Alice",
                "Smith"
        );
        users.put(user1.getEmail(), user1);

        User user2 = new User(
                UUID.randomUUID().toString(),
                "bob@example.com",
                passwordEncoder.encode("securepass456"),
                "Bob",
                "Johnson"
        );
        users.put(user2.getEmail(), user2);

        User user3 = new User(
                UUID.randomUUID().toString(),
                "charlie@example.com",
                passwordEncoder.encode("mypassword789"),
                "Charlie",
                "Brown"
        );
        users.put(user3.getEmail(), user3);
    }

    public Optional<User> findByEmail(String email) {
        return Optional.ofNullable(users.get(email));
    }

    public Optional<User> findById(String id) {
        return users.values().stream()
                .filter(u -> u.getId().equals(id))
                .findFirst();
    }

    public User save(User user) {
        if (user.getId() == null) {
            user.setId(UUID.randomUUID().toString());
        }
        users.put(user.getEmail(), user);
        return user;
    }

    public boolean existsByEmail(String email) {
        return users.containsKey(email);
    }
}
