// MEESHO X payment server — Razorpay order creation, signature verification, refunds, webhooks.
// Zero dependencies (Node 18+). Holds the Razorpay KEY SECRET, which must never ship in the app.
//   RAZORPAY_KEY_ID=rzp_test_xxx RAZORPAY_KEY_SECRET=xxx RAZORPAY_WEBHOOK_SECRET=xxx node index.js
'use strict';
const http = require('http');
const crypto = require('crypto');

const KEY_ID = process.env.RAZORPAY_KEY_ID || '';
const KEY_SECRET = process.env.RAZORPAY_KEY_SECRET || '';
const WEBHOOK_SECRET = process.env.RAZORPAY_WEBHOOK_SECRET || '';
const PORT = Number(process.env.PORT || 8080);
const MAX_AMOUNT_PAISE = Number(process.env.MAX_AMOUNT_PAISE || 50_000_000); // ₹5,00,000 safety cap
const RZP = 'https://api.razorpay.com/v1';

const auth = 'Basic ' + Buffer.from(`${KEY_ID}:${KEY_SECRET}`).toString('base64');
const hits = new Map(); // naive per-IP rate limit: 60 req / minute

function safeEqual(a, b) {
  const x = Buffer.from(String(a)), y = Buffer.from(String(b));
  return x.length === y.length && crypto.timingSafeEqual(x, y);
}
function verifyPaymentSignature(orderId, paymentId, signature, secret = KEY_SECRET) {
  const expected = crypto.createHmac('sha256', secret).update(`${orderId}|${paymentId}`).digest('hex');
  return safeEqual(expected, signature);
}
function verifyWebhook(rawBody, signature, secret = WEBHOOK_SECRET) {
  const expected = crypto.createHmac('sha256', secret).update(rawBody).digest('hex');
  return safeEqual(expected, signature);
}
async function rzp(path, body) {
  const r = await fetch(RZP + path, { method: 'POST', headers: { Authorization: auth, 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  const j = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(j?.error?.description || `Razorpay HTTP ${r.status}`);
  return j;
}
function send(res, code, obj) {
  res.writeHead(code, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
  res.end(JSON.stringify(obj));
}

async function handle(req, res, raw) {
  const ip = req.socket.remoteAddress || '';
  const now = Date.now(); const h = (hits.get(ip) || []).filter(t => now - t < 60_000); h.push(now); hits.set(ip, h);
  if (h.length > 60) return send(res, 429, { error: 'Too many requests' });

  if (req.method === 'GET' && req.url === '/health') return send(res, 200, { ok: true, mode: KEY_ID.startsWith('rzp_live_') ? 'live' : 'test' });
  if (req.method !== 'POST') return send(res, 404, { error: 'Not found' });

  if (req.url === '/api/payments/webhook') {
    if (!WEBHOOK_SECRET || !verifyWebhook(raw, req.headers['x-razorpay-signature'] || '')) return send(res, 400, { error: 'Bad signature' });
    const evt = JSON.parse(raw);
    console.log(new Date().toISOString(), 'webhook', evt.event, evt.payload?.payment?.entity?.id || '');
    // TODO when the order DB exists: mark order paid/failed/refunded idempotently by payment/order id.
    return send(res, 200, { received: true });
  }

  let body; try { body = JSON.parse(raw || '{}'); } catch { return send(res, 400, { error: 'Invalid JSON' }); }

  if (req.url === '/api/payments/order') {
    const amount = Number(body.amount);
    if (!Number.isInteger(amount) || amount < 100 || amount > MAX_AMOUNT_PAISE) return send(res, 400, { error: 'Invalid amount' });
    // PRODUCTION: compute the amount from the server-side cart, not from the client.
    const order = await rzp('/orders', { amount, currency: 'INR', receipt: String(body.receipt || '').slice(0, 40), payment_capture: 1 });
    return send(res, 200, { order_id: order.id, amount: order.amount, currency: order.currency, key_id: KEY_ID });
  }
  if (req.url === '/api/payments/verify') {
    const { razorpay_order_id: o, razorpay_payment_id: p, razorpay_signature: s } = body;
    if (!o || !p || !s) return send(res, 400, { verified: false, error: 'Missing fields' });
    const verified = verifyPaymentSignature(o, p, s);
    console.log(new Date().toISOString(), 'verify', p, verified);
    return send(res, verified ? 200 : 400, { verified });
  }
  if (req.url === '/api/payments/refund') {
    const id = String(body.payment_id || '');
    if (!/^pay_[A-Za-z0-9]+$/.test(id)) return send(res, 400, { error: 'Invalid payment id' });
    // PRODUCTION: require an authenticated admin/user session and check the order belongs to them.
    const payload = Number.isInteger(body.amount) ? { amount: body.amount } : {};
    const r = await rzp(`/payments/${id}/refund`, payload);
    return send(res, 200, { refund_id: r.id, status: r.status });
  }
  return send(res, 404, { error: 'Not found' });
}

if (require.main === module) {
  if (!KEY_ID || !KEY_SECRET) { console.error('Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET'); process.exit(1); }
  http.createServer((req, res) => {
    let raw = ''; req.on('data', c => { raw += c; if (raw.length > 1e6) req.destroy(); });
    req.on('end', () => handle(req, res, raw).catch(e => send(res, 502, { error: e.message })));
  }).listen(PORT, () => console.log(`MEESHO X payment server on :${PORT} (${KEY_ID.startsWith('rzp_live_') ? 'LIVE' : 'TEST'})`));
}
module.exports = { verifyPaymentSignature, verifyWebhook };
