export default function Privacy() {
  return (
    <main className="min-h-screen bg-[#F6F5F1] text-[#1A1A1A] dark:bg-[#0E0E12] dark:text-white">
      <article className="max-w-2xl mx-auto px-6 py-16">
        <a
          href="/"
          className="text-sm uppercase tracking-wide opacity-60 hover:opacity-100"
        >
          Unrolled
        </a>
        <h1 className="mt-6 text-4xl font-bold tracking-tight">
          Privacy Policy
        </h1>
        <p className="mt-2 text-sm opacity-60">Last updated: October 2026</p>

        <section className="mt-10 space-y-6 text-base leading-relaxed">
          <p>
            <strong>Unrolled collects no personal data.</strong> The app
            processes your photos and videos entirely on your device. We run
            no server, we have no account system, and nothing about you or
            your library is sent to us.
          </p>

          <h2 className="text-xl font-semibold pt-4">What Unrolled does</h2>
          <p>
            Unrolled is a photo-management utility. It reads the photos and
            videos in your device's library so you can review them one at a
            time, in piles (a month, a day, screenshots, an album), and decide
            whether to keep or discard each one. When you choose to discard
            items, Unrolled hands the list to your operating system's own
            confirmation dialog: the actual deletion is performed by your
            device, not by us, and trashed items go to the system's
            "Recently Deleted" bucket where your OS lets you recover them.
          </p>

          <h2 className="text-xl font-semibold pt-4">What stays on your device</h2>
          <p>
            Unrolled remembers which photos you have already kept or staged
            for deletion so it does not show them again. It also keeps a few
            activity counters: photos reviewed, photos deleted, storage freed,
            today's count, your streak and the milestones you reached. All of
            this contains only photo identifiers and numbers, is stored
            locally in the app's own preferences, and is removed when you
            uninstall the app, tap "Reset" in the trash sheet (decisions) or
            "Reset stats" in the stats sheet (counters).
          </p>

          <h2 className="text-xl font-semibold pt-4">Similar photo detection</h2>
          <p>
            To group near-duplicate shots, the app computes a tiny
            fingerprint (a 64-bit perceptual hash) from a 9 by 8 pixel
            thumbnail of each photo. This happens on your device, in memory,
            and the fingerprints are never stored or transmitted. You can
            switch the feature off in Settings with "Detect similar photos".
          </p>

          <h2 className="text-xl font-semibold pt-4">Place names (optional)</h2>
          <p>
            Each photo can show the city where it was taken. To turn GPS
            coordinates into a city name, the app asks the geocoding service
            built into your operating system (Apple on iOS, Google on
            Android). Only the coordinates of the photo being displayed are
            sent, never the photo itself or anything that identifies you. You
            can switch this off at any time in Settings with "Show location";
            when it is off, no coordinates leave your device.
          </p>

          <h2 className="text-xl font-semibold pt-4">Reminders (optional)</h2>
          <p>
            You can ask the app for a daily reminder at a time you choose.
            The reminder is a local notification scheduled by your device;
            no push service and no server are involved. It is off by default
            and requires your explicit permission.
          </p>

          <h2 className="text-xl font-semibold pt-4">Share cards</h2>
          <p>
            When you reach a milestone, the app can draw an image with the
            app name and your totals (for example "1,000 photos sorted") and
            hand it to the system share sheet. The image contains no photo
            from your library. Nothing is shared unless you pick a
            destination yourself.
          </p>

          <h2 className="text-xl font-semibold pt-4">What we do not do</h2>
          <ul className="list-disc pl-6 space-y-2">
            <li>We do not upload, copy, or transmit your photos or videos.</li>
            <li>We do not have accounts, logins, or profiles.</li>
            <li>
              We do not collect analytics, telemetry, or crash reports about
              your usage.
            </li>
            <li>We do not use advertising or tracking SDKs.</li>
            <li>We do not share anything with third parties.</li>
            <li>We do not rank or compare you with other users.</li>
          </ul>

          <h2 className="text-xl font-semibold pt-4">Permissions</h2>
          <p>
            Unrolled asks for photo and video library access because the
            whole point of the app is to let you swipe through every item you
            have. On Android 14 and later and on iOS, you can choose to grant
            access to a subset of items instead of the full library; Unrolled
            works with whatever subset you grant. On Android, the app also
            requests access to the location metadata stored inside your
            photos, which is only used for the optional place names described
            above, and the notification permission, only when you enable the
            daily reminder.
          </p>
          <p>
            Unrolled requests no other permissions: no camera, no device
            location, no microphone, no contacts.
          </p>

          <h2 className="text-xl font-semibold pt-4">Data retention</h2>
          <p>
            There is nothing to retain on our side. The app keeps no
            server-side records of you or your photos.
          </p>

          <h2 className="text-xl font-semibold pt-4">Children</h2>
          <p>
            Unrolled is not targeted at children. It is classified "Everyone"
            / PEGI 3 because it contains no objectionable content and
            collects no data, but it is a utility for managing a personal
            photo library and is aimed at general users.
          </p>

          <h2 className="text-xl font-semibold pt-4">Contact</h2>
          <p>
            Questions or concerns? Reach out at{" "}
            <a
              className="underline underline-offset-4"
              href="mailto:julien@rahier.dev"
            >
              julien@rahier.dev
            </a>
            .
          </p>

          <h2 className="text-xl font-semibold pt-4">Changes</h2>
          <p>
            If this policy ever changes materially, the "Last updated" date
            above will change.
          </p>
        </section>
      </article>
    </main>
  );
}
