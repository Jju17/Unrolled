export default function Privacy() {
  return (
    <main className="min-h-screen bg-[#F6F5F1] text-[#1A1A1A] dark:bg-[#0E0E12] dark:text-white">
      <article className="max-w-2xl mx-auto px-6 py-16">
        <a
          href="/"
          className="text-sm uppercase tracking-wide opacity-60 hover:opacity-100"
        >
          ← Unrolled
        </a>
        <h1 className="mt-6 text-4xl font-bold tracking-tight">
          Privacy Policy
        </h1>
        <p className="mt-2 text-sm opacity-60">Last updated: April 2026</p>

        <section className="mt-10 space-y-6 text-base leading-relaxed">
          <p>
            <strong>Unrolled collects no personal data.</strong> The app
            processes your photos entirely on your device. Nothing is sent to
            any server — not to us, not to any third party.
          </p>

          <h2 className="text-xl font-semibold pt-4">What Unrolled does</h2>
          <p>
            Unrolled is a photo-management utility. It reads the photos in
            your device's library so you can review them one at a time and
            decide whether to keep or discard each one. When you choose to
            discard photos, Unrolled hands the list to your operating system's
            own confirmation dialog — the actual deletion is performed by your
            device, not by us, and trashed photos go to the system's
            "Recently Deleted" bucket where your OS lets you recover them.
          </p>

          <h2 className="text-xl font-semibold pt-4">What we do not do</h2>
          <ul className="list-disc pl-6 space-y-2">
            <li>We do not upload, copy, or transmit your photos.</li>
            <li>We do not have accounts, logins, or profiles.</li>
            <li>
              We do not collect analytics, telemetry, or crash reports about
              your usage.
            </li>
            <li>We do not use advertising or tracking SDKs.</li>
            <li>We do not share anything with third parties.</li>
          </ul>

          <h2 className="text-xl font-semibold pt-4">Permissions</h2>
          <p>
            Unrolled asks for photo library access because the whole point of
            the app is to let you swipe through every photo you have. On
            Android 14+, you can choose to grant access to a subset of photos
            instead of the full library; Unrolled will work with whatever
            subset you grant. On iOS, the system's own photo picker / access
            prompt controls this.
          </p>
          <p>
            Unrolled requests no other permissions — no camera, no location,
            no microphone, no contacts, no network.
          </p>

          <h2 className="text-xl font-semibold pt-4">Data retention</h2>
          <p>
            There is nothing to retain. The app keeps no server-side records
            of you or your photos.
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
            above will change. Because the app collects nothing, material
            changes are unlikely.
          </p>
        </section>
      </article>
    </main>
  );
}
