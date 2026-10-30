import { useState } from 'react';
import { SIMULATIONS } from './BookingForm';
import type { Plan } from './types';

const rupees = (n: number) => `₹${n.toLocaleString('en-IN')}`;

interface Props {
  plan: Plan;
  busy: boolean;
  onApprove: (simulate: string) => void;
  onRevise: (feedback: string) => void;
  onDiscard: () => void;
}

export function PlanView({ plan, busy, onApprove, onRevise, onDiscard }: Props) {
  const [simulate, setSimulate] = useState('');

  if (plan.status === 'failed') {
    return (
      <section className="plan">
        <h2>I need a destination</h2>
        <p className="sub">{plan.error}</p>
      </section>
    );
  }
  if (!plan.request || !plan.selection || !plan.itinerary) return null;

  const { request, selection, itinerary } = plan;
  const sourceText = plan.source === 'llm'
    ? `Drafted by an AI model (${plan.ai_provider}${plan.cache_hit ? ', from cache' : ''}).`
    : 'Drafted by the built-in planner.';
  const tokens = plan.usage ? plan.usage.input_tokens + plan.usage.output_tokens : 0;

  return (
    <section className="plan">
      <p className="where">{request.destination}, {request.days} days</p>
      <h2>{plan.over_budget ? 'Over budget' : 'Your plan fits the budget'}</h2>
      <p className={`sub ${plan.over_budget ? 'warn' : ''}`}>
        {rupees(plan.estimated_total ?? 0)} of {rupees(request.budget)}.
        {plan.over_budget && ' This is the cheapest combination I could find. You can still book it.'}
      </p>

      <ul className="picks">
        <li><span>Flight</span><span>{selection.flight.name}</span><b>{rupees(selection.flight.price)}</b></li>
        <li><span>Hotel</span><span>{selection.hotel.name}, {selection.nights} nights</span><b>{rupees(selection.hotel.price * selection.nights)}</b></li>
        <li><span>Cab</span><span>{selection.cab.name}</span><b>{rupees(selection.cab.price)}</b></li>
      </ul>

      {itinerary.summary && <p className="summary">{itinerary.summary}</p>}
      <ol className="days">
        {itinerary.days.map((d) => (
          <li key={d.day}>
            <h3>Day {d.day}: {d.title}</h3>
            <ul>{d.activities.map((a, i) => <li key={i}>{a}</li>)}</ul>
          </li>
        ))}
      </ol>

      <details className="try">
        <summary>Try a failure</summary>
        <label>
          What goes wrong while booking
          <select value={simulate} onChange={(e) => setSimulate(e.target.value)}>
            {SIMULATIONS.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
          </select>
        </label>
      </details>

      <div className="actions">
        <button className="primary" disabled={busy} onClick={() => onApprove(simulate)}>
          {busy ? 'Working…' : 'Approve and book'}
        </button>
        <button disabled={busy} onClick={() => onRevise('Make it cheaper.')}>Make it cheaper</button>
        <button disabled={busy} onClick={onDiscard}>Discard</button>
      </div>

      <p className="meta">
        {sourceText} {tokens > 0 && `${tokens.toLocaleString()} tokens used.`}
        {plan.notes.map((n, i) => <span key={i}> {n}</span>)}
      </p>
    </section>
  );
}
