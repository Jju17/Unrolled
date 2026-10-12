# Web: Unrolled

Marketing + legal surface for the Unrolled apps: landing page, **Privacy** policy, **Support** page. Deployed to **Firebase Hosting** under the project `unrolled-e4775` (custom subdomain to be configured).

## Stack

- Vite 8, React 19, React Router 7
- Tailwind CSS 4 via `@tailwindcss/vite`
- TypeScript 7

No Firebase SDK is shipped: none of the pages needs it today. Add `firebase` back the day a page calls a function or Firestore.

## Install

```bash
cd web
npm install
```

## Dev server

```bash
npm run dev          # http://localhost:5173, HMR on
```

## Production build & preview

```bash
npm run verify       # typecheck + vite build, what CI runs
npm run build        # vite build only, output in web/dist/
npm run preview      # serve the built output locally
```

`web/dist/` is what Firebase Hosting serves (configured in the root [`firebase.json`](../firebase.json)).

## Project layout

```
web/
├── index.html
├── vite.config.ts
├── tsconfig.json
└── src/
    ├── main.tsx          React Router setup, route table
    ├── index.css         Tailwind import
    └── pages/
        ├── Home.tsx      landing (placeholder)
        ├── Privacy.tsx   privacy policy (linked from the in-app About sheets)
        └── Support.tsx   support / contact (linked from the in-app About sheets)
```

The mobile clients link to `https://unrolled-e4775.web.app/privacy` and `.../support` from their About sheets. The contact email shown on these pages is `julien@rahier.dev` (kept consistent with the app stores' support contact).

## Deploy

```bash
scripts/deploy.sh    # from the repo root: gates, waits for CI, deploys hosting and functions
```

Never run `firebase deploy` by hand.

Firestore is configured to **deny** all reads and writes (see [`../firestore.rules`](../firestore.rules)) until concrete features need it.

## Conventions

- All routes live in `src/pages/`. Add a route by creating a component there and registering it in `src/main.tsx`.
- Styling is Tailwind utility classes; no `*.module.css` unless absolutely necessary.
- The privacy policy must describe what the apps really do (local decision storage, optional reverse geocoding); update it in the same change as any client behaviour that touches data.
- The contact email displayed publicly is **always `julien@rahier.dev`** (never the personal Gmail).
- Dependencies: `npm outdated` on every pass, bump to the latest (majors included) as long as `npm run verify` stays green.
