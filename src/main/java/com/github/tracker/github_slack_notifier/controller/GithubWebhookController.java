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
            // 1. Extract Author from "pusher" object
            String name = payload.path("pusher").path("name").asText();
            String email = payload.path("pusher").path("email").asText();

            Author author = authorRepository.findByEmail(email).orElse(new Author());
            author.setName(name);
            author.setEmail(email);

            // 2. Extract Commits and build a summary string
            StringBuilder commitMessages = new StringBuilder();
            JsonNode commitsNode = payload.path("commits");

            for (JsonNode node : commitsNode) {
                CommitRecord record = new CommitRecord();
                record.setMessage(node.path("message").asText());
                record.setAuthor(author);
                author.getCommits().add(record);

                commitMessages.append("• ").append(record.getMessage()).append("\n");
            }

            // 3. Save to H2 Database
            authorRepository.save(author);

            // 4. Send Slack Notification
            sendSlackNotification(name, commitMessages.toString());

            return ResponseEntity.ok("Webhook processed successfully");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    private void sendSlackNotification(String authorName, String messages) {
        Map<String, String> body = new HashMap<>();
        String text = "📦 *New Code Push*\n*Author:* " + authorName + "\n*Commits:*\n" + messages;
        body.put("text", text);

        restTemplate.postForEntity(slackUrl, body, String.class);
    }
}