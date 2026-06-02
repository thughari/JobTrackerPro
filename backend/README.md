# JobTrackerPro API 🚀

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2-green)
![Docker](https://img.shields.io/badge/Docker-Enabled-blue)
![License](https://img.shields.io/badge/License-MIT-purple)

The robust, enterprise-grade backend for **JobTrackerPro**. This REST API handles secure authentication, job application data management, high-performance analytics, and cloud file storage.

## 🔗 Quick Links

*   **Live Application:** [https://jobtrackerpro.in](https://jobtrackerpro.in)
*   **Frontend Repository:** [github.com/thughari/JobTrackerPro/tree/main/frontend](https://github.com/thughari/JobTrackerPro/tree/main/frontend)
*   **API Base URL:** [jobtracker-service-963261513098.asia-south1.run.app](https://jobtracker-service-963261513098.asia-south1.run.app)

## ✨ Key Features

*   **🔐 Secure Authentication:** Hybrid support for Google & GitHub OAuth2 alongside standard Email/Password, secured via JWT.
*   **☁️ Cloud Native Storage:** Integrates with **Cloudflare R2** for efficient user avatar storage and social image syncing.
*   **⚡ Performance:** Multi-threaded analytics using Java `CompletableFuture` and optimized JPQL queries.
*   **🛡️ Robust Error Handling:** Global Exception Handler returning standardized JSON error responses.

## 🏗️ System Architecture

The JobTrackerPro backend features a hybrid, high-performance automated application tracking architecture. It intercepts standard application confirmations (Indeed & LinkedIn) to process them locally, while delegating complex transactional emails to AI models.

### Sync Ingestion & Extraction Pipeline

```mermaid
flowchart TD
    %% Styling
    classDef service fill:#4A90E2,stroke:#357ABD,stroke-width:1.5px,color:#fff;
    classDef utility fill:#50E3C2,stroke:#34A790,stroke-width:1.5px,color:#333;
    classDef storage fill:#F5A623,stroke:#C68015,stroke-width:1.5px,color:#fff;
    classDef trigger fill:#D0021B,stroke:#9E0010,stroke-width:1.5px,color:#fff;

    A[Gmail Sync Push / Webhook] :::trigger --> B(GmailWebhookService) :::service
    B --> C{SmartExtractionService} :::service
    
    %% Template Ingest Pathway
    C -->|LinkedIn / Indeed Template| D(TemplateParser) :::utility
    D -->|Extract Role & Company| E{Body Template Matches?}
    E -->|Yes| F[Extract from Body & Clean Location] :::utility
    E -->|No| G[Extract from Subject & Find Location] :::utility
    D -->|Extract & Clean URL| H[UrlParser: Decode next= Parameter] :::utility
    D -->|Determine Status| I[Strip Safety Warnings & Scan Keywords] :::utility
    
    %% AI Ingest Pathway
    C -->|Unmatched Emails| J{app.gemini.enabled?}
    J -->|true| K(GeminiExtractionService) :::service
    J -->|false| L(MockGeminiService) :::service
    
    %% Job Matching & Storage
    F --> M(JobService: createOrUpdateJob) :::service
    G --> M
    K --> M
    L --> M
    
    M --> N{findBestMatch} :::service
    N -->|Stricter Token Matching for Short Names| O[Update Existing Job & Append Notes] :::storage
    N -->|No Active Match| P[Create New Job Entry] :::storage
    
    O --> Q[(Database: PostgreSQL)] :::storage
    P --> Q
    Q --> R(Caffeine Cache Eviction) :::service
```

### Core Architecture Components

1. **Inbound Webhook (`GmailWebhookService`)**:
   - Handles push notifications from Google Cloud Pub/Sub notifying the system of user mailbox updates. Reads batch email payloads asynchronously using Virtual Threads (`spring.threads.virtual.enabled=true`).

2. **Template Router Proxy (`SmartExtractionService`)**:
   - Acts as a `@Primary` decorator for `GeminiService`. Before routing an email payload to external AI services, it runs local template checkers to determine if the email originates from a supported platform (LinkedIn or Indeed).
   - If the templates match, it handles parsing locally, saving API bill costs and minimizing latency.

3. **Manual Extraction Engine (`TemplateParser` & `UrlParser`)**:
   - **Forward Headers Parsing**: Reconstructs `From` and `Subject` details from forwarded messages, enabling users to forward confirmation emails directly to their sync address.
   - **Indeed Body Layout Parser**: Prefers body-based matching over subject strings to correctly parse hyphenated roles (e.g. `Java Backend Developer - MS` where `MS` is part of the job title and not the company).
   - **Footer Disclaimer Filter**: Strips security and safety notices (e.g., *"Never share financial info without an interview"*) to prevent status classification pollution.
   - **URL Query Decelerator**: Decodes target parameters (like `next=`) from redirected links to store clean, direct job posting links (reducing character sizes from 220+ to ~50).
   - **Review Suffix Stripper**: Cleans locations dynamically by removing reviews meta-text (e.g., `Remote 95 reviews` -> `Remote`).

4. **AI & Mock Fallbacks (`GeminiExtractionService` / `MockGeminiService`)**:
   - Emails that do not follow standard layout structures are forwarded to Gemini 2.5 Flash Lite or mocked locally depending on configuration profiles.

5. **Entity De-duplication Logic (`JobService`)**:
   - Instead of blindly creating new application entries, `JobService.findBestMatch()` queries existing listings.
   - **Strict Word Token Matching**: Prevents short company names (like `MS`) from triggering loose substring matches on other database entries (like `ORION SYSTEMS`). If either company name is $\le 3$ characters, they must share an exact word-token match.
   - Matches are updated in-place with appended transaction logs, while new ones trigger fresh inserts.

---

## 🛠️ Tech Stack

*   **Core:** Java 21, Spring Boot 3
*   **Database:** MySQL (Google Cloud SQL) / Supabase (PostgreSQL)
*   **Security:** Spring Security 6, OAuth2 Client, JJWT
*   **Storage:** AWS SDK v2 (Cloudflare R2)
*   **Deployment:** Docker, Google Cloud Run, CI/CD via GitHub Actions

## ⚙️ Environment Variables

| Variable | Description |
| :--- | :--- |
| `JDBC_URL` | Database Connection URL |
| `JWT_SECRET` | 256-bit Secret Key for signing tokens |
| `GOOGLE_CLIENT_ID` | OAuth2 Client ID |
| `CLOUDFLARE_ENDPOINT` | R2 S3 API Endpoint |
| `APP_UI_URL` | `https://jobtrackerpro.in` |

## 🛠️ Development & Caching

The backend is architected to be **Contributor Friendly.**

### Spring Profiles
- **`local` (Default):** Uses Postgres in Docker, MailHog for emails, Mock Gemini AI, and Local Storage.
- **`prod`:** Used for Cloud Run deployment with real Gemini, R2, and SMTP settings.

### 💾 High-Performance Caching
I am using **Caffeine Cache** (highest performance in-memory cache for Java) to optimize:
- **User Profiles:** Cached by email to reduce Supabase hits.
- **Dashboard Stats:** Cached and automatically evicted on job updates.
- **Job Lists:** Leverages Spring's `@Cacheable` abstraction.

### 🤖 AI & Storage Mocking
If `app.gemini.enabled=false` (default in local), the system uses `MockGeminiService`. It generates realistic job data from any email you "forward" to it, allowing you to test UI and logic without a Google AI Key.

Similarly, images are saved to the `/backend/uploads` folder instead of Cloudflare R2 during local development.

## 📄 License
MIT License
