package com.github.tracker.github_slack_notifier.repository;

import com.github.tracker.github_slack_notifier.model.CommitRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommitRepository extends JpaRepository<CommitRecord, Long> {
}