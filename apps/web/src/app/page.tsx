/* eslint-disable @next/next/no-img-element -- AVIF screenshots at fixed sizes need no optimisation */
import Link from "next/link";
import { SOCIAL_MARKS } from "@/lib/brand-marks";
import {
  FAQS,
  FEATURES,
  FOOTER_COLUMNS,
  HERO_FACTS,
  PLANS,
  SAMPLE_QUOTES,
  STEPS,
  type Feature,
} from "@/lib/landing";

export const metadata = {
  title: { absolute: "SwiftDrop: send a parcel anywhere in Sri Lanka, priced up front and tracked live" },
  description:
    "Get the exact delivery price before you book, have a courier collect at your door, and follow every parcel live across all 25 districts.",
};

const WRAP = "mx-auto w-full max-w-[1248px] px-6";
const BAND = "py-[85px] pb-16 md:pt-32 md:pb-[85px]";
const H2 = "font-display text-[40px] font-extrabold leading-[1.02] tracking-[-0.03em] text-ink md:text-[56px]";
const SHARE_URL = "https://swiftdrop.lk/";

export default function HomePage() {
  return (
    <>
      <Hero />
      <Features />
      <Process />
      <Testimonials />
      <Pricing />
      <Questions />
      <Closing />
      <Footer />
    </>
  );
}

// ---------------------------------------------------------------- 2. hero

function Hero() {
  return (
    <section aria-labelledby="hero-title" className="bg-ground-alt pt-[85px] pb-16 md:pt-32 md:pb-28">
      <div className={`${WRAP} grid items-center gap-12 lg:grid-cols-2 lg:gap-[72px]`}>
        <div>
          <h1
            id="hero-title"
            className="font-display text-[62px] font-extrabold leading-[0.98] tracking-[-0.035em] text-ink md:text-[83px]"
          >
            Send it today.
            <br />
            Watch it arrive.
          </h1>
          <p className="mt-7 max-w-[30em] text-[17px] leading-[1.55] text-ink-2 md:text-[19px]">
            Get the exact price before you book, hand the parcel to a courier at your door, and follow it live across all
            25 districts until it is signed for.
          </p>
          <div className="mt-9 flex flex-wrap gap-3">
            <Link href="/login?callbackUrl=%2Fcustomer%2Fbook" className="btn-primary px-6 py-3.5 text-[15px]">
              Send a parcel
            </Link>
            <Link href="/quote" className="btn-secondary px-6 py-3.5 text-[15px]">
              Check a price
            </Link>
          </div>
          <p className="mt-7 flex items-center gap-3 text-sm text-ink-3">
            <Tick />
            No account needed to get a price or track a parcel.
          </p>
          <dl className="mt-11 grid grid-cols-2 gap-x-6 gap-y-7 border-t border-hairline pt-8 sm:grid-cols-4">
            {HERO_FACTS.map((fact) => (
              <div key={fact.label}>
                <dt className="sr-only">{fact.label}</dt>
                <dd className="whitespace-nowrap font-display text-[34px] font-extrabold leading-none tracking-[-0.02em] tabular-nums text-ink">
                  {fact.value}
                </dd>
                <dd className="mt-2.5 text-sm leading-snug text-ink-3">{fact.label}</dd>
              </div>
            ))}
          </dl>
        </div>
        <figure className="mx-auto w-full max-w-[520px] lg:justify-self-end">
          <BrowserFrame url="swiftdrop.lk/quote">
            <img
              src="/landing/hero-quote.avif"
              width={880}
              height={987}
              alt="The SwiftDrop price calculator: a Colombo to Kandy parcel at LKR 1,722.65 with each charge on its own line"
              className="block h-auto w-full"
            />
          </BrowserFrame>
          <figcaption className="mt-4 text-center text-xs text-ink-3">A real quote, held for 15 minutes.</figcaption>
        </figure>
      </div>
    </section>
  );
}

