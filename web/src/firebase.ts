import { initializeApp } from "firebase/app";
import { getFunctions } from "firebase/functions";

const app = initializeApp({
  projectId: "picswipe",
  appId: "",
  storageBucket: "picswipe.firebasestorage.app",
  apiKey: "",
  authDomain: "picswipe.firebaseapp.com",
  messagingSenderId: "",
});

export const functions = getFunctions(app, "europe-west1");
