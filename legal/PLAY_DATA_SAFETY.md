# Google Play Data safety working record

This file is a release worksheet, not a substitute for reviewing the current Google
Play form and every SDK provider's current disclosure.

## Observed app behavior

| Data or transfer | Observed behavior |
|---|---|
| Precise location | Used to record rides, speed, distance, and routes. Ride data is stored locally. |
| Motion/sensor data | Used locally to estimate lean and calibrate the device. |
| Ride names and statistics | Stored locally in Room until the user deletes them or clears/uninstalls the app. |
| Android backup | Disabled; database, shared preferences, app files, and device transfer are excluded. |
| GPX import | Read only after the user selects a file. |
| GPX export/share | Sent only after an explicit user action to the selected receiving app. |
| OpenStreetMap | When a map is shown, IP address, user agent, request time, and requested tile coordinates are sent to the OSMF tile service. |
| Google Mobile Ads | No request is made until UMP reports `canRequestAds()`. Data categories depend on consent, AdMob configuration, and Google's current SDK disclosure. |
| Google Play Billing | Product, purchase, account, and entitlement-related data is exchanged with Google Play; full card data is not delivered to the app. |
| Developer server/account | None found in the current app. |

## Play Console checks

- Link the public URL configured as `PRIVACY_POLICY_URL`.
- Declare third-party SDK collection and sharing, not only code written by the app
  developer.
- Review current disclosures in the Google Play SDK Index for:
  - Google Mobile Ads;
  - Google User Messaging Platform;
  - Google Play Billing.
- Recheck whether device or advertising IDs, approximate location, app interactions,
  diagnostics, crash data, and purchase history must be declared for the exact AdMob
  and Play Console configuration.
- Declare precise location correctly: local processing is distinct from collection,
  but OSM requests expose the displayed geographic area and AdMob behavior must be
  reviewed separately.
- Confirm that the target audience is adult and that the app is not child-directed.
- Re-run this audit after every dependency, analytics, crash-reporting, advertising,
  map-provider, cloud, account, or backup change.
