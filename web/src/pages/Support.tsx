const questions = [
  {
    question: "Where do deleted photos actually go?",
    answer:
      'They go into your operating system\'s own trash: "Recently Deleted" in the iOS Photos app, "Trash" or "Bin" in your Android gallery. Unrolled never deletes anything permanently. The OS keeps them around for 30 days so you can recover anything you discarded by mistake.',
  },
  {
    question: "Can I undo a swipe?",
    answer:
      'Yes. The middle button in the action bar brings back your most recent decision, kept or discarded, including a whole group of similar photos resolved at once. To restore several photos, tap the counter pill at the top right to open the review sheet and tap "Keep" next to any photo to pull it out of the trash list.',
  },
  {
    question: "What is a pile?",
    answer:
      'A pile is a finite set of photos: this month, on this day in earlier years, a year ago, screenshots, videos, one calendar month, or one album. Tap the pile title at the top left to pick another one. Each row shows how many photos are left so you can see what is close to being finished.',
  },
  {
    question: "I finished a pile. How do I go through it again?",
    answer:
      'Tap "Start over" on the "Pile finished" screen: it forgets the decisions you took on that pile\'s photos and shows every photo again. To forget every decision everywhere, open the counter pill and tap "Reset".',
  },
  {
    question: "How does the similar photos badge work?",
    answer:
      'When several photos were shot within a few seconds and look alike, the top card shows a "similar" badge. Tap it to see the group, tick the ones to keep and confirm. The comparison runs on your device with a tiny fingerprint of each thumbnail; switch it off in Settings under "Detect similar photos".',
  },
  {
    question: "What counts as storage freed?",
    answer:
      'The size of the files you actually trashed through "Empty trash". The session summary shows the size you are about to free; the stats sheet shows the total since you installed the app or last reset the stats.',
  },
  {
    question: "How do goals and streaks work?",
    answer:
      "The ring in the header fills with the photos you reviewed today against your daily goal (25 to 200, in Settings). Reaching the goal on consecutive days builds a streak; one missed day per week is forgiven automatically. Turn the whole thing off in Settings under \"Goals and streaks\" if you only want the tool.",
  },
  {
    question: "Does Unrolled send my photos anywhere?",
    answer:
      'No. Your photos never leave your device. The only thing that can be sent is the GPS coordinates of the photo on screen, to the system geocoding service, so the app can print a city name under it. Switch off "Show location" in Settings to disable that. See the privacy policy for details.',
  },
  {
    question: 'The app says "No photos found" but I have photos.',
    answer:
      'On Android 14 or later and on iOS, the system lets you grant access to only a subset of photos. If you picked zero photos in that dialog, Unrolled has nothing to show. Use the "Change selection" button in the app, or open your device\'s app settings and adjust the Photos permission for Unrolled.',
  },
  {
    question: "I do not see my videos.",
    answer:
      'Videos are part of the deck by default. If you turned them off, enable "Include videos" in Settings. On Android 13 and later the app also needs the "Photos and videos" permission, which you can adjust in the system settings.',
  },
];

export default function Support() {
  return (
    <main className="min-h-screen bg-[#F6F5F1] text-[#1A1A1A] dark:bg-[#0E0E12] dark:text-white">
      <article className="max-w-2xl mx-auto px-6 py-16">
        <a
          href="/"
          className="text-sm uppercase tracking-wide opacity-60 hover:opacity-100"
        >
          Unrolled
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
            {questions.map((item) => (
              <div key={item.question}>
                <h3 className="font-semibold">{item.question}</h3>
                <p className="mt-1 opacity-80">{item.answer}</p>
              </div>
            ))}
            <p className="opacity-80">
              Privacy details live in the{" "}
              <a className="underline underline-offset-4" href="/privacy">
                privacy policy
              </a>
              .
            </p>
          </div>
        </section>
      </article>
    </main>
  );
}
