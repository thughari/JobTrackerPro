# JobTrackerPro 🚀

![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.4-green?style=for-the-badge&logo=spring-boot)
![Angular](https://img.shields.io/badge/Angular-17-red?style=for-the-badge&logo=angular)
![Tailwind](https://img.shields.io/badge/Tailwind-CSS-blue?style=for-the-badge&logo=tailwindcss)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15+-blue?style=for-the-badge&logo=postgresql)
![Docker](https://img.shields.io/badge/Docker-Enabled-2496ED?style=for-the-badge&logo=docker)
![License](https://img.shields.io/badge/License-MIT-purple?style=for-the-badge)

> **"I stopped using Excel to track my job applications. I architected an Enterprise-Grade platform instead."**

**JobTrackerPro** is a robust, full-stack solution designed to visualize, automate, and manage the interview pipeline. Unlike simple CRUD tutorials, this project demonstrates **Advanced System Design**, **Cloud-Native Architecture**, and **Production-Grade Security**.

---

## 🔗 Quick Links

| 🚀 **Live Demo** | 📂 **Source Code** |
|:---:|:---:|
| [**Launch App**](https://thughari.github.io/JobTrackerPro) | [**GitHub Repo**](https://github.com/thughari/JobTrackerPro) |

---

## 🧠 System Architecture

This application is built as a distributed system focusing on separation of concerns, data integrity, and automation.

### 1. 🤖 AI-Powered Email Ingestion Pipeline
I engineered an event-driven pipeline to eliminate manual data entry using **Google Gemini 2.0 Flash**.

*   **Smart Upsert:** Uses fuzzy matching and Jaccard Similarity to detect if an incoming email is a *new application* or an *interview update* for an existing job.
*   **Resilience:** Gracefully handles unstructured data, HTML-only emails, and missing headers using regex fallback strategies.

### 2. 🔐 Hybrid Security Architecture
*   **Implementation:** Custom `OAuth2SuccessHandler` that merges identities based on trusted email verification.
*   **Flow:** Users can log in via **Google/GitHub** OR **Email/Password** interchangeably without creating duplicate accounts or data silos.
*   **Stateless:** Fully secured via **JWT (RS256)** with a custom Security Filter Chain.

### 3. ☁️ Atomic Cloud Storage
*   **Provider:** Cloudflare R2 (AWS S3 Compatible).
*   **Transactional Integrity:** Profile updates are atomic. If a user uploads a new image but the database transaction fails, the image upload is rolled back.
*   **Garbage Collection:** The system automatically issues delete commands for old/orphaned images in the R2 bucket when a user updates their photo, preventing storage leaks and reducing costs.

### 4. ⚡ High-Performance Analytics
*   **Backend:** Leverages **Java Stream API** for efficient in-memory aggregation of job statistics, reducing database hits to a single optimized read operation per dashboard load.
*   **Frontend:** Uses **Angular Signals** for reactive state management and **Optimistic UI** updates, ensuring zero-latency feedback for the user even on slow networks.

---

## 🛠️ The Tech Stack

| Domain | Technology | Key Usage |
| :--- | :--- | :--- |
| **Backend** | **Java 21** | Modern JVM features (Records, Pattern Matching) |
| **Framework** | **Spring Boot 3.4** | REST API, Security, Data JPA |
| **Database** | **PostgreSQL** | Supabase managed instance (Transaction Mode) |
| **AI Model** | **Gemini 2.0 Flash** | Intelligent email parsing |
| **Storage** | **Cloudflare R2** | S3-compatible object storage |
| **Frontend** | **Angular 17** | Signals, Standalone Components, Optimistic UI |
| **Styling** | **TailwindCSS** | Utility-first styling, Dark Mode |
| **DevOps** | **Docker & Cloud Run** | Containerized serverless deployment |

---

## 📂 Project Structure

The repository is structured as a Monorepo:

```text
JobTrackerPro/
├── backend/            # Spring Boot API
│   ├── src/main/java/  # Controllers, Services, DTOs
│   ├── Dockerfile      # Backend Container Config
│   └── service.yaml    # Google Cloud Run Config
├── frontend/           # Angular UI
│   ├── src/app/        # Components, Services, Guards
│   └── tailwind.config # CSS Configuration
└── README.md           # Documentation
```

---

## 🚀 Getting Started Locally

### Prerequisites
*   Java 21 JDK
*   Node.js v18+
*   PostgreSQL or MySQL Database

### 1. Clone the Repo
```bash
git clone https://github.com/thughari/JobTrackerPro.git
cd JobTrackerPro
```

### 2. Backend Setup
Navigate to the backend folder.

```bash
cd backend
# Create .env file with the variables below
# Run with Maven wrapper
./mvnw spring-boot:run
```

<details>
<summary>📋 <strong>Required Environment Variables (.env)</strong></summary>

```properties
# --- Database ---
JDBC_URL=jdbc:postgresql://localhost:5432/jobtracker
JDBC_USER=postgres
JDBC_PASS=password

# --- Security ---
JWT_SECRET=256bit_secret_key

# --- OAuth2 ---
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...
GITHUB_CLIENT_ID=...
GITHUB_CLIENT_SECRET=...

# --- AI & Cloud ---
GEMINI_API_KEY=...
CLOUDFLARE_ACCESS_KEY=...
CLOUDFLARE_SECRET_KEY=...
CLOUDFLARE_ENDPOINT=...
CLOUDFLARE_BUCKET=jobtracker-avatars
CLOUDFLARE_PUBLIC_URL=https://r2-domain.dev

# --- Email (Brevo/SMTP) ---
EMAIL_HOST=smtp-relay.brevo.com
EMAIL_PORT=587
EMAIL_USER_NAME=...
EMAIL_SMTP_KEY=...
EMAIL_SENDER=haribabu.thatikonda3@gmail.com
EMAIL_SENDER_NAME=JobTrackPro
```
</details>

### 3. Frontend Setup
Navigate to the frontend folder.

```bash
cd ../frontend
npm install
ng serve
```
Open `http://localhost:4200` in your browser.

---

## 📸 Screenshots

| **Interactive Dashboard** | **Profile & Automation** |
|:---:|:---:|
| ![Dashboard](https://github.com/user-attachments/assets/ce478fad-c855-4f43-a16c-ab305bb8041f) | ![Profile](https://github.com/user-attachments/assets/de7baf3a-e7a2-4317-b49d-3f24669ec089) |
| *Real-time D3.js analytics and charts* | *Email forwarding setup and secure settings* |

---

## 🤝 Contributing

Contributions are welcome!

1.  **Fork** the repository.
2.  Create a **Feature Branch** (`git checkout -b feature/AmazingFeature`).
3.  **Commit** your changes.
4.  **Push** to the branch.
5.  Open a **Pull Request** to branch `dev`.

---

## 📄 License

This project is licensed under the **MIT License**.

---

<div align="center">
  <h3>Designed & Engineered by <a href="https://thughari.github.io/">Hari Thatikonda</a></h3>
  <p><i>Building scalable systems with Java & Angular.</i></p>
  
  <a href="https://www.linkedin.com/in/hari-thatikonda/">
    <img src="https://img.shields.io/badge/LinkedIn-Connect-blue?style=for-the-badge&logo=linkedin" alt="LinkedIn">
  </a>
  <a href="https://github.com/thughari">
    <img src="https://img.shields.io/badge/GitHub-Follow-black?style=for-the-badge&logo=github" alt="GitHub">
  </a>
</div>
