# My Gym for the web

The gym in a browser: the same Supabase project, rules and design tokens as the phone app, laid out for
wide screens (sidebar, tables, side-by-side forms) and still usable on a phone.

```sh
npm install
npm run dev        # http://localhost:5173, against local Supabase (`supabase start`)
npm test           # the rules: status, dates, money, CSV import
npm run build      # type-checks, then builds to dist/ (a static site; host anywhere)
```

To use a hosted project, copy `.env.example` to `.env.local` and set its URL and publishable key. For
Google sign-in, set `VITE_GOOGLE_SIGN_IN=true` and add the site's address to the project's redirect URLs
(Authentication → URL Configuration).

What's kept in the browser rather than in Supabase, as the phone keeps it on the phone: the theme, the
dashboard switches, the alerts inbox, and when each member was last sent a reminder.

Not on the web: the camera (a photo is chosen as a file), the morning notification, the on-device AI brief
(the written brief is used), Siri and Android App Functions, sending WhatsApp through Make.com, backup,
demo mode and Hindi.
