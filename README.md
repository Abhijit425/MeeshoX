# MEESHO X — AI-powered shopping app (Android)

An original Indian e-commerce app built with Kotlin + Jetpack Compose (Material 3). This is **phase 1–5 of the roadmap plus an on-device "AI" layer**: a working, installable customer app with a seller dashboard, running local-first on a Room database seeded with a fictional demo catalogue.

> Not affiliated with Meesho, Flipkart or Amazon. All brands, sellers and products in the demo catalogue are fictional.

## Get the APK
Every push builds `MeeshoX-debug.apk` with GitHub Actions (`.github/workflows/android.yml`). Pushes to `main` also publish it on the **Releases** page. Download it on your phone, allow "install unknown apps", install, then create an account (any valid 10-digit mobile + password).

## What works in this build
| Area | Status |
|---|---|
| Auth | Register / login with PBKDF2-hashed passwords, privacy consent, logout, account deletion. OTP architecture documented; **no SMS provider configured**. |
| Home | Search, voice search (Android speech recogniser), visual search, banners, category rail, flash deals with countdown, trending, best sellers, recently viewed, wishlist shortcuts, personalised feed. |
| Catalogue | 13 categories, ~100 seeded products, infinite scroll, grid/list, 7 sort orders, filters (price range, rating, discount, brand, delivery time, stock). |
| Product page | Image pager + pinch-zoom, colours, sizes, stock, seller info, pincode delivery check, return policy, specs, price-history chart + deal score, frequently bought together, similar products, review stats vs generated summary, write a review (verified-purchase label), size finder, compare. |
| Cart & checkout | Quantity limits, stock validation, save for later, coupons, delivery charges, GST breakdown, addresses, pincode serviceability, standard/express, payment method selection. |
| Payments | `PaymentProvider` abstraction + **sandbox provider only — no real money**. UPI ID containing `fail` simulates a decline. Card numbers / CVVs are never collected. |
| Orders | History, (simulated) tracking timeline, notifications, cancel with stock release + sandbox refund, return/exchange with eligibility window, refund status, invoice share, reorder. |
| Support | Rule-based support bot, complaint tickets, human escalation (stored locally). |
| Seller | Registration (GSTIN/IFSC validation; only last 4 bank digits stored), listing with template description/keyword generator and price-band recommendation, edit price/stock/status, orders, revenue, weekly sales chart + moving-average forecast, low-stock alerts, reviews. |
| Extras | Compare up to 5, smart budget shopping, live deal radar, gift finder, fashion stylist, shopping memory, group shopping lists (local + share), smart reorder, local admin overview with fraud-risk queue, dark theme, notification & personalisation controls. |

### About the "AI"
Everything runs **offline with transparent rules and statistics** (`ai/ShoppingIntelligence.kt`) — query parsing, ranking, pros/cons, recommendations, review themes, deal scoring, size advice, colour-based visual search, budget optimisation, forecasting and fraud scoring. The UI labels these outputs as rule-based; nothing is presented as generative AI. A cloud or on-device model can be plugged in behind `AiProvider` later.

### Needs external integrations (not configured)
SMS OTP · payment gateway (Razorpay/Cashfree/PayU) · courier tracking · image recognition model for product-type detection · photo enhancement/background removal · GST verification API · seller payouts · push notifications (FCM) · cloud sync for group lists. See `docs/ARCHITECTURE.md`.

## Project layout
```
app/src/main/java/com/meeshox/app/
  data/      Room entities, DAO, database, DataStore prefs, repository, demo seeder
  domain/    pricing, validation, serviceability, payments, order lifecycle, hashing
  ai/        on-device shopping intelligence (rules + statistics)
  ui/        theme, components, navigation root, screens/
app/src/test/  JVM unit tests for pricing, parsing, ranking, payments, lifecycle, risk, reviews
database/schema.sql   PostgreSQL schema for the server (28 tables, constraints, indexes)
docs/ARCHITECTURE.md  system architecture, API surface and roadmap
```

## Build locally
Android Studio (Koala+) with JDK 17: open the folder and run `app`. Or with Gradle 8.9: `gradle :app:assembleDebug` and `gradle :app:testDebugUnitTest`.
