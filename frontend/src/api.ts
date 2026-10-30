import type { Booking, Plan } from './types';

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

async function call<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`/api${path}`, init);
  if (res.status === 503) throw new ApiError(503, 'This part of the app is temporarily unavailable. Try again in a few seconds.');
  if (res.status === 429) throw new ApiError(429, 'Too many requests. Wait a moment and try again.');
  if (!res.ok) throw new ApiError(res.status, `Request failed (${res.status}).`);
  return res.json() as Promise<T>;
}

export const createBooking = (destination: string, budget: number, notes: string) =>
  call<Booking>('/bookings', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ destination, budget, notes }),
  });

export const listBookings = () => call<Booking[]>('/bookings');

const post = <T,>(path: string, body: unknown) =>
  call<T>(path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });

export const createPlan = (prompt: string) => post<Plan>('/plans', { prompt });
export const approvePlan = (id: string, simulate: string) => post<Plan>(`/plans/${id}/approve`, { simulate });
export const revisePlan = (id: string, feedback: string) => post<Plan>(`/plans/${id}/revise`, { feedback });
export const rejectPlan = (id: string) => post<Plan>(`/plans/${id}/reject`, {});
