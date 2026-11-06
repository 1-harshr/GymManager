# Automatic WhatsApp messages through Make.com

GymManager queues welcomes, receipts and daily reminders on the phone and posts each one to a
Make.com scenario. The scenario sends it from the gym's WhatsApp Business number. There is no
server to run; Make's free plan (about 1,000 operations a month) covers a small gym.

```
GymManager (phone)                       Make.com scenario                     Meta
outbox ── POST JSON ──▶ Custom webhook ──▶ WhatsApp Business Cloud ──▶ member
                         └─ Webhook response (tells the app if it worked)
```

## 1. Meta: the number and the templates (once)

1. Create a Meta Business account and open **WhatsApp Manager**.
2. Add the gym's phone number and get its display name approved. A number moved from the
   WhatsApp Business app stops working in that app; a separate number avoids that.
3. Add a payment card (WhatsApp charges per message).
4. Create the five templates shown in the app under **Settings → WhatsApp → Templates for
   WhatsApp Manager**, each with **the exact name**, category **Utility**, language **English**,
   and the body text as shown (long-press to copy). Wait until they're **Approved**.

## 2. Make: the scenario (once)

1. Sign up at make.com (free plan) and create a new scenario.
2. Add **Webhooks → Custom webhook**. Create a webhook and copy its address
   (`https://hook.<region>.make.com/…`).
3. Paste that address into **Settings → WhatsApp** in the app and tap **Send a test** once, so
   Make learns the data structure.
4. Add **WhatsApp Business Cloud → Make an API Call** and connect it to the gym's WhatsApp
   Business account (Meta login inside Make).
   - URL: `/{phone-number-id}/messages` (the number's id from WhatsApp Manager)
   - Method: `POST`
   - Body: map the webhook's **`graph_body`** field. It's the complete request; nothing else to build.
5. Add **Webhooks → Webhook response**: status `200`.
6. Optional: right-click the API call → **Add error handler → Webhook response** with status
   `400` and body `{{error.message}}`. The app then shows WhatsApp's reason under Recent
   messages instead of "Sent to Make".
7. Turn the scenario **on** (scheduling: immediately as data arrives).

Make's screens change now and then; the module names above may differ slightly.

## What the app posts

```json
{
  "id": "a3f1…",            // unique per message; the same id is reused if the app retries
  "to": "919876543210",
  "template": "gym_receipt",
  "language": "en",
  "params": ["AKHADA Gym & MMA", "12", "Ravi", "MMA Monthly", "6 Nov 2026", "5 Dec 2026", "₹1,500", "₹0"],
  "p1": "AKHADA Gym & MMA", "p2": "12", "…": "…",
  "graph_body": "{\"messaging_product\":\"whatsapp\",\"to\":\"919876543210\",\"type\":\"template\",…}",
  "kind": "receipt",
  "member": "Ravi Kumar"
}
```

## How the app reacts to Make's answer

| Make answers | Outbox shows | Retried? |
|---|---|---|
| 2xx | Sent to Make | – |
| 408, 429, 5xx, or no internet | Waiting to send | Yes (Android: as soon as online, with back-off; iPhone: next time the app is open) |
| Any other 4xx (e.g. 404: scenario deleted, 400 from the error handler) | Not sent · reason | No |

## Limits worth knowing

- Without the error handler in step 6, Make answers 200 before WhatsApp has tried, so a refused
  message still shows "Sent to Make". Check failures in Make's scenario history.
- Make doesn't report delivery or read receipts back to the app.
- On iPhone, daily reminders go out when the app is opened after the reminder time.
- If the connection drops just after Make accepts a message, the app may post it again; the
  scenario can skip repeats by checking `id` (e.g. with a Make data store), if that matters.
