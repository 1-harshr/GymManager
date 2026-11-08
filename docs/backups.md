# Database backups

Supabase's free plan keeps no backups, so `.github/workflows/db-backup.yml` makes one every night at
02:00 India time: the database's roles, schema and data, dumped with the Supabase CLI, packed into one
file and encrypted with a passphrase. Each night's file is kept for 30 days under the workflow's runs in
the Actions tab. On the Pro plan Supabase backs up daily itself; this can keep running alongside as an
extra copy you hold.

What it doesn't cover: **member photos and gym logos**, which live in Supabase Storage rather than the
database. A restored gym has every record but shows initials until photos are added again.

## Setting it up

In the repository, open Settings → Secrets and variables → Actions → **Secrets** and add:

| Secret | Value |
| --- | --- |
| `SUPABASE_DB_URL` | In the Supabase dashboard: **Connect** → **Session pooler**, with the database password filled in. (GitHub's runners can't reach the direct connection, which is IPv6 only.) |
| `BACKUP_PASSPHRASE` | A long random passphrase, e.g. from `openssl rand -base64 32`. **Keep a copy outside GitHub** (a password manager): without it no backup can be opened, and GitHub won't show a secret again. |

Then run it once by hand: Actions → **Nightly database backup** → **Run workflow**, and check a file
named `gym-db-<date>` appears at the bottom of the run.

GitHub turns scheduled workflows off in a public repository after 60 days without commits. It emails
when it does; re-enable it from the Actions tab.

## Restoring

1. Download the night you want from the workflow run (a zip holding `gym-db-<date>.tar.gz.gpg`) and unzip it.
2. Decrypt and unpack it:

   ```sh
   gpg --decrypt gym-db-2026-11-08.tar.gz.gpg | tar -xzf -
   ```

   gpg asks for the passphrase. You get `roles.sql`, `schema.sql` and `data.sql`.
3. Restore into a **new, empty** Supabase project, not the damaged one, using its Session pooler connection string:

   ```sh
   psql --single-transaction --variable ON_ERROR_STOP=1 \
     --file roles.sql \
     --file schema.sql \
     --command 'SET session_replication_role = replica' \
     --file data.sql \
     --dbname "postgresql://postgres.<ref>:<password>@<pooler-host>:5432/postgres"
   ```

   `session_replication_role = replica` skips triggers while rows go back in, so checks like the
   overpayment trigger don't reject records that were valid when they were saved.
4. Point the apps and the web app at the new project (`local.properties`, the web's Actions variables),
   or copy what you need back into the old one.

Try a restore into a scratch project once, before you ever need one, so the steps are known to work
with your project.
