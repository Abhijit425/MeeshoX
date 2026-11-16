# MEESHO X — Architecture & roadmap

## 1. System architecture
```
 Android app (Kotlin, Compose, MVVM)          Admin web (Next.js, TS, Tailwind)
   │  Room (offline cache, cart) + DataStore        │
   └──────────── HTTPS / JWT ─────────────┬─────────┘
                                          ▼
                      API gateway (rate limiting, auth, request IDs)
                                          │
   ┌──────────────┬──────────────┬────────┴─────┬──────────────┬──────────────┐
   Auth/OTP     Catalogue/Search   Cart/Checkout   Orders/Ship   Seller/Payouts   AI service
   (JWT access   (Postgres FTS +    (inventory      (state        (KYC docs,       (rules first;
   + refresh,    trigram, Redis     reservation,    machine,      settlements)     optional local
   argon2id)     cache)             idempotency)    webhooks)                      / cloud models)
          │             │                 │              │              │
          └──── PostgreSQL ──── Redis ──── job queue (BullMQ) ──── object storage (S3-compatible, CDN)
                                         WebSockets: order status, support chat
```
* **Backend**: NestJS (TypeScript) modules mirroring the boxes above; REST + WebSocket gateway.
* **Payments**: gateway order is created server-side; app uses the gateway SDK; server verifies the HMAC webhook before marking an order paid. No card data touches our servers.
* **AI service**: same interface as the app's `AiProvider`. Rule engine is the default and the fallback; each response records its `engine` for provenance.

## 2. Android architecture (this build)
* `MeeshoXApp` is a manual DI container (Room DB, DataStore, `ShopRepository`, `SandboxPaymentProvider`). Swapping to Hilt is mechanical.
* `ShopRepository` is the single source of truth. In this build it is local-first (Room). Next step: a `RemoteDataSource` (Retrofit) with Room as cache; `WorkManager` syncs the offline cart.
* UI: one `ShopViewModel` exposing StateFlows; screens are stateless-ish composables with loading / empty / error states.

## 3. API surface (v1)
`POST /auth/otp/request` · `POST /auth/otp/verify` · `POST /auth/refresh` · `DELETE /me`
`GET /categories` · `GET /products?category=&q=&sort=&filters=&cursor=` · `GET /products/{id}` · `GET /products/{id}/price-history` · `GET /products/{id}/reviews`
`GET/PUT /cart` · `POST /cart/items` · `POST /coupons/validate` · `GET /serviceability/{pincode}`
`POST /orders` (Idempotency-Key) · `GET /orders` · `POST /orders/{id}/cancel` · `POST /orders/{id}/returns` · `GET /orders/{id}/invoice.pdf`
`POST /payments/webhook/{provider}` · `POST /ai/assistant` · `POST /ai/visual-search` · `GET /recommendations`
`/seller/*` (products, inventory, bulk upload CSV, orders, payouts, analytics) · `/admin/*` (moderation, coupons, banners, commission, fraud queue, reports)

## 4. Security
JWT access (15 min) + rotating refresh tokens · role-based guards (CUSTOMER/SELLER/ADMIN/SUPPORT) · argon2id on server (PBKDF2 on-device in this build) · input validation (class-validator) · rate limiting (Redis) · signed upload URLs with MIME/size checks · payment webhook signature verification · audit_logs for sensitive actions · encrypted bank details (KMS) · daily PITR backups · consent + account deletion (soft delete, PII scrub job). The Android app holds **no API secrets**.

## 5. Roadmap
| Phase | Scope | Status |
|---|---|---|
| 1 | Architecture, structure, design system, DB schema | ✅ |
| 2 | Auth, profile, home | ✅ local (OTP pending provider) |
| 3 | Categories, discovery, details, search | ✅ |
| 4 | Wishlist, cart, coupons, checkout | ✅ |
| 5 | Payment sandbox, orders, tracking, returns | ✅ sandbox / simulated tracking |
| 6 | Seller app & inventory | ✅ core; bulk CSV + documents pending backend |
| 7 | Admin web dashboard | ⏳ local overview only |
| 8 | AI engine | ✅ rule engine; model providers pending |
| 9 | Notifications (FCM), analytics, security hardening | ⏳ |
| 10 | Backend API, sync, instrumented UI tests, release signing | ⏳ |
