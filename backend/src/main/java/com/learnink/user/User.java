package com.learnink.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "app_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 180)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean mustChangePassword;

    /** Source de données de l'utilisateur : détermine ses exercices et la base sur laquelle ses requêtes s'exécutent. */
    @Column(nullable = false, columnDefinition = "integer default 1")
    private int datasource = 1;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {
    }

    public User(String email, String passwordHash, Role role) {
        this.email = email.toLowerCase();
        this.passwordHash = passwordHash;
        this.role = role;
        this.mustChangePassword = true;
        this.createdAt = Instant.now();
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.mustChangePassword = false;
    }

    public void assignDatasource(int datasource) {
        this.datasource = datasource;
    }

    public void resetPassword(String temporaryPasswordHash) {
        this.passwordHash = temporaryPasswordHash;
        this.mustChangePassword = true;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public int getDatasource() {
        return datasource;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
