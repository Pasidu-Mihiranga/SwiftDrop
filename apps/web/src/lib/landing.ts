/**
 * Copy and facts for the landing page. Every number here comes from how SwiftDrop is configured
 * (districts, quote lifetime, service levels, the seeded minimum fee); the testimonials are sample
 * text, marked as such on the page, until real customer feedback replaces them.
 */

export interface KeyFact {
  value: string;
  label: string;
}

export const HERO_FACTS: readonly KeyFact[] = [
  { value: "25", label: "districts, island‑wide" },
  { value: "15 min", label: "your quoted price is held" },
  { value: "3", label: "speeds, standard to same day" },
  { value: "LKR 350", label: "lowest delivery fee" },
];

export interface Feature {
  id: string;
  eyebrow: string;
  title: string;
  body: string;
  points: readonly string[];
  link: { href: string; label: string };
  image: { src: string; width: number; height: number; alt: string; device: "browser" | "phone" };
}

export const FEATURES: readonly Feature[] = [
  {
    id: "tracking",
    eyebrow: "For senders",
    title: "See where your parcel is, not where it should be",
    body:
      "Every scan by the courier updates the parcel's timeline the moment it happens, from collection to the photo taken at the door.",
    points: ["Live timeline with every status", "Proof of delivery photo", "Receipts in your history"],
    link: { href: "/track", label: "Track a parcel" },
    image: {
      src: "/landing/feature-tracking.avif",
      width: 1040,
      height: 856,
      alt: "A customer's parcel page with its delivery timeline",
      device: "browser",
    },
  },
  {
    id: "couriers",
    eyebrow: "For couriers",
    title: "A run sheet that fits in one hand",
    body:
      "Couriers see today's parcels in order, open one, and move it to the next step with a single tap. A failed attempt records why, so the sender knows straight away.",
    points: ["Only the valid next step is offered", "Photo upload for proof", "Works on any phone browser"],
    link: { href: "/login?callbackUrl=%2Fcourier", label: "Courier sign-in" },
    image: {
      src: "/landing/feature-courier.avif",
      width: 780,
      height: 1400,
      alt: "The courier's parcel screen on a phone, showing the delivery steps",
      device: "phone",
    },
  },
  {
    id: "stores",
    eyebrow: "For online stores",
    title: "Orders from your shop, updates back to it",
    body:
      "Create deliveries from your store's system with OAuth client credentials, and get a signed webhook for every status change, retried until your endpoint answers.",
    points: ["REST API with idempotency keys", "HMAC-signed webhooks", "Agreed rates per lane"],
    link: { href: "/merchant/register", label: "Register your store" },
    image: {
      src: "/landing/feature-stores.avif",
      width: 1040,
      height: 856,
      alt: "The merchant portal with webhook deliveries and API details",
      device: "browser",
    },
  },
];

export interface Step {
  title: string;
  body: string;
}

export const STEPS: readonly Step[] = [
  {
    title: "Get a price",
    body: "Choose the two districts, the weight and the speed. The full breakdown appears at once, no account needed.",
  },
  {
    title: "Book a pickup",
    body: "Sign in, add the addresses and confirm. The price you were quoted is the price you pay.",
  },
  {
    title: "Follow it to the door",
    body: "A courier collects it and every step shows up live until the recipient signs for it.",
  },
];

export interface Quote {
  text: string;
  /** Who would say this. Sample personas, not named people. */
  source: string;
  context: string;
  initials: string;
}

/** Sample text that shows the layout. Replace with real, attributed customer feedback. */
export const SAMPLE_QUOTES: readonly Quote[] = [
  {
    text: "I quote the delivery to my customers before they pay, because the price I see is the price I am charged.",
    source: "Sample quote",
    context: "Online bookshop, Colombo",
    initials: "OB",
  },
  {
    text: "Orders flow in from our shop and the status comes back by webhook. We stopped answering where-is-my-parcel calls.",
    source: "Sample quote",
    context: "Clothing store, Kandy",
    initials: "CS",
  },
  {
    text: "The run sheet is in delivery order and one tap moves a parcel on. No paperwork at the end of the day.",
    source: "Sample quote",
    context: "Courier, Western Province",
    initials: "CW",
  },
];

