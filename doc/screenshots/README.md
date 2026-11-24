# SwiftDrop UI screenshots

Every screen of the web app and the supporting tools after the brand redesign, taken on 24 Nov 2026 from the local
compose stack with Chrome at 1440 px wide (courier screens at 390 px, a phone). Signed in through Keycloak as each
seeded demo user; all data shown is demo data. The landing page's testimonials are sample text, labelled on the page.

## Landing page

### Desktop (1440 px), full page

![Landing page, desktop](00-landing-desktop.png)

### Phone (390 px), full page

![Landing page, phone](00-landing-phone.png)

## Public pages

### Home

`/`

![Home](01-public-home.png)

### Public quote calculator

`/quote`

![Public quote calculator](02-public-quote.png)

### Track a parcel

`/track`

![Track a parcel](03-public-track.png)

### Sign in

`/login`

![Sign in](04-public-login.png)

### Forbidden page

`/forbidden`

![Forbidden page](06-public-forbidden.png)

### Quote with fee breakdown

`/quote`

![Quote with fee breakdown](07-public-quote-result.png)

### Keycloak login (swiftdrop theme)

`/realms/swiftdrop/protocol/openid-connect/auth`

![Keycloak login (swiftdrop theme)](08-keycloak-login.png)

### Public tracking result

`/track/SD261123D152P5F`

![Public tracking result](09-public-track-result.png)

## Customer

### Customer home

`/customer`

![Customer home](10-customer-home.png)

### Book a delivery (wizard)

`/customer/book`

![Book a delivery (wizard)](11-customer-book.png)

### Delivery history

`/customer/history`

![Delivery history](12-customer-history.png)

### Notification preferences

`/customer/notifications/preferences`

![Notification preferences](13-customer-notification-preferences.png)

### Account

`/account`

![Account](14-customer-account.png)

### Parcel detail with live timeline

`/customer/parcels/68569b95-fdf7-49fb-862c-8c2604c57c70`

![Parcel detail with live timeline](15-customer-parcel-detail.png)

### Become a merchant (registration form)

`/merchant/register`

![Become a merchant (registration form)](16-customer-merchant-register.png)

## Merchant

### Merchant dashboard

`/merchant`

![Merchant dashboard](20-merchant-home.png)

### Merchant orders

`/merchant/orders`

![Merchant orders](21-merchant-orders.png)

### API credentials

`/merchant/credentials`

![API credentials](22-merchant-credentials.png)

### Webhook endpoints

`/merchant/webhooks`

![Webhook endpoints](23-merchant-webhooks.png)

## Courier (phone, 390 px)

### Courier run sheet (phone)

`/courier`

![Courier run sheet (phone)](30-courier-run-sheet.png)

### Courier parcel: status stepper and proof (phone)

`/courier/parcels/b0a6a461-2489-4c58-a177-b2665dad58bd`

![Courier parcel: status stepper and proof (phone)](31-courier-parcel.png)

### Phone header menu (open)

`/courier`

![Phone header menu (open)](32-courier-phone-menu.png)

## Dispatcher

### Dispatch & workload board

`/admin/dispatch`

![Dispatch & workload board](40-dispatcher-dispatch-board.png)

### Couriers

`/admin/couriers`

![Couriers](41-dispatcher-couriers.png)

### Reports

`/admin/reports`

![Reports](42-dispatcher-reports.png)

## Admin

### Operations overview

`/admin`

![Operations overview](50-admin-overview.png)

### Users

`/admin/users`

![Users](51-admin-users.png)

### Merchant review

`/admin/merchants`

![Merchant review](52-admin-merchants.png)

### Zones & districts

`/admin/pricing/zones`

![Zones & districts](53-admin-pricing-zones.png)

### Rate cards

`/admin/pricing/rate-cards`

![Rate cards](54-admin-pricing-rate-cards.png)

### Remote areas

`/admin/pricing/remote-areas`

![Remote areas](55-admin-pricing-remote-areas.png)

### Fees & handling (new)

`/admin/pricing/settings`

![Fees & handling (new)](56-admin-pricing-settings.png)

### Merchant rates

`/admin/pricing/merchants`

![Merchant rates](57-admin-pricing-merchants.png)

### Webhooks dead-letter queue

`/admin/webhooks`

![Webhooks dead-letter queue](58-admin-webhooks-dlq.png)

### SMS inbox

`/admin/notifications/sms-inbox`

![SMS inbox](59-admin-sms-inbox.png)

### Reports

`/admin/reports`

![Reports](60-admin-reports.png)

### Dispatch (admin)

`/admin/dispatch`

![Dispatch (admin)](61-admin-dispatch.png)

## Other tools

### Merchant simulator (M8)

`/`

![Merchant simulator (M8)](70-merchant-simulator.png)

### Mailpit inbox (sent emails)

`/`

![Mailpit inbox (sent emails)](71-mailpit.png)

## Grafana dashboards

### Grafana: SwiftDrop - Bookings, courier ops and webhooks

`/d/swiftdrop-parcels-webhooks/swiftdrop-bookings-courier-ops-and-webhooks`

![Grafana: SwiftDrop - Bookings, courier ops and webhooks](80-grafana-parcels-webhooks.png)

### Grafana: SwiftDrop - Dispatch consumer lag

`/d/swiftdrop-dispatch/swiftdrop-dispatch-consumer-lag`

![Grafana: SwiftDrop - Dispatch consumer lag](81-grafana-dispatch.png)

### Grafana: SwiftDrop - Tracking Service

`/d/swiftdrop-tracking/swiftdrop-tracking-service`

![Grafana: SwiftDrop - Tracking Service](82-grafana-tracking.png)

