import type { Metadata } from "next";
import { LegalDocument, LegalSection } from "@/components/legal-document";

export const metadata: Metadata = {
  title: "Terms of service",
  description: "The rules for using SwiftDrop to price, book, and track a parcel in Sri Lanka.",
};

export default function TermsPage() {
  return (
    <LegalDocument title="Terms of service">
      <LegalSection title="The service">
        <p>
          SwiftDrop lets you check a delivery price, book a parcel, and follow it in Sri Lanka.
          These terms cover the site at https://swiftdrop.sapunipathirana.me.
        </p>
      </LegalSection>
      <LegalSection title="Your account">
        <p>
          Booking a parcel needs an account. You can sign in with email and a password, or with
          Google. You are responsible for what happens on your account, and for keeping the
          password private.
        </p>
      </LegalSection>
      <LegalSection title="Prices">
        <p>
          A price shown before you book is an estimate. The price confirmed when you book is the
          price you pay for that delivery.
        </p>
      </LegalSection>
      <LegalSection title="What you may send">
        <p>
          You will give a correct address and a way to reach the receiver. You will not use
          SwiftDrop to send anything illegal, dangerous, or prohibited.
        </p>
      </LegalSection>
      <LegalSection title="Google sign-in">
        <p>
          Continue with Google is provided by Google. Google’s own terms apply to your Google
          account. SwiftDrop uses the name, email address, and profile picture Google shares
          only to create your account and sign you in.
        </p>
      </LegalSection>
      <LegalSection title="Stopping">
        <p>
          You may stop using SwiftDrop at any time and ask us to close the account. We may
          suspend an account that breaks these terms.
        </p>
      </LegalSection>
      <LegalSection title="Contact">
        <p>Email hello@swiftdrop.lk. SwiftDrop, Colombo, Sri Lanka.</p>
      </LegalSection>
    </LegalDocument>
  );
}
