# Jenkins Setup Guide

## Prerequisites

- Jenkins LTS (2.440+) with Docker installed on the agent
- Java 21 and Maven 3.9 available on the agent
- Python 3.12 available on the agent
- Plugins: Pipeline, AnsiColor, JUnit, Docker Pipeline, Credentials Binding, Slack Notification

## 1. Create Required Credentials

In **Manage Jenkins → Credentials → System → Global credentials**:

| ID | Type | Description |
|----|------|-------------|
| `docker-hub-credentials` | Username/Password | Docker Hub login |
| `slack-webhook` | Secret text | Slack incoming webhook URL |

## 2. Configure Environment Variables

In **Manage Jenkins → Configure System → Global properties → Environment variables**:

| Variable | Value |
|----------|-------|
| `SLACK_WEBHOOK_URL` | Your Slack webhook URL |

## 3. Create the Pipeline Job

1. **New Item** → name it `demand-forecast-platform` → select **Pipeline**
2. Under **Pipeline**:
   - Definition: `Pipeline script from SCM`
   - SCM: Git → your repo URL
   - Branch: `*/main`
   - Script Path: `demand-forecast-platform/Jenkinsfile`
3. Enable **GitHub hook trigger for GITScm polling** (if using GitHub webhook)

## 4. Pipeline Stages Explained

```
Checkout → Test (parallel) → Build Docker Images → Push to Docker Hub → Deploy
```

| Stage | What it does |
|-------|-------------|
| Checkout | Clones the repo, shows last 5 commits |
| Test | Runs all 6 services in parallel (mvn test + pytest) |
| Build Docker Images | Builds 6 Docker images in parallel |
| Push to Docker Hub | Pushes with `:BUILD_NUMBER` and `:latest` tags (main only) |
| Deploy | Runs `docker compose up -d` then `scripts/health-check.sh` (main only) |

## 5. Triggering Builds

**Manual:** Click *Build Now* in Jenkins UI

**GitHub webhook:**
1. Go to GitHub repo → Settings → Webhooks → Add webhook
2. Payload URL: `http://<jenkins-host>/github-webhook/`
3. Content type: `application/json`
4. Events: *Just the push event*

## 6. Slack Notifications

The Jenkinsfile uses `SLACK_WEBHOOK_URL` env var (Slack incoming webhook format).
To get a webhook: Slack → App Directory → Incoming Webhooks → Add to Slack.

## 7. Docker Build Caching

The Jenkins agent uses the local Docker daemon for caching — if you rebuild on the
same agent the layer cache speeds up Java builds significantly (Maven layers are large).
For multi-agent setups, configure a Docker registry cache or use BuildKit's remote cache.
