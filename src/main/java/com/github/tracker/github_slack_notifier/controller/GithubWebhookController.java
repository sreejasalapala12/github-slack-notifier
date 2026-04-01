package com.github.tracker.github_slack_notifier.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tracker.github_slack_notifier.model.Author;
import com.github.tracker.github_slack_notifier.model.CommitRecord;
import com.github.tracker.github_slack_notifier.repository.AuthorRepository;
import com.github.tracker.github_slack_notifier.repository.CommitRepository; // Added
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/github")
public class GithubWebhookController {

    @Autowired
    private AuthorRepository authorRepository;

    @Autowired
    private CommitRepository commitRepository; // Added

    @Value("${slack.webhook.url}")
    private String slackUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/webhook")
    public ResponseEntity<String> handlePush(@RequestBody JsonNode payload) {
        try {
            // 1. ROBUST EXTRACTION LOGIC
            String name = payload.path("head_commit").path("author").path("name").asText();
            String email = payload.path("head_commit").path("author").path("email").asText();

            if (name.isEmpty() || email.isEmpty()) {
                name = payload.path("pusher").path("name").asText();
                email = payload.path("pusher").path("email").asText();
            }

            if (name.isEmpty()) {
                name = payload.path("sender").path("login").asText();
                email = name + "@github.com";
            }

            // 2. AUTHOR LOGIC
            Author author = authorRepository.findByEmail(email).orElse(new Author());
            author.setName(name);
            author.setEmail(email);
            authorRepository.save(author);

            // 3. COMMIT LOGIC (Saving to DB & Building Slack Msg)
            StringBuilder slackCommitMsg = new StringBuilder();
            JsonNode commits = payload.path("commits");

            if (commits.isArray() && commits.size() > 0) {
                commits.forEach(commitNode -> {
                    String msg = commitNode.path("message").asText();

                    // Save to H2
                    CommitRecord record = new CommitRecord();
                    record.setMessage(msg);
                    record.setAuthor(author);
                    commitRepository.save(record); // ACTUAL SAVE CALL

                    slackCommitMsg.append("• ").append(msg).append("\n");
                });
            } else {
                slackCommitMsg.append("_No detailed commit messages found_");
            }

            // 4. SEND SLACK NOTIFICATION
            try {
                sendSlackNotification(name, slackCommitMsg.toString());
                System.out.println(" Slack notification sent and commits saved for: " + name);
            } catch (Exception slackEx) {
                System.err.println(" Slack failed, but DB is saved: " + slackEx.getMessage());
            }

            return ResponseEntity.ok("Success");

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    private void sendSlackNotification(String authorName, String messages) {
        Map<String, String> body = new HashMap<>();
        String formattedText = "*New Code Push Detected*\n" +
                "*Author:* " + authorName + "\n" +
                "*Commits:*\n" + messages;
        body.put("text", formattedText);
        restTemplate.postForEntity(slackUrl, body, String.class);
    }
}