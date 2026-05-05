# Functions — Unrolled

Firebase **Cloud Functions v2** in TypeScript, Node 22, region `europe-west1`. Currently a placeholder surface (`ping` callable) — reserved for server-side helpers when the mobile clients start needing them.

## Stack

- TypeScript 5.4
- [`firebase-functions`](https://github.com/firebase/firebase-functions) v7 (modular SDK, v2 triggers)
- [`firebase-admin`](https://github.com/firebase/firebase-admin-node) 13
- [Vitest](https://vitest.dev/) 4 for tests
- Node 22 (declared in `engines.node`)

## Install

```bash
cd functions
npm install
```

## Build

```bash
npm run build        # tsc → lib/  (lib/ is gitignored)
```

## Run locally

```bash
npm run serve        # build + firebase emulators:start --only functions
```

Or, from the repo root, `firebase emulators:start` to run hosting + firestore + functions together.

## Deploy

```bash
cd functions && npm run build
firebase deploy --only functions
```

The Firebase project id (`picswipe-4c451`) lives in the root [`../.firebaserc`](../.firebaserc).

## Layout

```
functions/
├── package.json
├── tsconfig.json
├── src/
│   └── index.ts        — function exports (currently: `ping`)
└── lib/                — tsc output (gitignored)
```

## Authoring rules

- **One file per logical group.** A new `users.ts` or `media.ts` is fine; re-export from `src/index.ts`.
- **All functions ship with `region: "europe-west1"`** for data-residency consistency. Use the `REGION` constant in `src/index.ts` rather than re-typing the string.
- **No top-level side effects.** `initializeApp()` once at module load is the only allowed import-time call.
- **Secrets** — never inline credentials. Use `defineSecret(...)` from `firebase-functions/params`, then:

  ```bash
  firebase functions:secrets:set SECRET_NAME
  ```

  Read at runtime via `SECRET_NAME.value()` (not at import time, or the function will fail to deploy when the secret rotates).

## Tests

```bash
npm test             # vitest run
```

No production tests yet — the placeholder `ping` doesn't warrant coverage. Add tests next to any real handler.
