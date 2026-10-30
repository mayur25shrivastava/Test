import pytest

from app import catalog, planner


@pytest.mark.parametrize("prompt,dest,days,budget", [
    ("4 days in Goa under ₹30k, beaches and food", "Goa", 4, 30000),
    ("Plan a weekend in Jaipur with budget 20,000", "Jaipur", 2, 20000),
    ("a week in Kerala within Rs 1.5 lakh", "Kerala", 7, 150000),
    ("trip to Manali", "Manali", 3, 30000),
    ("5 night stay in New Delhi under 45k", "New Delhi", 5, 45000),
])
def test_parse_regex(prompt, dest, days, budget):
    r = planner.parse_regex(prompt)
    assert (r["destination"], r["days"], r["budget"]) == (dest, days, budget)


def test_parse_without_destination():
    assert planner.parse_regex("something relaxing please")["destination"] is None


def test_sanitize_rejects_junk():
    assert planner.sanitize_request({"destination": "<script>", "days": 3, "budget": 100}) is None
    assert planner.sanitize_request({"destination": "Goa", "days": 99, "budget": 5000})["days"] == planner.MAX_DAYS


def test_catalog_is_deterministic():
    assert catalog.search("Goa") == catalog.search("goa")


def test_cheapest_draft_cost_and_days():
    req = {"destination": "Goa", "days": 4, "budget": 30000}
    opts = catalog.search("Goa")
    draft = planner.cheapest_draft(opts, req)
    assert len(draft["days"]) == 4
    f, h, c = opts["flights"][0], opts["hotels"][0], opts["cabs"][0]
    assert planner.total_cost(opts, draft, req) == f["price"] + h["price"] * 3 + c["price"]


def test_parse_llm_json_accepts_fenced_json_and_pads_days():
    req = {"destination": "Goa", "days": 3, "budget": 30000}
    opts = catalog.search("Goa")
    text = '```json\n{"flight_id":"FL-1","hotel_id":"HT-2","cab_id":"CB-1","summary":"ok","days":[{"day":1,"title":"Arrive","activities":["Beach"]}]}\n```'
    draft = planner.parse_llm_json(text, opts, req)
    assert len(draft["days"]) == 3 and draft["days"][0]["activities"] == ["Beach"]


def test_parse_llm_json_rejects_unknown_ids():
    req = {"destination": "Goa", "days": 3, "budget": 30000}
    with pytest.raises(ValueError):
        planner.parse_llm_json('{"flight_id":"FL-99","hotel_id":"HT-1","cab_id":"CB-1"}', catalog.search("Goa"), req)
