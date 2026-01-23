# JobTrackerPro UI 🎨

![Angular](https://img.shields.io/badge/Angular-17+-red)
![TailwindCSS](https://img.shields.io/badge/Tailwind-CSS-blue)
![TypeScript](https://img.shields.io/badge/TypeScript-5.0-blue)

The modern, responsive frontend for **JobTrackerPro**. Built with Angular and TailwindCSS, it provides a seamless user experience for tracking job applications and visualizing career progress.

## 🔗 Quick Links

*   **Live Application:** [https://thughari.github.io/JobTrackerPro](https://thughari.github.io/JobTrackerPro)
*   **Backend Repository:** [github.com/thughari/JobTrackerPro](https://github.com/thughari/JobTrackerPro)
*   **Backend API URL:** [jobtracker-service-963261513098.asia-south1.run.app](https://jobtracker-service-963261513098.asia-south1.run.app)

## ✨ Key Features

*   **📊 Interactive Dashboard:** Real-time statistics using **D3.js** interactive charts.
*   **👤 Advanced Profile:** Atomic updates for profile data, supporting file uploads (R2) and external URLs.
*   **🌗 Theming:** Built-in **Dark Mode** support persisted via LocalStorage.
*   **⚡ Optimistic UI:** Smart caching and context-aware data fetching to minimize network latency.
*   **🔒 Security:** JWT-based authentication with route guards (`AuthGuard`, `GuestGuard`).

## 🛠️ Tech Stack

*   **Framework:** Angular 17+ (Standalone Components, Signals)
*   **Styling:** TailwindCSS
*   **Icons:** Phosphor Icons
*   **Charts:** D3.js
*   **Deployment:** GitHub Pages

## 🚀 Getting Started

1.  **Clone the repository**
    ```bash
    git clone https://github.com/thughari/JobTrackerPro/tree/main/frontend
    cd JobTrackerPro/frontend
    ```
2.  **Install Dependencies**
    ```bash
    npm install
    ```
3.  **Configure API**
    Update `src/environments/environment.prod.ts` to point to your Cloud Run URL.
4.  **Run Development Server**
    ```bash
    ng serve
    ```

## 📂 Project Structure
```text
src/app/
├── components/          # Standalone UI Components (Auth, Dashboard, Profile)
├── services/            # API Communication & Signal Store
├── core/                # Guards & Interceptors
└── shared/              # Pipes and Utilities
