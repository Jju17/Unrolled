# Functions: Unrolled

Firebase **Cloud Functions v2** in TypeScript, Node 22, region `europe-west1`. Currently a placeholder surface (`ping` callable) reserved for server-side helpers when the mobile clients start needing them.

## Stack

- TypeScript 7
- [`firebase-functions`](https://github.com/firebase/firebase-functions) v7 (modular SDK, v2 triggers)
- [`firebase-admin`](https://github.com/firebase/firebase-admin-node) 14
- [Vitest](https://vitest.dev/) 5 for tests
- Node 22 (declared in `engines.node`, matches `firebase.json`'s runtime)

## Install

```bash
cd functions
npm install
```

## Build

```bash
npm run build        # tsc, output in lib/ (gitignored); test files are excluded
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

The Firebase project id (`unrolled-e4775`) lives in the root [`../.firebaserc`](../.firebaserc).

## Layout

```
functions/
├── package.json
├── tsconfig.json
├── src/
│   ├── index.ts        function exports (currently: `ping`)
│   └── index.test.ts   vitest suite, calls the handlers through `.run(request)`
└── lib/                tsc output (gitignored)
```

## Authoring rules

- **One file per logical group.** A new `users.ts` or `media.ts` is fine; re-export from `src/index.ts`.
- **All functions ship with `region: "europe-west1"`** for data-residency consistency. Use the `REGION` constant in `src/index.ts` rather than re-typing the string.
- **No top-level side effects.** `initializeApp()` once at module load is the only allowed import-time call.
- **Every handler has a test next to it** (`*.test.ts`), exercised through `handler.run(...)` so no emulator is needed.
- **Secrets**: never inline credentials. Use `defineSecret(...)` from `firebase-functions/params`, then:

  ```bash
  firebase functions:secrets:set SECRET_NAME
  ```

  Read at runtime via `SECRET_NAME.value()` (not at import time, or the function will fail to deploy when the secret rotates).

## Tests

```bash
npm test             # vitest run
```
