import uuid

from fastapi import FastAPI, HTTPException
from langgraph.types import Command
from pydantic import BaseModel, Field

from . import planner
from .graph import build_graph
from .llm import provider_name

app = FastAPI(title="Trip Saga AI planner")
graph = build_graph()


class CreatePlan(BaseModel):
    prompt: str = Field(min_length=3, max_length=500)


class Approve(BaseModel):
    simulate: str = ""


class Revise(BaseModel):
    feedback: str = Field(default="Make it cheaper.", max_length=200)


def _config(plan_id: str) -> dict:
    return {"configurable": {"thread_id": plan_id}}


def _view(plan_id: str) -> dict:
    snap = graph.get_state(_config(plan_id))
    v = snap.values
    if not v:
        raise HTTPException(404, "plan not found")
    status = "awaiting_approval" if snap.next else v.get("status", "unknown")
    view = {"id": plan_id, "status": status, "error": v.get("error"), "booking_id": v.get("booking_id"),
            "notes": v.get("notes", []), "usage": v.get("usage"), "source": v.get("source"),
            "cache_hit": v.get("cache_hit", False), "ai_provider": provider_name()}
    if v.get("draft"):
        req, opts, draft = v["request"], v["options"], v["draft"]
        view.update(request=req, selection=planner.selection(opts, draft, req),
                    itinerary={"summary": draft["summary"], "days": draft["days"]},
                    estimated_total=v.get("estimated_total"), over_budget=v.get("over_budget", False))
    return view


@app.get("/health")
def health():
    return {"status": "ok", "ai_provider": provider_name()}


@app.post("/plans")
def create_plan(body: CreatePlan):
    plan_id = uuid.uuid4().hex
    graph.invoke({"prompt": body.prompt}, _config(plan_id))
    return _view(plan_id)


@app.get("/plans/{plan_id}")
def get_plan(plan_id: str):
    return _view(plan_id)


def _resume(plan_id: str, decision: dict):
    if _view(plan_id)["status"] != "awaiting_approval":
        raise HTTPException(409, "plan is not waiting for a decision")
    graph.invoke(Command(resume=decision), _config(plan_id))
    return _view(plan_id)


@app.post("/plans/{plan_id}/approve")
def approve(plan_id: str, body: Approve):
    return _resume(plan_id, {"action": "approve", "simulate": body.simulate})


@app.post("/plans/{plan_id}/revise")
def revise(plan_id: str, body: Revise):
    return _resume(plan_id, {"action": "revise", "feedback": body.feedback})


@app.post("/plans/{plan_id}/reject")
def reject(plan_id: str):
    return _resume(plan_id, {"action": "reject"})
