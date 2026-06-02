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

## 🏗️ System Architecture & App Flow

JobTrackerPro is built as a highly automated, decoupled system focusing on security, latency reduction, and seamless user experiences.

### 🔄 End-to-End Application Flow

```mermaid
flowchart TD
    %% Styling
    classDef service fill:#4A90E2,stroke:#357ABD,stroke-width:1.5px,color:#fff;
    classDef utility fill:#50E3C2,stroke:#34A790,stroke-width:1.5px,color:#333;
    classDef storage fill:#F5A623,stroke:#C68015,stroke-width:1.5px,color:#fff;
    classDef trigger fill:#D0021B,stroke:#9E0010,stroke-width:1.5px,color:#fff;

    A[Gmail Webhook / Push Notice]:::trigger --> B(GmailWebhookService):::service
    B --> C{SmartExtractionService}:::service
    
    %% Template Ingest Pathway
    C -->|LinkedIn / Indeed Template| D(TemplateParser):::utility
    D -->|Extract Role & Company| E{Body Template Matches?}
    E -->|Yes| F[Extract from Body & Clean Location]:::utility
    E -->|No| G[Extract from Subject & Find Location]:::utility
    D -->|Extract & Clean URL| H[UrlParser: Decode next= Parameter]:::utility
    D -->|Determine Status| I[Strip Safety Warnings & Scan Keywords]:::utility
    
    %% AI Ingest Pathway
    C -->|Unmatched Emails| J{app.gemini.enabled?}
    J -->|true| K(GeminiExtractionService):::service
    J -->|false| L(MockGeminiService):::service
    
    %% Job Matching & Storage
    F --> M(JobService: createOrUpdateJob):::service
    G --> M
    K --> M
    L --> M
    
    M --> N{findBestMatch}:::service
    N -->|Stricter Token Matching for Short Names| O[Update Existing Job & Append Notes]:::storage
    N -->|No Active Match| P[Create New Job Entry]:::storage
    
    O --> Q[(Database: PostgreSQL)]:::storage
    P --> Q
    Q --> R(Caffeine Cache Eviction / Angular Signal Update):::service
```

The system operates across four key pipeline phases:

#### 1. 📬 Automated Ingestion & Webhook Layer
- **Gmail Sync Integration**: Users link their Gmail account via OAuth2. A Google Pub/Sub topic listens to mailbox events and sends real-time push notifications to `GmailWebhookService`.
- **Smart routing (`SmartExtractionService`)**: Ingested emails are routed through a `@Primary` interceptor. It checks if the email is a standard template from **LinkedIn** or **Indeed**.
  - **Matched**: Processed locally instantly (0ms AI cost, sub-millisecond execution).
  - **Unmatched**: Delegated to **Google Gemini 2.0 Flash** (or local `MockGeminiService` if offline).

#### 2. ⚙️ Manual Extraction Engine (`TemplateParser`)
- **Forward Header Reconstruction**: Recognizes forwarded email structures so you can forward confirmation emails directly to your sync mailbox.
- **Indeed Body Parser**: Extracts the role, company name, and location directly from standard Indeed body layouts to avoid misidentifying hyphenated roles (e.g. `Java Backend Developer - MS` parses `Java Backend Developer - MS` as the role and `Capco` as the company).
- **Url query decoding**: Recognizes `apply.indeed.com` confirmation links, extracts their `next=` query parameter, URL-decodes it, and stores the direct public listing URL (e.g., `https://in.indeed.com/viewjob?jk=...`), reducing length from 220+ to ~50 characters.
- **Location & Status Cleaning**: Removes reviews meta-data (e.g. `Remote 95 reviews` -> `Remote`) and filters safety warning disclaimers to prevent false matches (e.g., preventing *"without an interview"* from triggering an *Interview Scheduled* status).

#### 3. 🧠 Smart Deduplication & Matching (`JobService`)
- When a parsed job hits `JobService.createOrUpdateJob()`, it compares the company and role with your active board entries:
  - **Strict Token matching**: If either company name is $\le 3$ characters (e.g., `MS`), it requires an exact word-token match rather than a simple substring `contains` check (preventing `MS` from matching `ORION SYSTEMS`).
  - **Status Updates**: If a match is found (e.g. an interview invite email for a job you already applied to), it updates the existing entry in-place and appends the email details to the transaction notes. Otherwise, it inserts a new job entry.

#### 4. 🎨 Reactive UI & In-Memory Caching
- **State Management**: The Angular frontend uses **Angular Signals** for reactive state updates, giving the user instant UI response.
- **Caffeine In-Memory Caching**: User profiles, dashboard analytics, and job lists are cached on the Spring Boot backend, automatically evicted upon job modifications to keep the UI up-to-date while protecting the DB from heavy read traffic.
- **Don't-Drown Slice Scaling**: The D3.js Donut Chart scales tiny slices (e.g., 1 application status out of 400+) up to a minimum 2.5% display size so they are visible and hoverable, while keeping tooltip values mathematically accurate.

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



