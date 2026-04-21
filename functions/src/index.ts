import { initializeApp } from "firebase-admin/app";
import { onCall } from "firebase-functions/v2/https";

initializeApp();

const REGION = "europe-west1";

export const ping = onCall({ region: REGION }, async () => {
  return { ok: true };
});
