# Release legal configuration

Release builds are intentionally blocked until the real service-provider details and
the public privacy-policy URL are supplied. Do not commit private signing material or
store passwords with these values.

Configure the following Gradle properties in the release CI environment or in the
developer machine's Gradle user properties:

```properties
LEGAL_PROVIDER_NAME=Full legal name or registered company
LEGAL_PROVIDER_ADDRESS=Complete service address, country
LEGAL_PROVIDER_EMAIL=Monitored support and privacy email address
PRIVACY_POLICY_URL=https://example.com/privacy
MAP_TILE_URL=https://your-approved-osm-tile-provider.example/
```

The same identity must be used consistently in:

- the in-app Legal notice and Privacy policy;
- the public privacy-policy page;
- Google Play's developer profile and store listing;
- the Google Play Data safety form;
- invoices, tax details, and consumer contacts where applicable.

Before publication, determine whether the provider must additionally state a legal
representative, trade-register entry, VAT ID, competent supervisory authority, or
professional-law information. These fields cannot be inferred from the source code
and must not be invented.

The AdMob account must contain a published privacy message for the app. The release
implementation refreshes UMP consent on every app launch, blocks all ad requests
until `canRequestAds()` is true, and exposes the UMP privacy-options form whenever
UMP says it is required.

`MAP_TILE_URL` must be a provider-approved HTTPS base URL compatible with osmdroid's
`/{z}/{x}/{y}.png` requests. For a worldwide commercial release, do not assume that
the donation-funded public OSMF tile endpoint is a guaranteed production service.
Keep the visible OpenStreetMap attribution when the selected tiles use OSM data.

For a worldwide release, obtain a market-specific legal review. The bundled texts
cover the app's observed technical behavior and core EU/German issues; they do not
create automatic compliance with every national consumer, privacy, tax, advertising,
product-liability, or motorcycle-use rule.
