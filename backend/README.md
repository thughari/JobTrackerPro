# JobTrackerPro API 🚀

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2-green)
![Docker](https://img.shields.io/badge/Docker-Enabled-blue)
![License](https://img.shields.io/badge/License-MIT-purple)

The robust, enterprise-grade backend for **JobTrackerPro**. This REST API handles secure authentication, job application data management, high-performance analytics, and cloud file storage.

## 🔗 Quick Links

*   **Live Application:** [https://thughari.github.io/JobTrackerPro-UI](https://thughari.github.io/JobTrackerPro-UI)
*   **Frontend Repository:** [github.com/thughari/JobTrackerPro-UI](https://github.com/thughari/JobTrackerPro-UI)
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
| `APP_UI_URL` | `https://thughari.github.io/JobTrackerPro-UI` |

## 🚀 Getting Started

1.  **Clone the repository**
    ```bash
    git clone https://github.com/thughari/JobTrackerPro.git
    cd JobTrackerPro
    ```
2.  **Configure Application**
    Update `src/main/resources/application-prod.properties` or set system Env Vars.
3.  **Run the App**
    ```bash
    mvn spring-boot:run
    ```

## 📄 License
MIT License
