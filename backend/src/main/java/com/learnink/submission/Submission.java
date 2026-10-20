package com.learnink.submission;

import com.learnink.exercise.Exercise;
import com.learnink.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** Tentative d'un étudiant sur un exercice, avec la note obtenue. */
@Entity
@Table(name = "submission", indexes = @Index(name = "idx_submission_user_exercise", columnList = "user_id, exercise_id"))
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id")
    private Exercise exercise;

    @Column(nullable = false, length = 10000)
    private String query;

    @Column(nullable = false)
    private double score;

    @Column(nullable = false)
    private boolean correct;

    private Double executionTimeMs;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Submission() {
    }

    public Submission(User user, Exercise exercise, String query, double score, boolean correct,
                      Double executionTimeMs) {
        this.user = user;
        this.exercise = exercise;
        this.query = query;
        this.score = score;
        this.correct = correct;
        this.executionTimeMs = executionTimeMs;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getQuery() {
        return query;
    }

    public double getScore() {
        return score;
    }

    public boolean isCorrect() {
        return correct;
    }

    public Double getExecutionTimeMs() {
        return executionTimeMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
