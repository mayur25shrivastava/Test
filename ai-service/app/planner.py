"""Pure planning logic (no network, no LLM): parsing, costing, prompts, validation, fallback plan."""
import json
import re

STOPWORDS = {
    "under", "below", "within", "with", "for", "budget", "from", "and", "on", "next", "this",
    "upto", "up", "max", "around", "about", "that", "where", "which", "i", "we", "my", "our",
}
MAX_DAYS = 14
DEFAULT_DAYS = 3
DEFAULT_BUDGET = 30000


def _clean_destination(raw: str | None) -> str | None:
    if not raw:
        return None
    words = []
    for w in raw.split():
        if w.lower() in STOPWORDS:
            break
        words.append(w)
    name = " ".join(words).strip(" .,'-")
    name = re.sub(r"[^A-Za-z .'-]", "", name)[:40].strip()
    return name or None


def parse_regex(prompt: str) -> dict:
    """Best-effort extraction of destination, days and budget. destination is None if not found."""
    text = prompt.strip()
    m = re.search(r"\b(?:in|to|at|visit(?:ing)?)\s+([A-Za-z][A-Za-z.'-]*(?:\s+[A-Za-z][A-Za-z.'-]*){0,2})", text, re.I)
    destination = _clean_destination(m.group(1)) if m else None

    days = DEFAULT_DAYS
    dm = re.search(r"(\d{1,2})\s*-?\s*(?:day|days|night|nights)\b", text, re.I)
    if dm:
        days = int(dm.group(1))
    elif re.search(r"\bweekend\b", text, re.I):
        days = 2
    elif re.search(r"\bweek\b", text, re.I):
        days = 7
    days = max(1, min(days, MAX_DAYS))

    budget = DEFAULT_BUDGET
    bm = re.search(
        r"(?:under|below|within|budget(?:\s+of)?|upto|up\s+to|max(?:imum)?)\s*(?:of\s*)?(?:₹|rs\.?|inr)?\s*([\d][\d,]*(?:\.\d+)?)\s*(k|l|lakh|lac)?",
        text, re.I)
    if not bm:
        bm = re.search(r"(?:₹|rs\.?|inr)\s*([\d][\d,]*(?:\.\d+)?)\s*(k|l|lakh|lac)?", text, re.I)
    if bm:
        value = float(bm.group(1).replace(",", ""))
        unit = (bm.group(2) or "").lower()
        if unit == "k":
            value *= 1_000
        elif unit in ("l", "lakh", "lac"):
            value *= 100_000
        budget = int(value)
    return {"destination": destination, "days": days, "budget": budget}


def sanitize_request(req: dict) -> dict | None:
    """Validate a request dict (e.g. produced by an LLM). Returns None if unusable."""
    raw = str(req.get("destination") or "")
    if re.search(r"[^A-Za-z .'-]", raw):   # reject, do not "repair", suspicious model output
        return None
    dest = _clean_destination(raw)
    try:
        days = max(1, min(int(req.get("days", DEFAULT_DAYS)), MAX_DAYS))
        budget = int(req.get("budget", DEFAULT_BUDGET))
    except (TypeError, ValueError):
        return None
    if not dest or budget <= 0:
        return None
    return {"destination": dest, "days": days, "budget": budget}


def nights(request: dict) -> int:
    return max(request["days"] - 1, 1)


def _by_id(items: list[dict], item_id: str) -> dict | None:
    return next((i for i in items if i["id"] == item_id), None)


def selection(options: dict, draft: dict, request: dict) -> dict:
    return {
        "flight": _by_id(options["flights"], draft["flight_id"]),
        "hotel": _by_id(options["hotels"], draft["hotel_id"]),
        "cab": _by_id(options["cabs"], draft["cab_id"]),
        "nights": nights(request),
    }


def total_cost(options: dict, draft: dict, request: dict) -> int:
    s = selection(options, draft, request)
    return s["flight"]["price"] + s["hotel"]["price"] * s["nights"] + s["cab"]["price"]


