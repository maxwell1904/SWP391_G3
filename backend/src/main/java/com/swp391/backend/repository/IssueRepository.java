package com.swp391.backend.repository;

import com.swp391.backend.entity.Issue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueRepository extends JpaRepository<Issue, Long> {
    List<Issue> findAllByOrderByIssueIdDesc();
}
