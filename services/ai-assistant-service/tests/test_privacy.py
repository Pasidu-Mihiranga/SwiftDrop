from app.privacy import PiiKind, PiiRedactor, PseudonymVault, StreamingRestorer, VaultCipher
from app.privacy.detectors import find_in_text, kind_for_field, luhn_valid
from app.privacy.redactor import mask_name


def kinds(text: str) -> list[tuple[str, str]]:
    return [(f.kind.value, f.value) for f in find_in_text(text)]


# ---- detectors ----------------------------------------------------------------------------------

def test_finds_sri_lankan_and_international_phone_numbers():
    assert kinds("call 0771234567 or 077 123 4567 or +94 77 123 4567") == [
        ("PHONE", "0771234567"), ("PHONE", "077 123 4567"), ("PHONE", "+94 77 123 4567")]


def test_finds_both_nic_formats():
    assert kinds("old 912345678V new 199123456789") == [("NIC", "912345678V"), ("NIC", "199123456789")]


def test_cards_must_pass_the_luhn_check():
    assert luhn_valid("4111 1111 1111 1111")
    assert not luhn_valid("4111 1111 1111 1112")
    assert kinds("card 4111 1111 1111 1111") == [("CARD", "4111 1111 1111 1111")]
    assert kinds("order 4111111111111112") == []


def test_emails_and_tracking_numbers():
    assert kinds("mail kamal.perera@example.lk") == [("EMAIL", "kamal.perera@example.lk")]
    assert kinds("Where is SD261020K7Q2M9X and SD2610201234567?") == []


def test_field_names_mark_personal_data_but_not_zone_names():
    assert kind_for_field(("recipient", "name")) is PiiKind.NAME
    assert kind_for_field(("recipientPhone",)) is PiiKind.PHONE
    assert kind_for_field(("dropoff", "line1")) is PiiKind.ADDRESS
    assert kind_for_field(("dropoff", "lat")) is PiiKind.LOCATION
    assert kind_for_field(("zones", "name")) is None
    assert kind_for_field(("dropoff", "city")) is None
    assert kind_for_field(()) is None


# ---- vault -------------------------------------------------------------------------------------

def test_the_same_value_keeps_its_placeholder():
    vault = PseudonymVault()
    assert vault.token_for("+94 77 123 4567", PiiKind.PHONE) == "[PHONE_1]"
    assert vault.token_for("+94771234567", PiiKind.PHONE) == "[PHONE_1]"
    assert vault.token_for("0711111111", PiiKind.PHONE) == "[PHONE_2]"
    assert vault.token_for("Kamal Perera", PiiKind.NAME) == "[NAME_1]"
    assert vault.value_of("[PHONE_1]") == "+94 77 123 4567"
    assert vault.value_of("[PHONE_9]") is None
    assert len(vault) == 3


def test_vault_round_trips_encrypted_and_keeps_counting():
    cipher = VaultCipher("a-test-secret")
    vault = PseudonymVault()
    vault.token_for("Kamal Perera", PiiKind.NAME)
    blob = cipher.encrypt(vault)
    assert b"Kamal" not in blob
    restored = cipher.decrypt(blob)
    assert restored.value_of("[NAME_1]") == "Kamal Perera"
    assert restored.token_for("Nimal Silva", PiiKind.NAME) == "[NAME_2]"


def test_unreadable_vaults_are_discarded():
    blob = VaultCipher("one-secret").encrypt(PseudonymVault({"[NAME_1]": ["NAME", "Kamal"]}))
    assert len(VaultCipher("another-secret").decrypt(blob)) == 0
    assert len(VaultCipher("one-secret").decrypt(None)) == 0
    assert len(VaultCipher(None).decrypt(b"garbage")) == 0


# ---- redactor ----------------------------------------------------------------------------------

def test_text_is_pseudonymized_and_restored():
    redactor, vault = PiiRedactor(), PseudonymVault()
    text = "Call me on 0771234567 or mail me at kamal@example.lk about SD261020K7Q2M9X"
    pseudo = redactor.pseudonymize_text(text, vault)
    assert pseudo == "Call me on [PHONE_1] or mail me at [EMAIL_1] about SD261020K7Q2M9X"
    assert redactor.restore(pseudo, vault) == text
    assert redactor.restore("unknown [NAME_7]", vault) == "unknown [NAME_7]"


def test_known_values_are_replaced_even_without_a_pattern():
    redactor, vault = PiiRedactor(), PseudonymVault()
    vault.token_for("Kamal Perera", PiiKind.NAME)
    assert redactor.pseudonymize_text("Is kamal perera home?", vault) == "Is [NAME_1] home?"
    assert redactor.pseudonymize_text("already [NAME_1]", vault) == "already [NAME_1]"


def test_structured_results_are_pseudonymized_by_field():
    seen = []
    redactor, vault = PiiRedactor(seen.append), PseudonymVault()
    parcel = {
        "trackingNumber": "SD261020K7Q2M9X",
        "recipient": {"name": "Kamal Perera", "phone": "+94771234567", "email": None},
        "dropoff": {"line1": "12 Temple Road", "city": "Kandy", "lat": 7.29},
        "notes": ["ring 0711111111"],
        "weightKg": 2.0,
        "fragile": True,
    }
    out = redactor.pseudonymize_json(parcel, vault)
    assert out == {
        "trackingNumber": "SD261020K7Q2M9X",
        "recipient": {"name": "[NAME_1]", "phone": "[PHONE_1]", "email": None},
        "dropoff": {"line1": "[ADDRESS_1]", "city": "Kandy", "lat": "[LOCATION_1]"},
        "notes": ["ring [PHONE_2]"],
        "weightKg": 2.0,
        "fragile": True,
    }
    assert redactor.restore_json({"q": ["[NAME_1]", 3]}, vault) == {"q": ["Kamal Perera", 3]}
    assert PiiKind.NAME in seen and PiiKind.PHONE in seen


def test_mcp_results_are_masked_irreversibly():
    redactor = PiiRedactor()
    masked = redactor.mask_json({
        "recipient": {"name": "Kamal Perera", "phone": "+94771234567", "email": "kamal@example.lk"},
        "dropoff": {"line1": "12 Temple Road", "city": "Kandy", "lat": 7.29},
        "nic": "199123456789",
        "note": "card 4111 1111 1111 1111",
        "items": [{"recipientName": "Nimal"}],
        "count": 2,
    })
    assert masked == {
        "recipient": {"name": "K*** P.", "phone": "+94*******67", "email": "k***@example.lk"},
        "dropoff": {"line1": "***", "city": "Kandy", "lat": None},
        "nic": "***********9",
        "note": "card ************1111",
        "items": [{"recipientName": "N***"}],
        "count": 2,
    }


def test_masked_names_stay_masked():
    assert mask_name("K*** P.") == "K*** P."
    assert mask_name("") == ""


def test_streaming_restores_placeholders_split_across_chunks():
    vault = PseudonymVault()
    vault.token_for("+94771234567", PiiKind.PHONE)
    restorer = StreamingRestorer(lambda t: PiiRedactor().restore(t, vault))
    chunks = ["Your courier will call ", "[PHO", "NE_1] today. ", "See [", "list] and [NAME_", "4"]
    out = "".join(restorer.feed(c) for c in chunks) + restorer.flush()
    assert out == "Your courier will call +94771234567 today. See [list] and [NAME_4"
