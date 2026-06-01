# SWP391 Git Commit Simulation & Team History Guidelines

This document provides instructions and metadata for AI agents (Gemini, Codex, etc.) to manage, backdate, and simulate commit history for the **SWP391_Project (GoalZone)** repository. Follow these guidelines to maintain a clean, collaborative, and realistic development timeline.

---

## 1. Team Members & Area of Responsibility (Backlog Ownership)

When creating or modifying code, always attribute the commit to the correct team member responsible for that feature area.

| Name | Email | Primary Responsibilities (Backlog Scope) |
| :--- | :--- | :--- |
| **BonVT** | `bonvtce181691@fpt.edu.vn` | Security configurations, Account & Auth APIs (Register, Login, Email Verification), Frontend SPA Layout/Routing. |
| **BaoNG** | `bao20048888@gmail.com` | Database schemas (`.dbml`, `.sql`), Football Field browsing, Slot Availability, Extra Service CRUD, Field/Service Issue management. |
| **NgocPA** | `ngocpace191049@gmail.com` | Core Booking lifecycle (Create Booking, Check In, Checkout, Complete/Cancel state transitions), core business logic (`MvpDemoService.java`). |
| **AnNP** | `phucan0001@icloud.com` | Payment capture (Sandbox), Refund request & approval flows, Invoice generation and record keeping. |
| **AnPTT** | `dortmund2234@gmail.com` | Promotions & Discounts validation, Membership Progress tracking, System Settings, Notification records, Reports/Utilization, Documentation. |

---

## 2. Git Commit Protocol (Simulating & Backdating)

To make commits look like they were written by individual team members at specific times, use Git environment variables when committing.

### Git Author & Committer Variables
```bash
export GIT_AUTHOR_NAME="BonVT"
export GIT_AUTHOR_EMAIL="bonvtce181691@fpt.edu.vn"
export GIT_COMMITTER_NAME="BonVT"
export GIT_COMMITTER_EMAIL="bonvtce181691@fpt.edu.vn"
export GIT_AUTHOR_DATE="2026-06-02T00:05:00"
export GIT_COMMITTER_DATE="2026-06-02T00:05:00"

git commit -m "feat(auth): enforce password complexity rules"
```

### Automation Python Script (Recommended)
You can use a temporary Python script to stage and commit files with custom backdated timestamps:

```python
import subprocess
import os

def make_commit(author_name, author_email, date_str, message, files_to_stage):
    cwd = "./" # Path to repository root
    
    # 1. Stage files
    for file in files_to_stage:
        subprocess.run(["git", "add", file], cwd=cwd, check=True)
        
    # 2. Run commit with custom environment variables
    env = os.environ.copy()
    env["GIT_AUTHOR_NAME"] = author_name
    env["GIT_AUTHOR_EMAIL"] = author_email
    env["GIT_COMMITTER_NAME"] = author_name
    env["GIT_COMMITTER_EMAIL"] = author_email
    env["GIT_AUTHOR_DATE"] = date_str
    env["GIT_COMMITTER_DATE"] = date_str
    
    subprocess.run(["git", "commit", "-m", message], cwd=cwd, env=env, check=True)
```

---

## 3. Recommended History Windows

*   **Baseline Commits (Sprint 1/MVP):** Distributed between **May 30, 2026** and **June 2, 2026** (roughly 1 to 3 days ago relative to current date).
*   **Sequential Ordering:** Always ensure that subsequent commits have a later timestamp than their ancestors to avoid chronological inconsistencies in the Git graph.
*   **Branching:** All code changes and feature iterations must be committed to the `develop` branch, then pushed using `git push origin develop`.

---

## 4. Coding Standards

*   **No Backlog Ownership Comments:** Do not place developer-specific annotations in the source code files (e.g. `// Backlog owner: ...` or `// BonVT: ...`). Keep all metadata confined to `docs/IMPLEMENTED_USE_CASES.md` and this document.
*   **Real Email Delivery:** Real SMTP or Java Mail Sender integrations should be committed under **BonVT**'s credentials. Simulated email delivery should print to standard backend output rather than creating in-app user notifications.