### Grafana: SwiftDrop – Identity & Access (M1)

`/d/swiftdrop-identity/swiftdrop-e28093-identity-and-access-m1`

![Grafana: SwiftDrop – Identity & Access (M1)](83-grafana-identity.png)

### Grafana: SwiftDrop – Merchants & Merchant API (M7)

`/d/swiftdrop-merchants/swiftdrop-e28093-merchants-and-merchant-api-m7`

![Grafana: SwiftDrop – Merchants & Merchant API (M7)](84-grafana-merchants.png)

### Grafana: SwiftDrop – Notifications & Business KPIs (M9)

`/d/swiftdrop-m9-observability/dac72bd`

![Grafana: SwiftDrop – Notifications & Business KPIs (M9)](85-grafana-m9-observability.png)

### Grafana: SwiftDrop – Platform Overview

`/d/swiftdrop-platform/swiftdrop-e28093-platform-overview`

![Grafana: SwiftDrop – Platform Overview](86-grafana-platform.png)

### Grafana: SwiftDrop – Pricing & Zones (M2)

`/d/swiftdrop-pricing/swiftdrop-e28093-pricing-and-zones-m2`

![Grafana: SwiftDrop – Pricing & Zones (M2)](87-grafana-pricing.png)

### Grafana: SwiftDrop – Reporting & AI Assistant (M10)

`/d/swiftdrop-reporting-assistant/swiftdrop-e28093-reporting-and-ai-assistant-m10`

![Grafana: SwiftDrop – Reporting & AI Assistant (M10)](88-grafana-reporting-assistant.png)

## Colour contrast

Measured for every text pairing in the theme (`apps/web/src/app/globals.css`), with both instruments kept separate:
WCAG 2 contrast ratio (AA: 4.5:1 text, 3:1 non-text) and APCA Lc (75 body text, 60 small or large content text, 15
non-text). An APCA result is not a WCAG 2 conformance claim.

```
ok   body/headings on page                        #000000 on #fdf1f3  WCAG 19.05:1 (min 4.5)  APCA Lc  99.3 (min 75)
ok   body on alternate ground                     #000000 on #f4e8ea  WCAG 17.58:1 (min 4.5)  APCA Lc  94.1 (min 75)
ok   secondary text (16px)                        #373737 on #fdf1f3  WCAG 10.80:1 (min 4.5)  APCA Lc  90.6 (min 75)
ok   secondary text on alt                        #373737 on #f4e8ea  WCAG  9.96:1 (min 4.5)  APCA Lc  85.3 (min 75)
ok   slate-600 text on cards                      #423a3c on #ffffff  WCAG 11.04:1 (min 4.5)  APCA Lc  95.6 (min 75)
ok   slate-600 on page                            #423a3c on #fdf1f3  WCAG 10.02:1 (min 4.5)  APCA Lc  88.9 (min 75)
ok   slate-500 small text on cards                #534a4c on #ffffff  WCAG  8.57:1 (min 4.5)  APCA Lc  89.5 (min 60)
ok   slate-500 on page                            #534a4c on #fdf1f3  WCAG  7.77:1 (min 4.5)  APCA Lc  82.8 (min 60)
ok   slate-500 on alt                             #534a4c on #f4e8ea  WCAG  7.17:1 (min 4.5)  APCA Lc  77.5 (min 60)
ok   slate-400 icons/placeholder (non-text 3:1)   #8f8285 on #ffffff  WCAG  3.68:1 (min 3.0)  APCA Lc  64.4 (min 30)
ok   accent links on page                         #be185d on #fdf1f3  WCAG  5.48:1 (min 4.5)  APCA Lc  71.6 (min 60)
ok   brand-700 links on page                      #9d174d on #fdf1f3  WCAG  7.15:1 (min 4.5)  APCA Lc  79.0 (min 75)
ok   brand-700 text on cards                      #9d174d on #ffffff  WCAG  7.88:1 (min 4.5)  APCA Lc  85.7 (min 75)
ok   active nav / notice-info                     #9d174d on #f9dfea  WCAG  6.30:1 (min 4.5)  APCA Lc  70.8 (min 60)
ok   button label on accent                       #fcfcfd on #be185d  WCAG  5.89:1 (min 4.5)  APCA Lc  81.6 (min 60)
ok   button label on hover                        #fcfcfd on #9d174d  WCAG  7.69:1 (min 4.5)  APCA Lc  88.6 (min 60)
ok   text on dark banner                          #fcfcfd on #141314  WCAG 18.08:1 (min 4.5)  APCA Lc 105.3 (min 75)
ok   muted text on dark banner                    #c9bcbf on #141314  WCAG 10.09:1 (min 4.5)  APCA Lc  67.4 (min 60)
ok   accent on dark (button fill edge, non-text)  #be185d on #141314  WCAG  3.07:1 (min 3.0)  APCA Lc  22.8 (min 15)
ok   feature eyebrow (14px semibold) on alt       #9d174d on #f4e8ea  WCAG  6.60:1 (min 4.5)  APCA Lc  73.7 (min 60)
ok   accent text on alt ground                    #be185d on #f4e8ea  WCAG  5.05:1 (min 4.5)  APCA Lc  66.3 (min 60)
ok   plan note / captions (14px) on page          #534a4c on #fdf1f3  WCAG  7.77:1 (min 4.5)  APCA Lc  82.8 (min 60)
ok   recommended plan text on white               #373737 on #ffffff  WCAG 11.90:1 (min 4.5)  APCA Lc  97.3 (min 75)
ok   Recommended badge (12px semibold)            #fcfcfd on #be185d  WCAG  5.89:1 (min 4.5)  APCA Lc  81.6 (min 60)
```
