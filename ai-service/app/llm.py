"""LLM provider factory. Free options: Groq (free API key), Google Gemini (free tier), Ollama (local)."""
import hashlib
import json
import os

from . import planner
from .cache import cache

DEFAULT_MODELS = {
    "groq": "llama-3.1-8b-instant",
    "gemini": "gemini-2.5-flash",
    "ollama": "llama3.2",
}


def provider_name() -> str:
    p = (os.getenv("LLM_PROVIDER") or "auto").lower()
    if p != "auto":
        return p
    if os.getenv("GROQ_API_KEY"):
        return "groq"
    if os.getenv("GOOGLE_API_KEY"):
        return "gemini"
    if os.getenv("OLLAMA_BASE_URL"):
        return "ollama"
    return "mock"


def get_llm():
    """Returns a LangChain chat model, or None for 'mock' (built-in deterministic planner)."""
    p = provider_name()
    model = os.getenv("LLM_MODEL") or DEFAULT_MODELS.get(p, "")
    if p == "groq":
        from langchain_groq import ChatGroq
        return ChatGroq(model=model, temperature=0.3, timeout=30, max_retries=1)
    if p == "gemini":
        from langchain_google_genai import ChatGoogleGenerativeAI
        return ChatGoogleGenerativeAI(model=model, temperature=0.3, timeout=30, max_retries=1)
    if p == "ollama":
        from langchain_ollama import ChatOllama
        return ChatOllama(model=model, base_url=os.getenv("OLLAMA_BASE_URL", "http://host.docker.internal:11434"),
                          temperature=0.3)
    return None


def _usage(msg) -> dict:
    u = getattr(msg, "usage_metadata", None) or {}
    return {"input_tokens": int(u.get("input_tokens", 0)), "output_tokens": int(u.get("output_tokens", 0))}


def draft_with_llm(llm, request: dict, options: dict, feedback: str):
    """Returns (draft, usage, cache_hit). Raises on any LLM or validation problem; caller falls back."""
    system, user = planner.build_prompt(request, options, feedback)
    key = "draft:" + hashlib.sha256(f"{provider_name()}|{system}|{user}".encode()).hexdigest()
    cached = cache.get(key)
    if cached:
        return cached, {"input_tokens": 0, "output_tokens": 0}, True
    msg = llm.invoke([("system", system), ("user", user)])
    text = msg.content if isinstance(msg.content, str) else json.dumps(msg.content)
    draft = planner.parse_llm_json(text, options, request)
    cache.set(key, draft, ttl=3600)
    return draft, _usage(msg), False


def extract_request_with_llm(llm, prompt: str) -> dict | None:
    system = ('Extract trip details. Reply with ONLY JSON: {"destination": str, "days": int, "budget": int}. '
              "budget is in Indian rupees as a plain number.")
    msg = llm.invoke([("system", system), ("user", prompt)])
    text = msg.content if isinstance(msg.content, str) else ""
    start, end = text.find("{"), text.rfind("}")
    if start == -1 or end == -1:
        return None
    return planner.sanitize_request(json.loads(text[start:end + 1]))
