# Web — Unrolled

Marketing + legal surface for the Unrolled apps: landing page, **Privacy** policy, **Support** page. Deployed to **Firebase Hosting** under the project `picswipe-4c451` (custom subdomain to be configured).

## Stack

- [Vite](https://vitejs.dev/) 7
- React 19 + React Router 7
- [Tailwind CSS](https://tailwindcss.com/) 4 (via `@tailwindcss/vite`)
- TypeScript 5.9
- Firebase JS SDK 12 (placeholder config — see *Firebase config* below)

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
npm run build        # tsc + vite build → web/dist/
npm run preview      # serve the built output locally
```

`web/dist/` is what Firebase Hosting serves (configured in the root [`firebase.json`](../firebase.json)).

## Project layout

```
web/
├── index.html
├── vite.config.ts
├── tsconfig.json
├── public/
└── src/
    ├── main.tsx          — React Router setup, route table
    ├── index.css         — Tailwind import
    ├── firebase.ts       — Firebase app init (region europe-west1, placeholder values)
    └── pages/
        ├── Home.tsx      — landing
        ├── Privacy.tsx   — privacy policy (linked from in-app About sheet)
        └── Support.tsx   — support / contact (linked from in-app About sheet)
```

The mobile clients link to `https://picswipe-4c451.web.app/privacy` and `.../support` from their About sheets — the contact email shown on these pages is `julien@rahier.dev` (kept consistent with the app stores' support contact).

## Firebase config

`src/firebase.ts` initializes a Firebase app pointed at project `picswipe-4c451`, region `europe-west1`. The values for `apiKey`, `appId`, `messagingSenderId` are currently **empty placeholders** — fill them in from the Firebase console before calling any Firebase API. None of today's pages call Firebase, so empty placeholders are fine for now.

## Deploy

```bash
cd web && npm run build && cd ..
firebase deploy --only hosting
```

This pushes `web/dist/` to Firebase Hosting. Firestore is configured to **deny** all reads and writes (see [`../firestore.rules`](../firestore.rules)) until concrete features need it.

## Conventions

- All routes live in `src/pages/`. Add a route by creating a component there and registering it in `src/main.tsx`.
- Styling is Tailwind utility classes — avoid `*.module.css` unless absolutely necessary.
- The contact email displayed publicly is **always `julien@rahier.dev`** (never the personal Gmail).
