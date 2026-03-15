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
