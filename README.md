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

## Who Is This For?

- Job seekers who want zero-effort tracking via email automation
- Engineers interested in real-world AI ingestion pipelines
- Developers learning enterprise Spring Boot + Angular architecture
- Open-source contributors looking for a non-trivial system to extend

---

## 📌 Project Status

- JobTrackerPro is actively developed and open for contributions.
- The `dev` branch is the primary development branch.
- The `main` branch is reserved for stable, production-ready releases.

---

## 🔗 Quick Links

- 🚀 Live Demo: https://jobtrackerpro.in
- 📂 Source Code: https://github.com/thughari/JobTrackerPro

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
├── backend/                # Spring Boot API
│   ├── src/main/java/      # Controllers, Services, DTOs
│   ├── src/main/resources/ # Configurations for local, dev, prod # use local for dev
│   ├── Dockerfile          # Backend Container Config
│   ├── scripts             # Utility Scripts (e.g., simulate-email.sh)
│   └── service.yaml        # Google Cloud Run Config
├── frontend/               # Angular UI
│   ├── src/app/            # Components, Services, Guards
│   └── tailwind.config     # CSS Configuration
├── .github/workflows/      # CI/CD (GCP Cloud Run & GitHub Pages)
├── docker-compose.yml      # Local Dev Infrastructure 
└── README.md               # Documentation
```

---

## 🚀 One-Command Onboarding

You can run the entire ecosystem locally with **zero configuration**. The app automatically uses **Mock AI** and **Local File Storage** so you don't need any paid API keys to start contributing.

### 1. Spin up Infrastructure
Requires Docker Desktop. This starts PostgreSQL and MailHog (Email Trap).
```bash
docker-compose up -d
```

### 2. Start the Backend
```bash
cd backend
./mvnw spring-boot:run
```

### 3. Start the Frontend
```bash
cd frontend
npm install
npm start
```

### 4. Test the Features
- **Dashboard:** Access at `http://localhost:4200`.
- **Emails:** View outgoing emails at `http://localhost:8025` (MailHog).
- **AI Ingestion:** Use the `scripts/simulate-email.sh` to see the Mock AI create jobs automatically.

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



