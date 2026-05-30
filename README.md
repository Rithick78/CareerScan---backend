# CareerScan — Backend

> Spring Boot REST API that powers AI-based job matching using resume parsing,
> live job search, and skill overlap scoring.

---

## What is CareerScan?

CareerScan is an AI-powered job matching platform. Most job seekers waste
hours manually reading job descriptions to figure out which ones they qualify
for. CareerScan solves this by reading your resume automatically, understanding
your skills, searching live jobs, and ranking each one by how well it matches
your profile — so you only apply to jobs where you actually qualify.

---

## The Problem It Solves

| Problem | How CareerScan Fixes It |
|---|---|
| Applying to wrong roles wastes time | Match score (0–100) tells you before you apply |
| Reading 50 job descriptions takes hours | AI scans all of them automatically |
| Job portals don't know your real skills | Groq AI reads your actual PDF resume |
| High rejection rate from mismatched applications | Only high-match jobs shown at top |

---

## Resume Upload Pipeline

```
User uploads PDF
       ↓
Step 1 — FileStorageService
         Validate PDF + size → save to disk
       ↓
Step 2 — ResumeParserService
         PDFBox extracts text from PDF
       ↓
Step 3 — GroqService
         AI reads text → returns role, skills, city, experience
       ↓
Step 4 — ResumeDataService
         Save parsed data to MySQL
       ↓
Step 5 — JobSearchService (GET /api/jobs)
         Search live jobs → score each job → sort → return top 10
```
## Tech Stack

| Layer | Technology | Version |
|---|---|---|
| **Frontend** | React + Vite | 19.x + 8.x |
| **State Management** | Redux Toolkit | 2.x |
| **Routing** | React Router | v7 |
| **HTTP Client** | Axios | 1.x |
| **Styling** | Tailwind CSS + shadcn/ui | 4.x + v4 |
| **Backend** | Java + Spring Boot | 17 + 4.x |
| **Security** | Spring Security + JWT | 6.x |
| **Database** | MySQL + Spring Data JPA | 8.x |
| **AI Parsing** | Groq API (LLaMA) | — |
| **PDF Extraction** | Apache PDFBox | 3.x |
| **Job Search** | JSearch API (RapidAPI) | — |
| **Build Tools** | Maven + Vite | — |