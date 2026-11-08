# My Gym for the web

The gym in a browser: the same Supabase project, rules and design tokens as the phone app, laid out for
wide screens (sidebar, tables, side-by-side forms) and still usable on a phone.

```sh
npm install
npm run dev:local    # starts Docker and local Supabase, then http://localhost:5173 against it
npm run dev:remote   # http://localhost:5173 against the staging project; no Docker needed
npm test             # the rules: status, dates, money, CSV import
npm run build:remote # type-checks, then builds to dist/ for staging (a static site; host anywhere)
```

Each target's address and publishable key go in `.env.docker` and `.env.staging` (copied from `.env.example`; not committed). For Google sign-in,
set `VITE_GOOGLE_SIGN_IN=true` there and add the site's address to the project's redirect URLs
(Authentication → URL Configuration).

## Live on GitHub Pages

Every push to `main` that touches `web/` builds the site and publishes it to
**https://1-harshr.github.io/GymManager/** (`.github/workflows/web-pages.yml`; it can also be run by hand from
the Actions tab). It points at whichever project the repository's Actions variables `VITE_SUPABASE_URL` and
`VITE_SUPABASE_PUBLISHABLE_KEY` name, currently staging. The site lives under `/GymManager/`, so links inside
the app go through the router rather than to the domain's root. The page app stores link to for deleting an
account is https://1-harshr.github.io/GymManager/delete-account.

What's kept in the browser rather than in Supabase, as the phone keeps it on the phone: the theme, the
dashboard switches, the alerts inbox, and when each member was last sent a reminder.

Not on the web: the camera (a photo is chosen as a file), the morning notification, the on-device AI brief
(the written brief is used), Siri and Android App Functions, sending WhatsApp through Make.com,
demo mode and Hindi.
