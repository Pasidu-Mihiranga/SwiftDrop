# SwiftDrop UI screenshots

Every screen of the web app and the supporting tools, taken on 24 Nov 2026 from the local compose stack (`dev` after #130) with Chrome
at 1440 px wide (courier screens at 390 px, a phone). Signed in through Keycloak as each seeded demo user; all data shown is demo data.

Regenerate after UI changes: start the stack, then run the Playwright script described in `doc/08-missing-work.md`.

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
