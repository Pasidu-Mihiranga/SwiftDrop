import pytest

from app.tools.places import DISTRICTS, TOWNS, DistrictResolver

SERVED = ["Colombo", "Gampaha", "Kandy", "Nuwara Eliya", "Monaragala"]


@pytest.mark.parametrize("place, district", [
    ("Kandy", "Kandy"),
    ("  KANDY district ", "Kandy"),
    ("Colombo 03", "Colombo"),
    ("Nugegoda", "Colombo"),
    ("mt. lavinia", "Colombo"),
    ("Negombo", "Gampaha"),
    ("Peradeniya", "Kandy"),
    ("nuwara-eliya", "Nuwara Eliya"),
    ("Kandi", "Kandy"),
    ("Moneragala", "Monaragala"),
    ("Kataragama", "Monaragala"),
])
def test_resolves_districts_towns_and_close_spellings(place, district):
    assert DistrictResolver(SERVED).resolve(place) == district


@pytest.mark.parametrize("place", ["", "  ", "123", "Atlantis", "London"])
def test_unknown_places_are_not_guessed(place):
    assert DistrictResolver(SERVED).resolve(place) is None


def test_a_known_town_outside_the_served_districts_is_reported_as_such():
    resolver = DistrictResolver(SERVED)
    assert resolver.resolve("Hikkaduwa") == "Galle"
    assert not resolver.is_served("Galle") and resolver.is_served("kandy")


def test_without_a_zone_list_every_district_is_a_candidate():
    resolver = DistrictResolver(None)
    assert not resolver.known_list
    assert resolver.resolve("Jaffna") == "Jaffna" and resolver.is_served("Jaffna")


def test_every_town_maps_to_one_of_the_25_districts():
    assert len(DISTRICTS) == 25
    assert set(TOWNS.values()) <= set(DISTRICTS)