export interface Plan {
  name: string;
  price: string;
  priceNote: string;
  summary: string;
  features: readonly string[];
  action: { href: string; label: string };
  recommended?: boolean;
}

export const PLANS: readonly Plan[] = [
  {
    name: "Pay per parcel",
    price: "LKR 350",
    priceNote: "from, per parcel",
    summary: "For anyone sending now and then.",
    features: [
      "Exact price before you book",
      "Standard, express and same day",
      "Cash on delivery available",
      "Live tracking and receipts",
    ],
    action: { href: "/quote", label: "Get a price" },
  },
  {
    name: "Online stores",
    price: "Agreed rates",
    priceNote: "set per lane when approved",
    summary: "For shops sending every day.",
    features: [
      "Order API and signed webhooks",
      "Discounts on the lanes you use",
      "Optional waived fees",
      "Merchant portal and order history",
    ],
    action: { href: "/merchant/register", label: "Register your store" },
    recommended: true,
  },
  {
    name: "Contract",
    price: "Custom",
    priceNote: "for regular volume",
    summary: "For businesses with scheduled pickups.",
    features: [
      "Volume pricing per lane",
      "Same-day vans up to 100 kg",
      "Remote-area terms",
      "A named contact",
    ],
    action: { href: "mailto:hello@swiftdrop.lk?subject=Contract%20delivery", label: "Talk to us" },
  },
];

export interface Faq {
  question: string;
  answer: string;
}

export const FAQS: readonly Faq[] = [
  {
    question: "How is the price worked out?",
    answer:
      "From the zones the two districts are in, the weight or size of the parcel, the road distance and the speed you pick, plus fuel and tax. Every part is shown on its own line, and the quote is held for 15 minutes.",
  },
  {
    question: "Where do you deliver?",
    answer:
      "To all 25 districts in the nine provinces of Sri Lanka. A few hard-to-reach areas carry a small extra charge, which the quote shows before you book.",
  },
  {
    question: "How do I track a parcel?",
    answer:
      "Enter the tracking number on the Track page; no account is needed. Signed in, you also see the live timeline and the proof-of-delivery photo.",
  },
  {
    question: "Can the recipient pay cash on delivery?",
    answer:
      "Yes. Add the amount to collect when you book; the courier collects it at the door and the cash-on-delivery fee appears in your quote.",
  },
  {
    question: "What is same-day delivery?",
    answer:
      "A bike or a van takes the parcel straight there on the same day, priced on the distance. Each vehicle has a weight and distance limit, and orders close at the day's cut-off time.",
  },
  {
    question: "How does my online store connect?",
    answer:
      "Register your business. Once approved you get API credentials for creating orders and can add webhook endpoints that receive a signed message at every status change.",
  },
];

export interface FooterColumn {
  title: string;
  links: readonly { href: string; label: string }[];
}

export const FOOTER_COLUMNS: readonly FooterColumn[] = [
  {
    title: "Send",
    links: [
      { href: "/quote", label: "Get a price" },
      { href: "/login?callbackUrl=%2Fcustomer%2Fbook", label: "Book a delivery" },
      { href: "/track", label: "Track a parcel" },
      { href: "/customer/history", label: "History & receipts" },
    ],
  },
  {
    title: "Business",
    links: [
      { href: "/merchant/register", label: "Register your store" },
      { href: "/merchant", label: "Merchant portal" },
      { href: "/#stores", label: "API & webhooks" },
      { href: "/#pricing", label: "Plans" },
    ],
  },
  {
    title: "Help",
    links: [
      { href: "/#help", label: "Questions & answers" },
      { href: "/privacy", label: "Privacy policy" },
      { href: "/terms", label: "Terms of service" },
      { href: "mailto:hello@swiftdrop.lk", label: "hello@swiftdrop.lk" },
      { href: "/login", label: "Sign in" },
      { href: "/account", label: "Your account" },
    ],
  },
];
