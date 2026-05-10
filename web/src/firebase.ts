import { initializeApp } from "firebase/app";
import { getFunctions } from "firebase/functions";

const app = initializeApp({
  apiKey: "AIzaSyAtYNRj3N3aqE__8Yfj33Ep10qitqvSONc",
  authDomain: "unrolled-e4775.firebaseapp.com",
  projectId: "unrolled-e4775",
  storageBucket: "unrolled-e4775.firebasestorage.app",
  messagingSenderId: "496480123496",
  appId: "1:496480123496:web:21c9a4af9dc04b607119dd",
});

export const functions = getFunctions(app, "europe-west1");
