package com.leaping.portfolio_app.auth.repository;

import com.leaping.portfolio_app.auth.model.User;
import com.leaping.portfolio_app.auth.model.UserRole;
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
        // Alice = ADMIN (full access)
        User user1 = new User(
                UUID.randomUUID().toString(),
                "alice@example.com",
                passwordEncoder.encode("password123"),
                "Alice",
                "Smith"
        );
        user1.setRole(UserRole.ADMIN);  // Alice is admin
        users.put(user1.getEmail(), user1);

        // Bob = CLIENT (limited access)
        User user2 = new User(
                UUID.randomUUID().toString(),
                "bob@example.com",
                passwordEncoder.encode("securepass456"),
                "Bob",
                "Johnson"
        );
        user2.setRole(UserRole.CLIENT);  // Bob is client (default, but explicit)
        users.put(user2.getEmail(), user2);

        // Charlie = CLIENT (limited access)
        User user3 = new User(
                UUID.randomUUID().toString(),
                "charlie@example.com",
                passwordEncoder.encode("mypassword789"),
                "Charlie",
                "Brown"
        );
        user3.setRole(UserRole.CLIENT);  // Charlie is client
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
