# ALAS Browser Policy Site

This repository hosts the public policy website for ALAS Browser.

## Deploy target
This site is configured for **Vercel** (not GitHub Pages).

## Files
- `index.html` - Privacy Policy and Terms page
- `vercel.json` - Vercel routing and security headers

## Deploy steps (Vercel)
1. Go to Vercel dashboard and click **Add New Project**.
2. Import this GitHub repo: `alaslife/alas-browser-policy`.
3. Framework preset: **Other**.
4. Build command: leave empty.
5. Output directory: leave empty.
6. Deploy.

## Custom domain
1. In Vercel project settings, open **Domains**.
2. Add your domain (example: `policy.alasbrowser.com`).
3. Set the DNS records shown by Vercel.
4. Wait for SSL to be issued automatically.

## Notes
- Replace `YOUR_EMAIL_HERE` in `index.html` with your support email.
- Any push to `main` triggers a new deployment.
