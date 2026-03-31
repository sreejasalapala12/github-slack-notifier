package com.github.tracker.github_slack_notifier.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tracker.github_slack_notifier.model.Author;
import com.github.tracker.github_slack_notifier.model.CommitRecord;
import com.github.tracker.github_slack_notifier.repository.AuthorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
            // 1. Precise extraction with safety checks
            JsonNode pusher = payload.path("pusher");
            String name = pusher.path("name").asText();
            String email = pusher.path("email").asText();

            // If name or email is missing, the JSON format from GitHub might be different
            if (name.isEmpty() || email.isEmpty()) {
                System.out.println("⚠️ Warning: Pusher data missing. Checking 'sender' node instead...");
                name = payload.path("sender").path("login").asText();
                email = name + "@github.com"; // Fallback email
            }

            // 2. Database Logic
            Author author = authorRepository.findByEmail(email).orElse(new Author());
            author.setName(name);
            author.setEmail(email);

            // 3. Extract Commits
            StringBuilder commitSummary = new StringBuilder();
            payload.path("commits").forEach(commit -> {
                commitSummary.append("- ").append(commit.path("message").asText()).append("\n");
            });

            // 4. Save to H2
            authorRepository.save(author);
            System.out.println("✅ Saved to H2: " + name);

            // 5. Send Slack (Wrapped in try-catch so it doesn't cause a 500)
            try {
                sendSlackNotification(name, commitSummary.toString());
            } catch (Exception slackEx) {
                System.err.println("❌ Slack failed, but DB is saved: " + slackEx.getMessage());
            }

            return ResponseEntity.ok("Success");
        } catch (Exception e) {
            e.printStackTrace(); // This shows the EXACT error in your IDE console
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    private void sendSlackNotification(String authorName, String messages) {
        Map<String, String> body = new HashMap<>();
        String text = "📦 *New Code Push*\n*Author:* " + authorName + "\n*Commits:*\n" + messages;
        body.put("text", text);

        restTemplate.postForEntity(slackUrl, body, String.class);
    }
}