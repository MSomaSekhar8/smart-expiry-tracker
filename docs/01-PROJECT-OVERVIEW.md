# 01 — Project Overview

## Smart Expiry & Pantry Waste Tracker

A full-stack web application that helps households stop wasting food and
medicine by tracking what they own, warning them before things expire, and
quantifying the money lost to waste.

## What the project does

- Users register with an email address and password and build a personal
  pantry inventory: item name, category, quantity, unit, purchase date,
  expiry date, shelf life and notes.
- Every item is classified **SAFE / EXPIRING / EXPIRED** using a warning
  window that is configured **per category** (e.g. medicines warn 7 days
  before expiry, perishables 1 day).
- A scheduled job runs every day at 07:00 and emails each user a digest of
  only their own expiring/expired items, exactly once per item per day.
- Users can record thrown-away items (whole or partial) with an estimated
  cost lost; a dashboard and an analytics page show waste trends and totals.
- Items can be added by scanning a barcode with the camera (or uploading a
  photo, or typing it); product information is fetched from Open Food Facts
  and cached server-side in PostgreSQL.

## The problem it solves

People throw away food and expired medicine mainly because they lose track of
expiry dates. Writing dates on packages is manual and easily forgotten.
Existing solutions often require hardware or closed ecosystems. This project
provides a lightweight, web-based, hardware-free tracker with proactive email
warnings and waste quantification.

## Target users

- Households who want to reduce food waste and save money.
- Anyone who manages perishables or medicines with short expiry windows.
- Caregivers/coordinators who need an overview of a shared pantry (the
  application supports a `USER` / `ADMIN` role model; admins can trigger
  digest runs manually).

## Main purpose

1. **Track** — keep a personal, up-to-date inventory of pantry items.
2. **Warn** — automatically notify users before items expire (per-category
   thresholds).
3. **Quantify** — show exactly how much was wasted and how much it cost.

## Scope

| In scope | Out of scope |
|---|---|
| Email/password authentication with JWT access + refresh tokens | Third-party identity providers (OAuth/Google login) |
| Pantry item CRUD with expiry tracking | Smart-appliance integration |
| Daily digest emails (Resend) | In-app push notifications |
| Waste logging and monthly analytics | Barcode generation / label printing |
| Barcode scanning + Open Food Facts lookup | Product inventory for stores (B2B features) |
| Roles USER / ADMIN with an admin digest trigger | Multi-tenant teams/shared pantries |

## High-level facts (verified from source)

- **Monorepo**: React frontend at the repo root, Spring Boot backend in
  `backend/`.
- **Backend**: Java 21, Spring Boot 3.5.16, packaged as a single jar
  (`pantry-tracker-backend-0.1.0.jar`).
- **Database**: PostgreSQL via Supabase, used strictly as managed Postgres
  (no Supabase Auth, no RLS, no Edge Functions); schema owned by Flyway
  (V1–V4).
- **Frontend**: React 19 + TypeScript + Vite 6 + Tailwind CSS 4.
- **Deployments**: API on Render (`https://smart-expiry-tracker-pn5i.onrender.com`),
  web app on Vercel (`https://smart-expiry-tracker-kappa.vercel.app`).
- **Verification**: 146 backend tests (0 failures), a green frontend build,
  and a 15-step production smoke test (15/15 PASS).