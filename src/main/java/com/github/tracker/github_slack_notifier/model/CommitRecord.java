package com.github.tracker.github_slack_notifier.model;

import jakarta.persistence.*;

@Entity
public class CommitRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String message;

    @ManyToOne
    @JoinColumn(name = "author_id")
    private Author author;

    // Standard Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Author getAuthor() { return author; }
    public void setAuthor(Author author) { this.author = author; }
}