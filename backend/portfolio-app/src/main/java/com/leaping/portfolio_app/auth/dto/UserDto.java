package com.leaping.portfolio_app.auth.dto;

public class UserDto {
    private String id;
    private String email;
<<<<<<< HEAD
    private String firstName;
    private String lastName;
=======
>>>>>>> b71bbdb95a93ad930c47a96f85470589bfdad665

    public UserDto() {
    }

<<<<<<< HEAD
    public UserDto(String id, String email, String firstName, String lastName) {
        this.id = id;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
=======
    public UserDto(String id, String email) {
        this.id = id;
        this.email = email;
>>>>>>> b71bbdb95a93ad930c47a96f85470589bfdad665
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

<<<<<<< HEAD
    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

=======
>>>>>>> b71bbdb95a93ad930c47a96f85470589bfdad665
}
