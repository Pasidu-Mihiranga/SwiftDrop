# District boundaries

`sri-lanka-districts.geojson` holds simplified boundaries of Sri Lanka's 25 districts. It is
used only to answer "which district is this map point in?" (`DistrictLocator`), so a quote
never depends on a geocoder's address text.

- Source: geoBoundaries gbOpen, Sri Lanka ADM2 (simplified), derived from OpenStreetMap
  (Wambacher). https://www.geoboundaries.org
- Licence: Open Data Commons Open Database License (ODbL) 1.0. (c) OpenStreetMap contributors.
  https://www.openstreetmap.org/copyright
- Changes: feature names reduced to the district name used in `district_zone`; coordinates
  rounded to four decimal places (about 11 m).

Boundaries are approximate. A point within roughly a hundred metres of a border can resolve to
the neighbouring district; the quote then follows that district's zone.