function Tick() {
  return (
    <svg aria-hidden="true" viewBox="0 0 20 20" className="h-5 w-5 shrink-0">
      <circle cx="10" cy="10" r="10" className="fill-inverse" />
      <path d="M6 10.4l2.6 2.6L14.2 7.4" fill="none" className="stroke-inverse-ink" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function BrowserFrame({ url, children }: { url: string; children: React.ReactNode }) {
  return (
    <div className="overflow-hidden rounded-2xl border border-hairline bg-white shadow-lift">
      <div aria-hidden="true" className="flex h-10 items-center gap-2 bg-frame-chrome px-4">
        <span className="h-2.5 w-2.5 rounded-full bg-slate-300" />
        <span className="h-2.5 w-2.5 rounded-full bg-slate-300" />
        <span className="h-2.5 w-2.5 rounded-full bg-slate-300" />
        <span className="ml-4 flex h-6 max-w-[260px] flex-1 items-center rounded-full bg-ground px-3 text-xs text-ink-3">{url}</span>
      </div>
      {children}
    </div>
  );
}

// ---------------------------------------------------------------- 4. features

function Features() {
  return (
    <section id="stores" aria-labelledby="features-title" className={`scroll-mt-6 bg-ground ${BAND}`}>
      <div className={WRAP}>
        <div className="mx-auto max-w-[760px] text-center">
          <h2 id="features-title" className={H2}>
            Everything between the counter and the door
          </h2>
          <p className="mx-auto mt-5 max-w-[34em] text-[17px] leading-relaxed text-ink-2">
            One service for the person sending, the courier carrying and the shop selling, all looking at the same live
            parcel.
          </p>
        </div>
        <div className="mt-16 space-y-20 md:mt-24 md:space-y-28">
          {FEATURES.map((feature, index) => (
            <FeatureRow key={feature.id} feature={feature} flip={index % 2 === 1} />
          ))}
        </div>
      </div>
    </section>
  );
}

function FeatureRow({ feature, flip }: { feature: Feature; flip: boolean }) {
  return (
    <article id={`feature-${feature.id}`} className="grid items-center gap-10 lg:grid-cols-2 lg:gap-[88px]">
      <Stage feature={feature} className={flip ? "lg:order-2" : ""} />
      <div className={flip ? "lg:order-1" : ""}>
        <p className="text-sm font-semibold text-brand-700">{feature.eyebrow}</p>
        <h3 className="mt-3 font-display text-[30px] font-extrabold leading-[1.08] tracking-[-0.025em] text-ink md:text-[38px]">
          {feature.title}
        </h3>
        <p className="mt-5 max-w-[32em] text-[17px] leading-relaxed text-ink-2">{feature.body}</p>
        <ul className="mt-6 space-y-2.5">
          {feature.points.map((point) => (
            <li key={point} className="flex items-start gap-3 text-[15px] text-ink-2">
              <span aria-hidden="true" className="mt-[9px] h-1.5 w-1.5 shrink-0 rounded-full bg-ink" />
              {point}
            </li>
          ))}
        </ul>
        <Link
          href={feature.link.href}
          className="mt-7 inline-flex items-center gap-1.5 rounded-md text-[15px] font-semibold text-ink underline decoration-hairline decoration-2 underline-offset-[6px] hover:decoration-brand-600"
        >
          {feature.link.label}
          <span aria-hidden="true">&rarr;</span>
        </Link>
      </div>
    </article>
  );
}

/**
 * A real screen staged on a lit surface: a soft top light, the screen tilted a few degrees towards
 * the reader, and a floor shadow under it from the same light.
 */
function Stage({ feature, className }: { feature: Feature; className: string }) {
  const { image } = feature;
  return (
    <figure
      className={`relative overflow-hidden rounded-2xl stage-light px-6 pt-10 pb-14 sm:px-12 sm:pt-14 sm:pb-16 ${className}`}
    >
      <div aria-hidden="true" className="absolute inset-x-0 bottom-0 h-[34%] bg-stage-floor" />
      <div
        aria-hidden="true"
        className="absolute bottom-[9%] left-1/2 h-8 w-[72%] -translate-x-1/2 floor-shadow"
      />
      <div className="relative [perspective:1400px]">
        {image.device === "phone" ? (
          <div className="mx-auto w-[min(62%,260px)] [transform:rotateY(-8deg)_rotateX(4deg)] [transform-style:preserve-3d]">
            <div className="rounded-[38px] bg-inverse p-2.5 shadow-device">
              <img src={image.src} width={image.width} height={image.height} alt={image.alt} className="block h-auto w-full rounded-[30px]" />
            </div>
          </div>
        ) : (
          <div className="mx-auto max-w-[520px] [transform:rotateY(-7deg)_rotateX(4deg)] [transform-style:preserve-3d]">
            <BrowserFrame url={feature.id === "stores" ? "swiftdrop.lk/merchant" : "swiftdrop.lk/customer/parcels"}>
              <img src={image.src} width={image.width} height={image.height} alt={image.alt} className="block h-auto w-full" />
            </BrowserFrame>
          </div>
        )}
      </div>
    </figure>
  );
}

// ---------------------------------------------------------------- 5. process

function Process() {
  return (
    <section aria-labelledby="process-title" className={`bg-ground-alt ${BAND}`}>
      <div className={`${WRAP} grid gap-12 lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)] lg:gap-[88px]`}>
        <div>
          <h2 id="process-title" className={H2}>
            From quote to doorstep in three steps
          </h2>
          <p className="mt-5 max-w-[26em] text-[17px] leading-relaxed text-ink-2">
            No forms to print and no counter to visit. The courier comes to you.
          </p>
        </div>
        <ol className="relative">
          {STEPS.map((step, index) => (
            <li key={step.title} className="relative grid grid-cols-[48px_minmax(0,1fr)] gap-6 pb-12 last:pb-0">
              {index < STEPS.length - 1 ? (
                <span aria-hidden="true" className="absolute left-[23px] top-12 bottom-0 w-0.5 bg-hairline" />
              ) : null}
              <span className="relative grid h-12 w-12 place-items-center rounded-full bg-inverse font-display text-lg font-extrabold text-inverse-ink">
                {index + 1}
              </span>
              <div className="pt-2.5">
                <h3 className="font-display text-[22px] font-extrabold tracking-[-0.015em] text-ink">{step.title}</h3>
                <p className="mt-2 max-w-[34em] text-[16px] leading-relaxed text-ink-2">{step.body}</p>
              </div>
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}

// ---------------------------------------------------------------- 6. testimonials

function Avatar({ initials }: { initials: string }) {
  return (
    <span aria-hidden="true" className="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-ground-alt text-sm font-bold text-ink ring-1 ring-hairline">
      {initials}
    </span>
  );
}

function Testimonials() {
  const [lead, ...rest] = SAMPLE_QUOTES;
  return (
    <section aria-labelledby="quotes-title" className={`bg-ground ${BAND}`}>
      <div className={WRAP}>
        <div className="flex flex-wrap items-end justify-between gap-4">
          <h2 id="quotes-title" className={H2}>
            Why people switch
          </h2>
          <p className="rounded-full border border-ink/25 px-3 py-1 text-xs font-medium text-ink-2">
            Sample quotes: replace with real customer feedback
          </p>
        </div>
        <figure className="mt-14">
          <span aria-hidden="true" className="block font-display text-[72px] font-extrabold leading-[0.6] text-ink">
            &ldquo;
          </span>
          <blockquote className="mt-6 max-w-[22em] font-display text-[30px] font-extrabold leading-[1.12] tracking-[-0.025em] text-ink md:text-[48px]">
            {lead.text}
          </blockquote>
          <figcaption className="mt-8 flex items-center gap-3">
            <Avatar initials={lead.initials} />
            <span>
              <span className="block text-[15px] font-semibold text-ink">{lead.source}</span>
              <span className="block text-sm text-ink-3">{lead.context}</span>
            </span>
          </figcaption>
        </figure>
        <div className="mt-16 grid gap-10 border-t border-hairline pt-12 md:grid-cols-2 md:gap-16">
          {rest.map((quote) => (
            <figure key={quote.context}>
              <blockquote className="text-[19px] leading-relaxed text-ink">&ldquo;{quote.text}&rdquo;</blockquote>
              <figcaption className="mt-6 flex items-center gap-3">
                <Avatar initials={quote.initials} />
                <span>
                  <span className="block text-[15px] font-semibold text-ink">{quote.source}</span>
                  <span className="block text-sm text-ink-3">{quote.context}</span>
                </span>
              </figcaption>
            </figure>
          ))}
        </div>
      </div>
    </section>
  );
}

// ---------------------------------------------------------------- 7. pricing

function Pricing() {
  return (
    <section id="pricing" aria-labelledby="pricing-title" className={`scroll-mt-6 bg-ground-alt ${BAND}`}>
      <div className={WRAP}>
        <div className="mx-auto max-w-[720px] text-center">
          <h2 id="pricing-title" className={H2}>
            Pay per parcel, or agree your rates
          </h2>
          <p className="mx-auto mt-5 max-w-[32em] text-[17px] leading-relaxed text-ink-2">
            Every price is worked out from the same published rules, shown line by line before anyone pays.
          </p>
        </div>
        <ul className="mt-16 grid items-stretch gap-6 lg:grid-cols-3 lg:gap-8">
          {PLANS.map((plan) => (
            <li
              key={plan.name}
              className={`flex flex-col rounded-2xl p-8 ${plan.recommended ? "border-2 border-ink bg-white" : "border border-hairline bg-ground"}`}
            >
              <div className="flex items-center justify-between gap-3">
                <h3 className="text-[17px] font-semibold text-ink">{plan.name}</h3>
                {plan.recommended ? (
                  <span className="rounded-full bg-brand-600 px-2.5 py-1 text-xs font-semibold text-on-accent">Recommended</span>
                ) : null}
              </div>
              <p className="mt-6 font-display text-[44px] font-extrabold leading-none tracking-[-0.03em] text-ink">{plan.price}</p>
              <p className="mt-2 text-sm text-ink-3">{plan.priceNote}</p>
              <p className="mt-5 text-[15px] text-ink-2">{plan.summary}</p>
              <Link
                href={plan.action.href}
                className={`mt-7 w-full py-3 text-[15px] ${plan.recommended ? "btn-primary" : "btn-secondary"}`}
              >
                {plan.action.label}
              </Link>
              <ul className="mt-8 space-y-3.5 border-t border-hairline pt-7">
                {plan.features.map((item) => (
                  <li key={item} className="flex items-start gap-3 text-[15px] text-ink-2">
                    <Check />
                    {item}
                  </li>
                ))}
              </ul>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}

function Check() {
  return (
    <svg aria-hidden="true" viewBox="0 0 20 20" className="mt-0.5 h-5 w-5 shrink-0">
      <circle cx="10" cy="10" r="10" className="fill-ground-alt" />
      <path d="M6 10.4l2.6 2.6L14.2 7.4" fill="none" className="stroke-ink" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

// ---------------------------------------------------------------- 8. questions

function Questions() {
  return (
    <section id="help" aria-labelledby="faq-title" className={`scroll-mt-6 bg-ground ${BAND}`}>
      <div className={`${WRAP} grid gap-12 lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)] lg:gap-[88px]`}>
        <div className="lg:sticky lg:top-10 lg:self-start">
          <h2 id="faq-title" className={H2}>
            Questions, answered
          </h2>
          <p className="mt-5 max-w-[24em] text-[17px] leading-relaxed text-ink-2">
            Anything else? The SwiftDrop assistant in the corner of every page can answer with your own parcels in view.
          </p>
          <a
            href="mailto:hello@swiftdrop.lk"
            className="mt-6 inline-flex items-center gap-1.5 rounded-md text-[15px] font-semibold text-ink underline decoration-hairline decoration-2 underline-offset-[6px] hover:decoration-brand-600"
          >
            Write to us <span aria-hidden="true">&rarr;</span>
          </a>
        </div>
        <div className="border-t border-hairline">
          {FAQS.map((faq, index) => (
            <details key={faq.question} className="group border-b border-hairline" open={index === 0}>
              <summary className="flex cursor-pointer list-none items-center justify-between gap-6 rounded-md py-6 text-[17px] font-semibold text-ink [&::-webkit-details-marker]:hidden">
                {faq.question}
                <span
                  aria-hidden="true"
                  className="grid h-9 w-9 shrink-0 place-items-center rounded-full border border-hairline bg-ground-alt text-ink transition-transform duration-200 group-open:rotate-45 motion-reduce:transition-none"
                >
                  <svg viewBox="0 0 16 16" className="h-3.5 w-3.5">
                    <path d="M8 2v12M2 8h12" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
                  </svg>
                </span>
              </summary>
              <p className="max-w-[38em] pb-7 pr-14 text-[16px] leading-relaxed text-ink-2">{faq.answer}</p>
            </details>
          ))}
        </div>
      </div>
    </section>
  );
}

// ---------------------------------------------------------------- 9. closing

function Closing() {
  return (
    <section aria-labelledby="closing-title" className="bg-inverse py-20 md:py-24">
      <div className={`${WRAP} flex flex-col gap-10 lg:flex-row lg:items-center lg:justify-between`}>
        <div className="flex flex-col gap-6 sm:flex-row sm:items-center sm:gap-8">
          <img src="/brand/swiftdrop-logo-dark.avif" alt="" width={640} height={324} className="h-14 w-auto shrink-0 self-start object-contain sm:self-center" />
          <h2
            id="closing-title"
            className="font-display text-[40px] font-extrabold leading-[1.02] tracking-[-0.03em] text-inverse-ink md:text-[56px]"
          >
            Your next parcel, <span className="whitespace-nowrap">already priced.</span>
          </h2>
        </div>
        <div className="flex shrink-0 flex-wrap gap-3">
          <Link href="/login?callbackUrl=%2Fcustomer%2Fbook" className="btn-primary px-6 py-3.5 text-[15px] focus-visible:ring-offset-inverse">
            Send a parcel
          </Link>
          <Link
            href="/quote"
            className="btn border border-inverse-ink/70 px-6 py-3.5 text-[15px] text-inverse-ink hover:bg-inverse-ink/10 focus-visible:ring-offset-inverse"
          >
            Check a price
          </Link>
        </div>
      </div>
    </section>
  );
}

// ---------------------------------------------------------------- 10. footer

function Footer() {
  const share = [
    { mark: SOCIAL_MARKS[0], href: `https://www.facebook.com/sharer/sharer.php?u=${encodeURIComponent(SHARE_URL)}` },
    { mark: SOCIAL_MARKS[1], href: `https://x.com/intent/post?url=${encodeURIComponent(SHARE_URL)}` },
    { mark: SOCIAL_MARKS[2], href: `https://wa.me/?text=${encodeURIComponent(`SwiftDrop parcel delivery ${SHARE_URL}`)}` },
  ];
  return (
    <footer className="bg-ground-alt pt-20 pb-10">
      <div className={WRAP}>
        <div className="grid grid-cols-2 gap-x-6 gap-y-12 lg:grid-cols-[minmax(0,4fr)_repeat(3,minmax(0,2fr))]">
          <div className="col-span-2 lg:col-span-1">
            <img src="/brand/swiftdrop-logo-light.avif" alt="SwiftDrop" width={640} height={322} className="h-12 w-auto" />
            <p className="mt-5 max-w-[22em] text-[15px] leading-relaxed text-ink-2">
              Parcel delivery to all 25 districts of Sri Lanka, priced before you book and tracked to the door.
            </p>
            <p className="mt-4 text-sm text-ink-3">Colombo, Sri Lanka</p>
          </div>
          {FOOTER_COLUMNS.map((column) => (
            <nav key={column.title} aria-label={column.title}>
              <h2 className="text-[15px] font-semibold text-ink">{column.title}</h2>
              <ul className="mt-4 space-y-3">
                {column.links.map((link) => (
                  <li key={link.label}>
                    <Link href={link.href} className="rounded-sm text-[15px] text-ink-2 hover:text-ink hover:underline hover:underline-offset-4">
                      {link.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </nav>
          ))}
        </div>
        <div className="mt-16 flex flex-col gap-6 border-t border-hairline pt-8 md:flex-row md:items-center md:justify-between">
          <div className="text-sm text-ink-3">
            <p>&copy; 2026 SwiftDrop. Prices are estimates until you book; the booked price is the price you pay.</p>
          </div>
          <div className="flex items-center gap-4">
            <span className="text-sm text-ink-3">Share SwiftDrop</span>
            <ul className="flex items-center gap-2">
              {share.map(({ mark, href }) => (
                <li key={mark.name}>
                  <a
                    href={href}
                    target="_blank"
                    rel="noopener noreferrer"
                    aria-label={`Share SwiftDrop on ${mark.name} (opens in a new tab)`}
                    className="grid h-10 w-10 place-items-center rounded-full border border-hairline text-ink-2 hover:border-ink hover:text-ink"
                  >
                    <svg aria-hidden="true" viewBox="0 0 24 24" className="h-[18px] w-[18px]" fill="currentColor">
                      <path d={mark.path} />
                    </svg>
                  </a>
                </li>
              ))}
            </ul>
          </div>
        </div>
      </div>
    </footer>
  );
}
