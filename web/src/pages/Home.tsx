const features = [
  {
    title: "Finite piles",
    body: "This month, on this day, a year ago, screenshots, videos, any album. A pile of 200 photos is something you can finish before the coffee gets cold.",
  },
  {
    title: "Swipe, undo, review",
    body: "Right to keep, left to the trash list. One tap brings the last decision back. Nothing leaves your device until you confirm in the system dialog.",
  },
  {
    title: "Similar shots grouped",
    body: "Bursts and near-duplicates are detected on the device with a perceptual hash, no cloud and no AI service. Keep the best one, drop the rest in one go.",
  },
  {
    title: "A session you can feel",
    body: "Every finished pile shows what you reviewed, what you kept, what you dropped and how much storage you are about to free.",
  },
  {
    title: "Calm goals",
    body: "A daily goal, a streak with a weekly freeze, a few milestones you can share. All optional, all stored on your phone, never ranked against anyone.",
  },
  {
    title: "Private by construction",
    body: "No account, no ads, no analytics, no subscription. The app does not ship an HTTP client. The only optional network call is the system geocoder that prints a city name.",
  },
];

export default function Home() {
  return (
    <main className="min-h-screen bg-[#F6F5F1] text-[#1A1A1A] dark:bg-[#0E0E12] dark:text-white">
      <section className="max-w-3xl mx-auto px-6 pt-20 pb-12">
        <p className="text-sm uppercase tracking-wide opacity-60">Unrolled</p>
        <h1 className="mt-4 text-5xl font-bold tracking-tight">
          Swipe your camera roll clean.
        </h1>
        <p className="mt-6 text-lg leading-relaxed opacity-80">
          A native photo cleaner for iPhone and Android. Pick a pile, swipe
          through it, empty the trash. Free, private, and built to be finished.
        </p>
        <div className="mt-8 flex flex-wrap gap-3">
          <a
            href="/support"
            className="rounded-full bg-[#EF3E5B] px-6 py-3 text-sm font-semibold text-white"
          >
            Get help
          </a>
          <a
            href="/privacy"
            className="rounded-full border border-current px-6 py-3 text-sm font-semibold opacity-80 hover:opacity-100"
          >
            Privacy policy
          </a>
        </div>
      </section>

      <section className="max-w-3xl mx-auto px-6 pb-20">
        <ul className="grid gap-6 sm:grid-cols-2">
          {features.map((feature) => (
            <li
              key={feature.title}
              className="rounded-2xl bg-white p-6 shadow-sm dark:bg-white/5"
            >
              <h2 className="text-lg font-semibold">{feature.title}</h2>
              <p className="mt-2 text-sm leading-relaxed opacity-80">
                {feature.body}
              </p>
            </li>
          ))}
        </ul>
        <p className="mt-12 text-sm opacity-60">
          Made by Julien Rahier. Questions:{" "}
          <a className="underline underline-offset-4" href="mailto:julien@rahier.dev">
            julien@rahier.dev
          </a>
        </p>
      </section>
    </main>
  );
}
