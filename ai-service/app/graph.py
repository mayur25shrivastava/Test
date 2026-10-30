"""LangGraph workflow: parse -> search -> draft -> validate (loop) -> human approval -> book."""
import os
from typing import TypedDict

import httpx
from langgraph.checkpoint.memory import MemorySaver
from langgraph.graph import END, START, StateGraph
from langgraph.types import interrupt

from . import catalog, planner
from .cache import cache
from .llm import draft_with_llm, extract_request_with_llm, get_llm

BOOKING_URL = os.getenv("BOOKING_URL", "http://api-gateway:8080")
ALLOWED_SIMULATIONS = {"", "FAIL_FLIGHT", "FAIL_HOTEL", "FAIL_CAB", "CRASH_HOTEL"}
MAX_AUTO_REVISIONS = 2


class PlanState(TypedDict, total=False):
    prompt: str
    request: dict
    options: dict
    draft: dict
    feedback: str
    revisions: int
    needs_revision: bool
    estimated_total: int
    over_budget: bool
    source: str            # "llm" or "fallback"
    usage: dict
    cache_hit: bool
    notes: list
    decision: dict
    booking_id: str
    status: str
    error: str


def _usage(state) -> dict:
    return dict(state.get("usage") or {"input_tokens": 0, "output_tokens": 0})


def parse_request(state: PlanState):
    prompt = state["prompt"]
    notes = list(state.get("notes", []))
    request = planner.parse_regex(prompt)
    usage = _usage(state)
    if not request["destination"]:           # only spend an LLM call when the cheap parser fails
        llm = get_llm()
        if llm:
            try:
                request = extract_request_with_llm(llm, prompt)
            except Exception:
                request = None
        else:
            request = None
        if not request:
            return {"status": "failed", "error": 'I could not find a destination. Try "4 days in Goa under 30k".'}
        notes.append("Destination understood by the AI model.")
    return {"request": request, "usage": usage, "notes": notes, "revisions": 0}


def search_options(state: PlanState):
    dest = state["request"]["destination"]
    key = f"options:{dest.lower()}"
    options = cache.get(key)
    if not options:
        options = catalog.search(dest)
        cache.set(key, options, ttl=600)
    return {"options": options}


def draft_plan(state: PlanState):
    request, options = state["request"], state["options"]
    notes = list(state.get("notes", []))
    usage = _usage(state)
    draft, source, hit = None, "fallback", False
    llm = get_llm()
    if llm:
        try:
            draft, u, hit = draft_with_llm(llm, request, options, state.get("feedback", ""))
            usage["input_tokens"] += u["input_tokens"]
            usage["output_tokens"] += u["output_tokens"]
            source = "llm"
        except Exception as e:  # rate limit, timeout, bad JSON ...
            notes.append(f"AI model unavailable ({type(e).__name__}); used the built-in planner instead.")
    if draft is None:
        draft = planner.cheapest_draft(options, request)
    return {"draft": draft, "source": source, "usage": usage, "cache_hit": hit, "notes": notes}


def validate(state: PlanState):
    request, options, draft = state["request"], state["options"], dict(state["draft"])
    notes = list(state.get("notes", []))
    total = planner.total_cost(options, draft, request)
    over = total > request["budget"]
    revisions = state.get("revisions", 0)
    feedback = f"The total was Rs {total}, above the Rs {request['budget']} budget. Choose cheaper options."
    # Stop asking when the model returns the same total again: another identical prompt cannot help.
    if over and state.get("source") == "llm" and revisions < MAX_AUTO_REVISIONS and feedback != state.get("feedback"):
        return {"needs_revision": True, "revisions": revisions + 1, "feedback": feedback}
    if over:  # out of attempts: keep the day plan but switch to the cheapest options
        cheap = planner.cheapest_draft(options, request)
        draft.update(flight_id=cheap["flight_id"], hotel_id=cheap["hotel_id"], cab_id=cheap["cab_id"])
        total = planner.total_cost(options, draft, request)
        over = total > request["budget"]
        notes.append("Switched to the cheapest options to get as close to your budget as possible.")
    return {"needs_revision": False, "draft": draft, "estimated_total": total, "over_budget": over,
            "notes": notes, "status": "awaiting_approval"}


def await_approval(state: PlanState):
    # Pauses the graph here until the API resumes it with the person's decision.
    decision = interrupt({"kind": "approval", "estimated_total": state.get("estimated_total")})
    update = {"decision": decision}
    if decision.get("action") == "revise":
        update.update(feedback=decision.get("feedback", "Make it cheaper."), revisions=0)
    return update


def route_validate(state: PlanState):
    return "draft_plan" if state.get("needs_revision") else "await_approval"


def route_decision(state: PlanState):
    action = (state.get("decision") or {}).get("action")
    if action == "approve":
        return "book"
    if action == "revise":
        return "draft_plan"
    return "end"


def reject(state: PlanState):
    return {"status": "rejected"}


def book(state: PlanState):
    simulate = (state["decision"].get("simulate") or "")
    if simulate not in ALLOWED_SIMULATIONS:
        simulate = ""
    req = state["request"]
    try:
        # notes are never taken from model output; only the validated simulation flag is forwarded
        res = httpx.post(f"{BOOKING_URL}/api/bookings", timeout=10,
                         json={"destination": req["destination"], "budget": req["budget"], "notes": simulate})
        res.raise_for_status()
        return {"booking_id": res.json()["id"], "status": "booked"}
    except Exception as e:
        return {"status": "booking_failed", "error": f"Could not start the booking ({type(e).__name__}). Try again."}


def build_graph():
    g = StateGraph(PlanState)
    g.add_node("parse_request", parse_request)
    g.add_node("search_options", search_options)
    g.add_node("draft_plan", draft_plan)
    g.add_node("validate", validate)
    g.add_node("await_approval", await_approval)
    g.add_node("reject", reject)
    g.add_node("book", book)
    g.add_edge(START, "parse_request")
    g.add_conditional_edges("parse_request", lambda s: END if s.get("status") == "failed" else "search_options",
                            {END: END, "search_options": "search_options"})
    g.add_edge("search_options", "draft_plan")
    g.add_edge("draft_plan", "validate")
    g.add_conditional_edges("validate", route_validate, {"draft_plan": "draft_plan", "await_approval": "await_approval"})
    g.add_conditional_edges("await_approval", route_decision, {"book": "book", "draft_plan": "draft_plan", "end": "reject"})
    g.add_edge("reject", END)
    g.add_edge("book", END)
    # MemorySaver keeps plans in process memory. Swap for the Postgres checkpointer to survive restarts.
    return g.compile(checkpointer=MemorySaver())
