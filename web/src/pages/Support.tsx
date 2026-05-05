export default function Support() {
  return (
    <main className="min-h-screen bg-[#F6F5F1] text-[#1A1A1A] dark:bg-[#0E0E12] dark:text-white">
      <article className="max-w-2xl mx-auto px-6 py-16">
        <a
          href="/"
          className="text-sm uppercase tracking-wide opacity-60 hover:opacity-100"
        >
          ← Unrolled
        </a>
        <h1 className="mt-6 text-4xl font-bold tracking-tight">Support</h1>

        <section className="mt-10 space-y-6 text-base leading-relaxed">
          <p>
            Unrolled is a small app. If something isn't working the way you
            expect, or you have an idea for what would make it more useful,
            drop us a line.
          </p>

          <h2 className="text-xl font-semibold pt-4">Email</h2>
          <p>
            <a
              className="underline underline-offset-4"
              href="mailto:julien@rahier.dev"
            >
              julien@rahier.dev
            </a>
            <br />
            <span className="opacity-60 text-sm">
              We read every email. Response within a few days.
            </span>
          </p>

          <h2 className="text-xl font-semibold pt-4">Common questions</h2>

          <div className="space-y-5 pt-2">
            <div>
              <h3 className="font-semibold">
                Where do deleted photos actually go?
              </h3>
              <p className="mt-1 opacity-80">
                They go into your operating system's own "Recently Deleted"
                bucket (iOS Photos app → Recently Deleted, or Android Photos
                → Bin). Unrolled never deletes anything permanently. The OS
                keeps them around for 30 days so you can recover anything you
                discarded by mistake.
              </p>
            </div>

            <div>
              <h3 className="font-semibold">
                Can I undo a swipe?
              </h3>
              <p className="mt-1 opacity-80">
                Yes — the middle button in the action bar brings back your
                most recent swipe, kept or discarded. For bulk restoration,
                tap the "kept / discarded" pill at the top right to open the
                review sheet and "Garder" any photo back out of the trash
                staging list.
              </p>
            </div>

            <div>
              <h3 className="font-semibold">
                Does Unrolled send my photos anywhere?
              </h3>
              <p className="mt-1 opacity-80">
                No. The app has no networking code and no servers. Everything
                happens on your device. See the{" "}
                <a className="underline underline-offset-4" href="/privacy">
                  privacy policy
                </a>{" "}
                for details.
              </p>
            </div>

            <div>
              <h3 className="font-semibold">
                The app says "No photos found" but I have photos.
              </h3>
              <p className="mt-1 opacity-80">
                On Android 14 or later, the system lets you grant access to
                only a subset of photos. If you picked zero photos in that
                dialog, Unrolled has nothing to show. Open your device's app
                settings → Permissions → Photos and videos to adjust what
                Unrolled can see.
              </p>
            </div>
          </div>
        </section>
      </article>
    </main>
  );
}