def fallback_days(request: dict) -> list[dict]:
    n = request["days"]
    dest = request["destination"]
    days = []
    for d in range(1, n + 1):
        if d == 1:
            title, acts = "Arrive and settle in", ["Airport pickup and hotel check-in", f"Evening walk around the main area of {dest}", "Local dinner"]
        elif d == n and n > 1:
            title, acts = "Last morning and departure", ["Relaxed breakfast and souvenir shopping", "Hotel check-out", "Transfer to the airport"]
        else:
            title, acts = f"Explore {dest}", ["Top sights in the morning", "Long local lunch", "Free time in the afternoon", "Sunset spot"]
        days.append({"day": d, "title": title, "activities": acts})
    return days


def cheapest_draft(options: dict, request: dict, summary: str = "") -> dict:
    """Deterministic plan: cheapest flight + cheapest hotel + cheapest cab."""
    cheapest = lambda items: min(items, key=lambda i: i["price"])["id"]
    return {
        "flight_id": cheapest(options["flights"]),
        "hotel_id": cheapest(options["hotels"]),
        "cab_id": cheapest(options["cabs"]),
        "summary": summary or f"A simple {request['days']}-day plan for {request['destination']} using the lowest-priced options.",
        "days": fallback_days(request),
    }


def build_prompt(request: dict, options: dict, feedback: str = "") -> tuple[str, str]:
    system = (
        "You are a careful travel planner. Reply with ONLY one JSON object, no prose, no markdown. "
        'Schema: {"flight_id": str, "hotel_id": str, "cab_id": str, "summary": str, '
        '"days": [{"day": int, "title": str, "activities": [str]}]}. '
        "Use only ids from the options given. Each day has 3 to 5 short activities."
    )

    def fmt(items, unit):
        return "\n".join(f'- {i["id"]}: {i["name"]} (Rs {i["price"]} {unit})' for i in items)

    user = (
        f"Destination: {request['destination']}\nDays: {request['days']} ({nights(request)} hotel nights)\n"
        f"Budget: Rs {request['budget']} total. Total = flight + hotel price x nights + cab.\n\n"
        f"Flights:\n{fmt(options['flights'], 'per trip')}\n\nHotels:\n{fmt(options['hotels'], 'per night')}\n\n"
        f"Cabs:\n{fmt(options['cabs'], 'for the whole trip')}\n"
    )
    if feedback:
        user += f"\nFix this from the previous attempt: {feedback}\n"
    return system, user


def parse_llm_json(text: str, options: dict, request: dict) -> dict:
    """Turn raw LLM text into a validated draft or raise ValueError."""
    cleaned = text.strip()
    cleaned = re.sub(r"^```(?:json)?\s*|\s*```$", "", cleaned, flags=re.I)
    start, end = cleaned.find("{"), cleaned.rfind("}")
    if start == -1 or end == -1:
        raise ValueError("no JSON object in response")
    data = json.loads(cleaned[start:end + 1])

    for key, group in (("flight_id", "flights"), ("hotel_id", "hotels"), ("cab_id", "cabs")):
        if _by_id(options[group], str(data.get(key))) is None:
            raise ValueError(f"unknown {key}: {data.get(key)!r}")

    base_days = fallback_days(request)
    days = []
    for idx in range(request["days"]):
        raw = data.get("days", [])[idx] if idx < len(data.get("days", [])) else None
        if isinstance(raw, dict) and isinstance(raw.get("activities"), list) and raw["activities"]:
            days.append({
                "day": idx + 1,
                "title": str(raw.get("title") or base_days[idx]["title"])[:80],
                "activities": [str(a)[:160] for a in raw["activities"][:5]],
            })
        else:
            days.append(base_days[idx])
    return {
        "flight_id": data["flight_id"], "hotel_id": data["hotel_id"], "cab_id": data["cab_id"],
        "summary": str(data.get("summary", ""))[:300], "days": days,
    }
