"""Tiny JSON cache: Redis when reachable, in-process dict otherwise (so local runs never break)."""
import json
import os
import time

try:
    import redis
except ImportError:  # pragma: no cover
    redis = None


class Cache:
    def __init__(self):
        self._mem: dict[str, tuple[float, str]] = {}
        self._redis = None
        url = os.getenv("REDIS_URL")
        if url and redis:
            try:
                client = redis.Redis.from_url(url, socket_connect_timeout=0.5, socket_timeout=0.5)
                client.ping()
                self._redis = client
            except Exception:
                self._redis = None

    def get(self, key: str):
        try:
            raw = self._redis.get(key) if self._redis else None
        except Exception:
            raw = None
        if raw is None:
            hit = self._mem.get(key)
            if hit and hit[0] > time.time():
                raw = hit[1]
        return json.loads(raw) if raw else None

    def set(self, key: str, value, ttl: int = 600):
        raw = json.dumps(value)
        self._mem[key] = (time.time() + ttl, raw)
        try:
            if self._redis:
                self._redis.setex(key, ttl, raw)
        except Exception:
            pass


cache = Cache()
