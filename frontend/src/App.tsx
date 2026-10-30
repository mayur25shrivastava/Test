import { useCallback, useEffect, useState } from 'react';
import { approvePlan, createBooking, createPlan, listBookings, rejectPlan, revisePlan } from './api';
import { BookingForm } from './BookingForm';
import { Planner } from './Planner';
import { PlanView } from './PlanView';
import { SagaTracker } from './SagaTracker';
import { Timeline } from './Timeline';
import { useBookingStream } from './useBookingStream';
import { destinationOf, type Booking, type Plan } from './types';

const messageOf = (e: unknown) => (e instanceof Error ? e.message : 'Something went wrong.');

export default function App() {
  const [selected, setSelected] = useState<string | null>(null);
  const [plan, setPlan] = useState<Plan | null>(null);
  const [trips, setTrips] = useState<Booking[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { booking, live } = useBookingStream(selected);

  const refresh = useCallback(() => {
    listBookings().then(setTrips).catch(() => { /* keep the last list */ });
  }, []);

  useEffect(() => {
    refresh();
    const t = setInterval(refresh, 4000);
    return () => clearInterval(t);
  }, [refresh]);

  const run = async (work: () => Promise<void>) => {
    setBusy(true);
    setError(null);
    try {
      await work();
    } catch (e) {
      setError(messageOf(e));
    } finally {
      setBusy(false);
    }
  };

  const openBooking = (id: string) => {
    setPlan(null);
    setSelected(id);
    refresh();
  };

  const plan_ = (prompt: string) => run(async () => { setSelected(null); setPlan(await createPlan(prompt)); });
  const approve = (simulate: string) => run(async () => {
    const result = await approvePlan(plan!.id, simulate);
    if (result.booking_id) openBooking(result.booking_id);
    else setError(result.error ?? 'The booking could not be started.');
  });
  const revise = (feedback: string) => run(async () => setPlan(await revisePlan(plan!.id, feedback)));
  const discard = () => run(async () => { await rejectPlan(plan!.id); setPlan(null); });
  const bookDirect = (destination: string, budget: number, notes: string) => run(async () => {
    openBooking((await createBooking(destination, budget, notes)).id);
  });

  return (
    <div className="app">
      <aside className="side">
        <h1>Trip Saga</h1>
        <Planner busy={busy} error={error} onPlan={plan_} />
        <details className="direct">
          <summary>Book without the planner</summary>
          <BookingForm busy={busy} error={null} onSubmit={bookDirect} />
        </details>
        <nav aria-label="Recent trips">
          <h3>Recent trips</h3>
          {trips.length === 0 && <p className="empty">No trips yet. Plan one to watch it happen.</p>}
          <ul className="trips">
            {trips.map((t) => (
              <li key={t.id}>
                <button className={t.id === selected ? 'on' : ''} onClick={() => openBooking(t.id)}>
                  <span className={`dot ${t.status.toLowerCase()}`} aria-hidden="true" />
                  <span>{destinationOf(t)}</span>
                  <time>{new Date(t.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</time>
                </button>
              </li>
            ))}
          </ul>
        </nav>
      </aside>
      <main className="main">
        {plan ? (
          <PlanView plan={plan} busy={busy} onApprove={approve} onRevise={revise} onDiscard={discard} />
        ) : booking ? (
          <>
            <SagaTracker booking={booking} />
            <Timeline events={booking.events} />
            {live && <p className="live">Live</p>}
          </>
        ) : (
          <div className="blank">
            <h2>Say where you want to go</h2>
            <p>Describe a trip in your own words. You will see a plan and its price first, and nothing is booked until you approve it.</p>
          </div>
        )}
      </main>
    </div>
  );
}
