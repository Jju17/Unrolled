# Release Notes — Unrolled v1.0

Copy-paste reference for App Store Connect (iOS) and Google Play Console (Android) submissions. EN + FR per platform.

---

## iOS — App Store Connect

### Metadata · English (U.S.)

**Subtitle** (30 chars max — shows under the app name in the App Store)
```
Swipe your camera roll clean.
```
→ 29 chars (cohérent avec la tagline déjà affichée dans l'About sheet in-app)

**Promotional Text** (170 chars max — editable post-release without re-review)
```
Tinder for your camera roll. Swipe through every photo, keep what you love, send the rest to the trash. Fully private, offline, fast.
```

**Description** (4000 chars max)
```
Unrolled is the fastest way to clean up your camera roll.

Tap. Swipe. Done.

Open the app, pick an album, and start sorting. Swipe right to keep a photo, swipe left to send it to a review pile. When you're ready, hit "Empty trash" and Unrolled hands the whole batch to iOS — your photos go to the standard Recently Deleted album, recoverable for 30 days.

WHY YOU'LL LIKE IT

• Zero-loss design — nothing gets deleted until you confirm twice (in-app + iOS dialog). Restore any photo from the review sheet before you commit.

• Fast — pre-loaded thumbnails, smooth fly-off animation, light haptic on every swipe. Built for muscle memory.

• Every album — Recents, Favorites, Screenshots, Selfies, Panoramas, Bursts, Live Photos, plus all your custom albums.

• Shuffle — randomize the order when chronological feels endless. Your place is saved.

• Pinch to zoom — tap any card to go full-screen, two-finger zoom anchored on your fingers.

• Undo — one tap brings back your last decision.

100% PRIVATE

Unrolled never touches the internet. Your photos stay on your device. No accounts. No tracking. No ads. No analytics. No third-party SDKs. The only network call the app could ever make is the Privacy Policy link in the About sheet — and only if you tap it.

Free. No in-app purchases. No subscription. No upsells.

Made with care for people whose camera roll has 50,000 photos.
```

**Keywords** (100 chars max, comma-separated, **no spaces** after commas)
```
camera roll,photo cleanup,delete photos,swipe,declutter,storage,sort,organize,gallery,clean
```

**Support URL**
```
https://unrolled-e4775.web.app/support
```

**Marketing URL**
```
https://unrolled-e4775.web.app
```

**Version**: `1.0`

**Copyright**
```
© 2026 Julien Rahier
```

**What's New in This Version** (v1.0 — first release)
```
First public release. Sort your camera roll by swiping — keep what you love, send the rest to the iOS trash. Fully private, fully offline.
```

---

### Metadata · French (France)

**Subtitle**
```
Range ta pellicule en swipant
```
→ 29 chars

**Promotional Text**
```
Tinder pour ta photothèque. Swipe sur chaque photo, garde celles que tu aimes, envoie le reste à la poubelle. 100% privée, offline, rapide.
```

**Description**
```
Unrolled est la façon la plus rapide de faire le tri dans ta photothèque.

Tap. Swipe. Done.

Ouvre l'app, choisis un album, et commence à trier. Swipe à droite pour garder une photo, à gauche pour l'envoyer dans la corbeille. Quand t'es prêt, tape « Mettre à la poubelle » et Unrolled passe le lot à iOS — tes photos vont dans « Récemment supprimées », récupérables pendant 30 jours.

POURQUOI TU VAS AIMER

• Aucun risque de perte — rien n'est supprimé tant que tu ne valides pas deux fois (alerte in-app + dialogue iOS). Tu peux ressortir n'importe quelle photo de la corbeille avant de confirmer.

• Rapide — thumbnails préchargés, animation de fly-off fluide, retour haptique léger à chaque swipe. Pensée pour la mémoire musculaire.

• Tous tes albums — Récents, Favoris, Captures d'écran, Selfies, Panoramas, Rafales, Live Photos, plus tous tes albums perso.

• Mode aléatoire — quand l'ordre chronologique te paraît interminable. Ta position est sauvegardée.

• Zoom au pinch — tap pour passer une photo en plein écran, zoom à deux doigts ancré sur tes doigts.

• Annuler — une tap pour récupérer ton dernier choix.

100% PRIVÉE

Unrolled ne touche jamais à internet. Tes photos restent sur ton appareil. Pas de compte. Pas de tracking. Pas de pub. Pas d'analytics. Pas de SDK tiers. Le seul appel réseau possible est le lien Privacy Policy dans l'About — et seulement si tu tapes dessus.

Gratuite. Pas d'achats intégrés. Pas d'abonnement. Pas d'upsell.

Faite avec soin pour les gens qui ont 50 000 photos dans leur pellicule.
```

**Keywords**
```
photothèque,nettoyer,supprimer photos,swipe,trier,stockage,galerie,organiser,ranger,libérer
```

**Support URL** / **Marketing URL** / **Copyright** : identiques à la version EN.

**Nouveautés**
```
Première version publique. Trie ta pellicule en swipant — garde celles que tu aimes, envoie le reste à la corbeille iOS. 100% privée, 100% offline.
```

---

### App Privacy

Section "App Privacy" (sidebar) → **Data Not Collected** sur tous les types. Aucune permission tracking, aucun SDK analytics, aucune connexion réseau (sauf ouverture de URL externe via `openURL` quand l'utilisateur tape un lien).

### Age Rating
**4+** — aucun contenu sensible. Réponds "None" à toutes les catégories du questionnaire.

### Pricing and Availability
- **Price**: Free
- **Availability**: All countries / regions

### App Review Information
- **Sign-in required**: No
- **Contact info**: julien@rahier.dev
- **Demo account**: Not applicable (no login)
- **Attachment**: screen recording (see "Screen recording checklist" below)

#### Notes (≤4000 chars — paste verbatim into App Store Connect)

App Store Connect's App Review Information notes field caps at 4000
characters, so this template is condensed. It still covers all 6
topics App Review requests under Guideline 2.1.

```
Hi App Review,

Re: Guideline 2.1 — full info for Unrolled.

1) SCREEN RECORDING
Attached: physical-device recording showing cold launch, Photos permission prompt (Allow), card stack swipe right=keep / left=delete, stats pill → trash review sheet, "Keep" restore, "Empty trash" → in-app alert → iOS system delete dialog → photos in Recently Deleted, About sheet (~45s total).

2) APP PURPOSE
Unrolled is a photo-cleanup utility. Users with thousands of unsorted photos swipe through their library card-by-card (right=keep, left=delete). When ready, a single confirmation moves the batch to iOS Recently Deleted (recoverable 30 days). Solves the "Photos app makes deleting one-by-one too tedious" problem. Audience: anyone with 5,000+ photos, no technical knowledge needed.

3) INSTRUCTIONS
No login or test credentials required. To test:
1. Launch the app.
2. Tap "Allow Full Access" on the Photos permission prompt. (Limited Access also works.)
3. Card stack appears with Recents.
4. Swipe right = keep. Swipe left = trash review pile.
5. Tap the heart/X stats pill (top right) → trash review sheet.
6. Tap "Keep" on a row to restore. Tap "Empty trash" footer button.
7. In-app confirmation alert → tap "Delete".
8. iOS native PhotoKit confirmation → tap "Delete".
9. Photos move to iOS Recently Deleted (recoverable 30 days).
Other features: album picker (top-left), shuffle (top-right), undo (bottom center), tap card for full-screen pinch-zoom, settings gear (bottom-left), "..." menu → About.

4) EXTERNAL SERVICES
None. Unrolled has no backend, analytics, ads, login, payments, or AI services. The app does not include an HTTP client. The only network calls are openURL handoffs to Safari for the Privacy and Support links in the About sheet (https://unrolled-e4775.web.app/privacy and /support). Third-party SPM dependencies: swift-composable-architecture (state management library, Point-Free) — no network behavior. System frameworks: PhotoKit (read + delete), SwiftUI/UIKit. NOT used: StoreKit, CloudKit, Network, UserNotifications.

5) REGIONAL DIFFERENCES
None. Identical features and content in every region. Only locale-driven difference is UI language (English base, French translation), following the device's preferred language.

6) REGULATED INDUSTRY
Not applicable. Personal photo-organization utility. Not in healthcare, finance, gambling, government, legal, or any regulated sector.

PURPOSE STRING (NSPhotoLibraryUsageDescription)
EN: "Unrolled needs access to your photos so you can sort them by swiping."
FR: "Unrolled a besoin d'accéder à tes photos pour que tu puisses les trier en swipant."
Photos access is used solely to (a) display thumbnails on swipe cards, (b) read creation-date metadata for the date overlay, (c) call PHAssetChangeRequest.deleteAssets when the user explicitly confirms an empty-trash action. No photo data is stored, copied, transmitted, or analyzed beyond display.

Best,
Julien Rahier — julien@rahier.dev
```

#### Screen recording checklist (capture on a physical iPhone)

1. Connect the iPhone to the Mac via USB.
2. **QuickTime Player** → File → New Movie Recording → arrow next to the
   record button → pick the iPhone as both video and audio source.
3. Force-quit Unrolled on the device first so the recording starts from
   a true cold launch.
4. Hit record, then on the iPhone follow the timeline above (~45 sec
   total).
5. Stop recording, File → Export As → 1080p, save as `.mov`.
6. Upload via App Store Connect — the App Review Information notes
   field accepts attachments up to 500 MB.

### Build & Submission Checklist
- [ ] Archive generated from Xcode (`Product → Archive`)
- [ ] Uploaded via Organizer / Transporter
- [ ] App icon: opaque RGB, no alpha (already fixed in `Icon-1024.png`)
- [ ] Screenshots: 1284×2778 (iPhone 6.7"), no alpha (use `-AppStore.png` files on Desktop)
- [ ] Privacy Policy URL reachable: https://unrolled-e4775.web.app/privacy
- [ ] Support URL reachable: https://unrolled-e4775.web.app/support
- [ ] French (France) locale added
- [ ] App Privacy = Data Not Collected
- [ ] "Add for Review" → Submit

---

## Android — Google Play Console

### Store Listing · English

**App name** (50 chars max)
```
Unrolled
```

**Short description** (80 chars max)
```
Tinder for your gallery. Swipe to keep, swipe to delete. Private and offline.
```

**Full description** (4000 chars max)
```
Unrolled is the fastest way to clean up your gallery.

Tap. Swipe. Done.

Open the app, pick an album, and start sorting. Swipe right to keep a photo, swipe left to send it to a review pile. When you're ready, hit "Empty trash" and Unrolled hands the whole batch to Android — your photos go to the system trash, recoverable for 30 days.

WHY YOU'LL LIKE IT

• Zero-loss design — nothing gets deleted until you confirm twice (in-app + Android system dialog). Restore any photo from the review sheet before you commit.

• Fast — async thumbnail loading via Coil, smooth fly-off animation, haptic feedback on every swipe.

• Every album — All Photos plus every folder Android exposes via MediaStore.

• Shuffle — randomize the order when chronological feels endless. Your place is saved.

• Pinch to zoom — tap any card to go full-screen, two-finger zoom anchored on your fingers, swipe down to dismiss.

• Undo — one tap brings back your last decision.

100% PRIVATE

Unrolled never touches the internet. Your photos stay on your device. No accounts. No tracking. No ads. No analytics. No third-party SDKs beyond Coil (image loading, on-device only). The app does not even include a network HTTP client.

Permissions:
• Photos and videos (READ_MEDIA_IMAGES) — to show your photos so you can sort them. Required.
• Visual user-selected media (Android 14+) — supported, so you can grant access to a subset of your library if you prefer.

Free. No in-app purchases. No subscription. No upsells.

Made with care for people whose gallery has 50,000 photos.
```

(The per-release "What's new" copy lives in the consolidated multi-locale section below, not here.)

---

### Store Listing · French (France)

**App name**
```
Unrolled
```

**Short description**
```
Tinder pour ta galerie. Swipe pour garder, swipe pour supprimer. Privée et offline.
```

**Full description**
```
Unrolled est la façon la plus rapide de faire le tri dans ta galerie.

Tap. Swipe. Done.

Ouvre l'app, choisis un album, et commence à trier. Swipe à droite pour garder une photo, à gauche pour l'envoyer dans la corbeille. Quand t'es prêt, tape « Mettre à la poubelle » et Unrolled passe le lot à Android — tes photos vont dans la corbeille système, récupérables pendant 30 jours.

POURQUOI TU VAS AIMER

• Aucun risque de perte — rien n'est supprimé tant que tu ne valides pas deux fois (alerte in-app + dialogue système Android). Tu peux ressortir n'importe quelle photo de la corbeille avant de confirmer.

• Rapide — thumbnails chargés en async via Coil, animation de fly-off fluide, retour haptique à chaque swipe.

• Tous tes albums — Toutes les photos plus chaque dossier exposé par MediaStore.

• Mode aléatoire — quand l'ordre chronologique te paraît interminable. Ta position est sauvegardée.

• Zoom au pinch — tap pour passer une photo en plein écran, zoom à deux doigts ancré sur tes doigts, swipe vers le bas pour fermer.

• Annuler — une tap pour récupérer ton dernier choix.

100% PRIVÉE

Unrolled ne touche jamais à internet. Tes photos restent sur ton appareil. Pas de compte. Pas de tracking. Pas de pub. Pas d'analytics. Pas de SDK tiers (sauf Coil pour le chargement d'image, on-device only). L'app n'embarque même pas de client HTTP réseau.

Permissions :
• Photos et vidéos (READ_MEDIA_IMAGES) — pour afficher tes photos et te laisser les trier. Obligatoire.
• Médias sélectionnés (Android 14+) — supporté, tu peux n'accorder l'accès qu'à une sélection de photos.

Gratuite. Pas d'achats intégrés. Pas d'abonnement. Pas d'upsell.

Faite avec soin pour les gens qui ont 50 000 photos dans leur galerie.
```

(La copie "Nouveautés" par release est dans la section multilocale consolidée ci-dessous.)

---

### Release notes — "What's new" per release (Play Console, copy-paste)

Paste the whole fenced block for the target version into **Play Console → [track] → Create new release → What's new in this release**. Play Console parses each `<locale>` tag and routes the copy to the matching Play Store locale. Cap: 500 chars per locale.

When bumping `versionCode` / `versionName` in `android/app/build.gradle.kts`, **prepend a new block here (newest on top)** and keep the three locales in sync.

#### v1.0.0 (versionCode 2)

```
<en-US>
First public release. Sort your gallery by swiping — keep what you love, send the rest to the system trash. Fully private, fully offline.
</en-US>
<fr-FR>
Première version publique. Trie ta galerie en swipant — garde celles que tu aimes, envoie le reste à la corbeille système. 100% privée, 100% offline.
</fr-FR>
<nl-NL>
Eerste publieke release. Sorteer je galerij door te swipen — bewaar wat je mooi vindt, stuur de rest naar de systeemprullenbak. Volledig privé, volledig offline.
</nl-NL>
```

---

### Categorization

- **App category**: Tools
- **Tags**: Photography, File management

### Data Safety

| Section | Réponse |
|---|---|
| Does your app collect or share any required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | N/A (no data collected) |
| Do you provide a way for users to request that their data be deleted? | N/A (no data collected) |
| Has your app been independently validated against a global security standard? | No |

→ Toutes les catégories : **Data not collected**, **Data not shared**.

### Content Rating (IARC questionnaire)

Réponds "Non" à toutes les questions sur violence, contenu sexuel, langage, jeux d'argent, drogues, etc. Résultat attendu : **Everyone / PEGI 3 / ESRB E**.

### Target Audience and Content
- **Target age groups**: 13+ (Apple a un seuil 4+ mais Google Play oblige à choisir un range où l'âge cible n'inclut pas les enfants si tu ne fais pas le COPPA-compliance)
- **Children's section**: No, the app is not directed at children.

### App Content
- **Ads**: **No**, my app does not contain ads.
- **App access**: **All functionality is available without restrictions** (pas de login).
- **Government apps**: No.
- **Financial features**: No.
- **News app**: No.
- **Health app**: No.

### Privacy Policy URL
```
https://unrolled-e4775.web.app/privacy
```
(Obligatoire dans Play Console → App content → Privacy policy.)

### Photo and Video Permissions Policy

Google Play exige une déclaration spécifique parce que tu demandes `READ_MEDIA_IMAGES` (full access). Justification à fournir :

```
Unrolled is a photo organizer. Its sole core feature is letting users swipe through their photo library to keep or delete each photo. Reading the entire image library (READ_MEDIA_IMAGES) is required so the user can sort across all their albums (Recents, custom folders, etc.) — restricting to user-selected photos (READ_MEDIA_VISUAL_USER_SELECTED only) would prevent the user from sorting photos they didn't pre-select, which defeats the entire purpose of the app.

The app:
- Reads photo metadata and pixel data only on the user's device.
- Never uploads, shares, or transmits any image data.
- Has no network capability beyond opening external URLs (Privacy Policy, Support) when the user taps them.
- Supports the partial-access flow (READ_MEDIA_VISUAL_USER_SELECTED) for users who prefer to grant access to a subset.
```

### Pricing & Distribution
- **Free**
- **Countries**: All available

### Release Track
1. **Internal testing** d'abord (jusqu'à 100 testeurs, propagation immédiate).
2. **Closed testing** ensuite si besoin de feedback (groupe nommé).
3. **Production** une fois validé.

### Build & Submission Checklist
- [ ] AAB généré : `./gradlew :app:bundleRelease` (pas APK pour Play Store)
- [ ] Signature : configurée via `signingConfigs.release` dans `app/build.gradle.kts` (keystore à `~/keystore.jks`, mots de passe dans `~/.gradle/gradle.properties`)
- [ ] Play App Signing activé côté Play Console
- [ ] Icône adaptive vérifiée sur Android 8+ (mipmap-anydpi-v26/ic_launcher.xml)
- [ ] Screenshots : minimum 2, max 8 par form factor (téléphone obligatoire ; tablette optionnel). Format JPEG ou PNG 24-bit (pas d'alpha), 320 px à 3840 px de côté, ratio 16:9 ou 9:16.
- [ ] Feature graphic : 1024×500 PNG ou JPEG (obligatoire pour Play Store)
- [ ] Privacy Policy URL reachable
- [ ] Data safety form complété
- [ ] Content rating obtenu
- [ ] Target audience défini
- [ ] French (France) locale ajoutée
- [ ] Permissions sensibles (READ_MEDIA_IMAGES) justifiées
- [ ] Promote to Production

---

## Notes communes

### Domaine Firebase
Les URL Privacy / Support pointent vers `unrolled-e4775.web.app/privacy` et `.../support`, sur le projet Firebase `unrolled-e4775` (région `europe-west1`). Avant la première soumission Store, vérifier que le site web a bien été déployé sur ce projet (`cd web && npm run build && firebase deploy --only hosting`) — sinon Apple / Google testent l'URL et la trouvent en 404. Migration future possible vers un domaine custom (`unrolled.app` ou similaire) — chantier séparé (DNS + Firebase Hosting custom domain + redirect).

### Ce qui change par plateforme
| | iOS | Android |
|---|---|---|
| Vocabulaire | "camera roll", "Recently Deleted" | "gallery", "system trash" |
| Trash recovery | iOS Photos → Recently Deleted (30j) | MediaStore.createTrashRequest → Bin (30j) |
| Permission justification | Implicite (NSPhotoLibraryUsageDescription) | Explicite (Photo & Video Permissions Policy declaration) |
| Keywords | 100 chars dédiés | Pas de champ — Google extrait du titre + description |
| Adaptive icon | Light + Dark + Tinted | mipmap-anydpi-v26 background + foreground |
