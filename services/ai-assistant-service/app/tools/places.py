"""City to district resolution for fee estimates.

pricing-service prices between districts, but people name towns ("Nugegoda", "Peradeniya"). A town is
resolved in this order:

1. a district name from pricing-service's live zone list (what SwiftDrop serves today);
2. a known town, from the table below;
3. a close spelling of either ("Kandi", "Moneragala"), when exactly one candidate is close.

The table only maps a town to its administrative district; whether that district is served, and in
which zone, always comes from pricing-service.
"""

import re
from difflib import get_close_matches

# The 25 administrative districts. Used when the zone list cannot be loaded, so a quote can still be
# attempted: pricing-service then has the last word (DISTRICT_NOT_SERVICED).
DISTRICTS = (
    "Ampara", "Anuradhapura", "Badulla", "Batticaloa", "Colombo", "Galle", "Gampaha", "Hambantota", "Jaffna",
    "Kalutara", "Kandy", "Kegalle", "Kilinochchi", "Kurunegala", "Mannar", "Matale", "Matara", "Monaragala",
    "Mullaitivu", "Nuwara Eliya", "Polonnaruwa", "Puttalam", "Ratnapura", "Trincomalee", "Vavuniya",
)

_TOWNS_BY_DISTRICT: dict[str, tuple[str, ...]] = {
    "Colombo": ("Dehiwala", "Mount Lavinia", "Mt Lavinia", "Moratuwa", "Sri Jayawardenepura Kotte", "Kotte",
                "Nugegoda", "Maharagama", "Kesbewa", "Piliyandala", "Homagama", "Kaduwela", "Battaramulla",
                "Malabe", "Avissawella", "Kolonnawa", "Wellawatte", "Bambalapitiya", "Kollupitiya", "Borella",
                "Rajagiriya", "Pannipitiya", "Padukka", "Ratmalana", "Boralesgamuwa", "Fort", "Pettah"),
    "Gampaha": ("Negombo", "Ja-Ela", "Ja Ela", "Wattala", "Kelaniya", "Ragama", "Kadawatha", "Minuwangoda",
                "Katunayake", "Kiribathgoda", "Nittambuwa", "Veyangoda", "Divulapitiya", "Mirigama",
                "Ganemulla", "Kandana", "Seeduwa", "Peliyagoda", "Biyagama"),
    "Kalutara": ("Panadura", "Horana", "Beruwala", "Aluthgama", "Bandaragama", "Matugama", "Wadduwa"),
    "Kandy": ("Peradeniya", "Katugastota", "Gampola", "Nawalapitiya", "Kundasale", "Digana", "Akurana",
              "Pilimathalawa", "Kadugannawa", "Teldeniya"),
    "Matale": ("Dambulla", "Sigiriya", "Galewela", "Ukuwela", "Rattota", "Naula"),
    "Nuwara Eliya": ("Hatton", "Talawakele", "Nanu Oya", "Ragala", "Maskeliya", "Walapane"),
    "Galle": ("Hikkaduwa", "Ambalangoda", "Unawatuna", "Elpitiya", "Baddegama", "Karapitiya", "Bentota",
              "Koggala", "Ahangama"),
    "Matara": ("Weligama", "Mirissa", "Dikwella", "Akuressa", "Kamburupitiya", "Deniyaya", "Hakmana"),
    "Hambantota": ("Tangalle", "Tissamaharama", "Ambalantota", "Beliatta", "Weeraketiya", "Sooriyawewa"),
    "Jaffna": ("Point Pedro", "Chavakachcheri", "Nallur", "Kankesanthurai", "Valvettithurai"),
    "Batticaloa": ("Kattankudy", "Eravur", "Valaichchenai", "Batti"),
    "Ampara": ("Kalmunai", "Akkaraipattu", "Sammanthurai", "Pottuvil", "Arugam Bay", "Dehiattakandiya"),
    "Trincomalee": ("Trinco", "Kinniya", "Kantale", "Nilaveli", "Mutur"),
    "Kurunegala": ("Kuliyapitiya", "Pannala", "Narammala", "Polgahawela", "Wariyapola", "Mawathagama",
                   "Ibbagamuwa", "Nikaweratiya", "Alawwa", "Giriulla"),
    "Puttalam": ("Chilaw", "Wennappuwa", "Marawila", "Nattandiya", "Kalpitiya", "Anamaduwa", "Dankotuwa"),
    "Anuradhapura": ("Kekirawa", "Medawachchiya", "Mihintale", "Eppawala", "Thambuttegama", "Habarana"),
    "Polonnaruwa": ("Kaduruwela", "Hingurakgoda", "Medirigiriya", "Minneriya"),
    "Badulla": ("Bandarawela", "Ella", "Haputale", "Welimada", "Mahiyanganaya", "Hali-Ela", "Hali Ela", "Passara"),
    "Monaragala": ("Moneragala", "Wellawaya", "Buttala", "Bibile", "Kataragama"),
    "Ratnapura": ("Embilipitiya", "Balangoda", "Pelmadulla", "Eheliyagoda", "Kuruwita", "Kahawatta"),
    "Kegalle": ("Mawanella", "Warakapola", "Rambukkana", "Ruwanwella", "Pinnawala", "Yatiyantota", "Dehiowita"),
}


def _key(name: str) -> str:
    """"Colombo 03", "colombo district", " KANDY " -> "colombo" / "kandy"."""
    name = re.sub(r"[^a-z ]", " ", name.lower())
    name = re.sub(r"\b(district|city|town)\b", " ", name)
    return " ".join(name.split())


TOWNS: dict[str, str] = {_key(town): district for district, towns in _TOWNS_BY_DISTRICT.items() for town in towns}


class DistrictResolver:
    """Resolves a place name against the districts SwiftDrop serves (from pricing-service's zones)."""

    def __init__(self, served_districts: list[str] | None) -> None:
        # None: the zone list could not be loaded, so every district is a candidate and pricing decides.
        self.served = {_key(d): d for d in (served_districts if served_districts is not None else DISTRICTS)}
        self.known_list = served_districts is not None

    def resolve(self, place: str) -> str | None:
        key = _key(place)
        if not key:
            return None
        if key in self.served:
            return self.served[key]
        district = TOWNS.get(key) or self._close(key)
        if district is None:
            return None
        # A known town whose district is not in the zone list: return it anyway, and let the caller
        # tell "not served" apart from "never heard of it".
        return self.served.get(_key(district), district)

    def is_served(self, district: str) -> bool:
        return _key(district) in self.served

    def _close(self, key: str) -> str | None:
        candidates = {**TOWNS, **self.served}
        matches = get_close_matches(key, candidates, n=2, cutoff=0.8)
        if not matches:
            return None
        districts = {candidates[m] for m in matches}
        return districts.pop() if len(districts) == 1 else None
