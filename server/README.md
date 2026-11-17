# MEESHO X payment server (Razorpay)

Holds the Razorpay **Key Secret** (never put it in the app). Zero dependencies, Node 18+.

| Endpoint | Purpose |
|---|---|
| `POST /api/payments/order` `{amount (paise), receipt}` | Creates a Razorpay Order (auto-capture) and returns `order_id`, `key_id` |
| `POST /api/payments/verify` `{razorpay_order_id, razorpay_payment_id, razorpay_signature}` | HMAC-SHA256 signature check → `{verified}` |
| `POST /api/payments/refund` `{payment_id, amount?}` | Issues a refund |
| `POST /api/payments/webhook` | Verifies `X-Razorpay-Signature`; logs events |
| `GET /health` | Liveness + test/live mode |

```bash
RAZORPAY_KEY_ID=rzp_test_xxx RAZORPAY_KEY_SECRET=xxx RAZORPAY_WEBHOOK_SECRET=xxx npm start
npm test
```
Deploy anywhere with HTTPS (Render, Railway, Fly.io, a VPS behind Caddy…), then put the https URL in the app: Profile → Payment gateway.

Before accepting real money: compute the order amount from a server-side cart (not the client), require user authentication on these routes, and persist orders/payments in PostgreSQL (`database/schema.sql`) with idempotent webhook handling.
