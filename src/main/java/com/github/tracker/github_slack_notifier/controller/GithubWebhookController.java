package com.github.tracker.github_slack_notifier.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tracker.github_slack_notifier.model.Author;
import com.github.tracker.github_slack_notifier.repository.AuthorRepository;
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

    @Value("${slack.webhook.url}")
    private String slackUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/webhook")
    public ResponseEntity<String> handlePush(@RequestBody JsonNode payload) {
        try {
            // 1. ROBUST EXTRACTION LOGIC
            // Priority 1: head_commit (The person who actually committed the code)
            String name = payload.path("head_commit").path("author").path("name").asText();
            String email = payload.path("head_commit").path("author").path("email").asText();

            // Priority 2: pusher (The person who pushed to GitHub)
            if (name.isEmpty() || email.isEmpty()) {
                name = payload.path("pusher").path("name").asText();
                email = payload.path("pusher").path("email").asText();
            }

            // Priority 3: sender (Fallback to GitHub username)
            if (name.isEmpty()) {
                System.out.println("⚠️ Warning: Committer/Pusher data missing. Using 'sender' login.");
                name = payload.path("sender").path("login").asText();
                email = name + "@github.com";
            }

            // 2. DATABASE LOGIC (H2)
            // Check if author exists, otherwise create new
            Author author = authorRepository.findByEmail(email).orElse(new Author());
            author.setName(name);
            author.setEmail(email);
            authorRepository.save(author);
            System.out.println("✅ Database Updated for: " + name);

            // 3. EXTRACT COMMIT MESSAGES
            StringBuilder commitSummary = new StringBuilder();
            JsonNode commits = payload.path("commits");
            if (commits.isArray() && commits.size() > 0) {
                commits.forEach(commit -> {
                    commitSummary.append("• ").append(commit.path("message").asText()).append("\n");
                });
            } else {
                commitSummary.append("_No detailed commit messages found (likely a Web UI edit or Tag)_");
            }

            // 4. SEND SLACK NOTIFICATION
            try {
                sendSlackNotification(name, commitSummary.toString());
                System.out.println("🚀 Slack notification sent successfully.");
            } catch (Exception slackEx) {
                // If Slack fails (404/no_service), the API still returns 200 because DB saved
                System.err.println("❌ Slack failed (check your Webhook URL): " + slackEx.getMessage());
            }

            return ResponseEntity.ok("Webhook processed and data saved to H2.");

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Internal Server Error: " + e.getMessage());
        }
    }

    private void sendSlackNotification(String authorName, String messages) {
        Map<String, String> body = new HashMap<>();

        // Formatting the text for Slack with some emoji flair
        String formattedText = "📦 *New Code Push Detected*\n" +
                "👤 *Author:* " + authorName + "\n" +
                "📝 *Commits:*\n" + messages;

        body.put("text", formattedText);

        // Sending the POST request to the Slack Webhook URL hi
        //jihj
        restTemplate.postForEntity(slackUrl, body, String.class);
    }
}