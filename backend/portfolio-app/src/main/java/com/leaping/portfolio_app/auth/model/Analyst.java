package com.leaping.portfolio_app.auth.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Analyst entity - represents analyst users with system-wide data analysis access.
 * Child of USERS (1:1) via @OneToOne @MapsId relationship.
 * Maps to analysts table in database.
 */
@Entity
@Table(name = "analysts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Analyst {

    /**
     * Primary Key = Foreign Key to users.user_id
     * Automatically derived from User relationship via @MapsId
     */
    @Id
    @Column(name = "user_id")
    private Long userId;

    /**
     * One-to-one relationship with User
     * @MapsId automatically maps userId PK to User.id FK
     * DO NOT manually set userId in constructor - use setUser() instead
     */
    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;
}
