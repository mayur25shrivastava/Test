"""End-to-end tests of the LangGraph workflow (real graph, interrupt and resume). No network, no real LLM."""
import pytest

pytest.importorskip("langgraph")
from langgraph.types import Command  # noqa: E402

from app import graph as g  # noqa: E402


class _Resp:
    def raise_for_status(self):
        pass

    def json(self):
        return {"id": "bk-1"}


@pytest.fixture
def booking_calls(monkeypatch):
    calls = []
    monkeypatch.setattr(g.httpx, "post", lambda url, json=None, timeout=None: (calls.append(json), _Resp())[1])
    monkeypatch.setattr(g, "get_llm", lambda: None)  # built-in planner, deterministic
    return calls


def _cfg(name):
    return {"configurable": {"thread_id": name}}


def test_plan_pauses_for_approval_then_books(booking_calls):
    graph, cfg = g.build_graph(), _cfg("approve")
    graph.invoke({"prompt": "4 days in Goa under 30k"}, cfg)
    assert graph.get_state(cfg).next            # paused at the approval step
    assert booking_calls == []                  # nothing booked before approval
    graph.invoke(Command(resume={"action": "approve", "simulate": "FAIL_HOTEL"}), cfg)
    assert graph.get_state(cfg).values["booking_id"] == "bk-1"
    assert booking_calls == [{"destination": "Goa", "budget": 30000, "notes": "FAIL_HOTEL"}]


def test_reject_never_books(booking_calls):
    graph, cfg = g.build_graph(), _cfg("reject")
    graph.invoke({"prompt": "3 days in Jaipur under 25k"}, cfg)
    graph.invoke(Command(resume={"action": "reject"}), cfg)
    assert booking_calls == []
    assert graph.get_state(cfg).values["status"] == "rejected"


def test_unknown_simulation_flag_is_dropped(booking_calls):
    graph, cfg = g.build_graph(), _cfg("flag")
    graph.invoke({"prompt": "3 days in Goa under 30k"}, cfg)
    graph.invoke(Command(resume={"action": "approve", "simulate": "anything; DROP TABLE"}), cfg)
    assert booking_calls[0]["notes"] == ""


def test_revise_returns_to_approval(booking_calls):
    graph, cfg = g.build_graph(), _cfg("revise")
    graph.invoke({"prompt": "3 days in Goa under 30k"}, cfg)
    graph.invoke(Command(resume={"action": "revise", "feedback": "cheaper"}), cfg)
    assert graph.get_state(cfg).next            # waiting for a decision again
    assert booking_calls == []


def test_missing_destination_ends_with_error(booking_calls):
    graph, cfg = g.build_graph(), _cfg("nodest")
    graph.invoke({"prompt": "something relaxing"}, cfg)
    snap = graph.get_state(cfg)
    assert not snap.next and snap.values["status"] == "failed"
