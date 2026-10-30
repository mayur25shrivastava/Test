"""Mock flight / hotel / cab provider search. Deterministic per destination so results are stable and cacheable."""
import hashlib
import random


def _rng(destination: str) -> random.Random:
    seed = int(hashlib.sha256(destination.lower().encode()).hexdigest(), 16) % (2**32)
    return random.Random(seed)


def search(destination: str) -> dict:
    r = _rng(destination)
    d = destination.title()
    flight_base = 3200 + r.randint(0, 3000)
    hotel_base = 1100 + r.randint(0, 600)
    cab_base = 700 + r.randint(0, 500)
    return {
        "flights": [
            {"id": "FL-1", "name": "IndiGo, economy", "price": flight_base},
            {"id": "FL-2", "name": "Air India Express, economy", "price": flight_base + 900},
            {"id": "FL-3", "name": "Akasa Air, flexible fare", "price": flight_base + 2200},
        ],
        "hotels": [  # price is per night
            {"id": "HT-1", "name": f"{d} Budget Stay", "price": hotel_base},
            {"id": "HT-2", "name": f"{d} Comfort Hotel", "price": hotel_base * 2 + 400},
            {"id": "HT-3", "name": f"{d} Grand Resort", "price": hotel_base * 5 + 1500},
        ],
        "cabs": [  # price is for the whole trip
            {"id": "CB-1", "name": "Shared airport transfers", "price": cab_base},
            {"id": "CB-2", "name": "Private sedan for the stay", "price": cab_base * 3 + 800},
        ],
    }
