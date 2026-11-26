import type { Metadata } from "next";
import { LegalDocument, LegalSection } from "@/components/legal-document";

export const metadata: Metadata = {
  title: "Privacy policy",
  description: "How SwiftDrop collects, uses, and shares information when you send or track a parcel.",
};

export default function PrivacyPage() {
  return (
    <LegalDocument title="Privacy policy">
      <LegalSection title="Who we are">
        <p>
          SwiftDrop is a parcel delivery service for Sri Lanka. This policy covers the site at
          https://swiftdrop.sapunipathirana.me. Questions about your information go to hello@swiftdrop.lk.
        </p>
      </LegalSection>
      <LegalSection title="Information you give us">
        <p>When you create an account or book a delivery, we store:</p>
        <ul className="list-disc space-y-1 pl-5">
          <li>your name and email address</li>
          <li>a password, if you sign in with email</li>
          <li>phone numbers and pickup and delivery addresses for a parcel</li>
          <li>the parcel details you enter, such as size, weight, and what is inside</li>
        </ul>
      </LegalSection>
      <LegalSection title="Google sign-in">
        <p>
          If you choose Continue with Google, Google shares your name, email address, and profile
          picture with SwiftDrop. We use those only to create your account and to sign you in.
          We do not sell them, and we do not use them for advertising.
        </p>
      </LegalSection>
      <LegalSection title="Information collected while you use the site">
        <p>
          A sign-in cookie keeps you signed in. The servers also keep a short log of requests,
          such as the time and the page, so we can run the service and investigate faults.
        </p>
      </LegalSection>
      <LegalSection title="How we use it">
        <p>We use this information to:</p>
        <ul className="list-disc space-y-1 pl-5">
          <li>create your account and recognise you when you return</li>
          <li>price, collect, and deliver a parcel</li>
          <li>show tracking to you and to the courier assigned to that parcel</li>
          <li>send delivery messages you have asked for</li>
        </ul>
      </LegalSection>
      <LegalSection title="Who can see it">
        <p>
          You can see your own account and parcels. A dispatcher and the courier assigned to a
          parcel can see the name, phone number, and address they need to collect and deliver it.
          We do not sell personal information.
        </p>
      </LegalSection>
      <LegalSection title="How long we keep it">
        <p>
          We keep your account and parcel records while the account is open, and for as long as
          we need them to finish a delivery and keep a receipt. Email hello@swiftdrop.lk if you
          want the account deleted.
        </p>
      </LegalSection>
      <LegalSection title="Contact">
        <p>Email hello@swiftdrop.lk. SwiftDrop, Colombo, Sri Lanka.</p>
      </LegalSection>
    </LegalDocument>
  );
}
