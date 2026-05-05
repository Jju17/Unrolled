import { initializeApp } from "firebase/app";
import { getFunctions } from "firebase/functions";

const app = initializeApp({
  projectId: "picswipe-4c451",
  appId: "",
  storageBucket: "picswipe-4c451.firebasestorage.app",
  apiKey: "",
  authDomain: "picswipe-4c451.firebaseapp.com",
  messagingSenderId: "",
});

export const functions = getFunctions(app, "europe-west1");
