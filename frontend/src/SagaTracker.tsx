import type { Booking, StepName } from './types';
import { destinationOf } from './types';

const STEPS: { key: StepName; label: string; working: string }[] = [
  { key: 'FLIGHT', label: 'Flight', working: 'Reserving your seat' },
  { key: 'HOTEL', label: 'Hotel', working: 'Holding your room' },
  { key: 'CAB', label: 'Cab', working: 'Lining up your pickup' },
];

type StepState = 'waiting' | 'working' | 'reserved' | 'failed' | 'released';
const STEP_EVENTS = new Set(['STEP_RESERVED', 'STEP_FAILED', 'STEP_CANCELLED']);

function stepStates(b: Booking): StepState[] {
  let activeTaken = false;
  return STEPS.map(({ key }) => {
    const last = [...b.events].reverse().find((e) => e.step === key && STEP_EVENTS.has(e.type));
    if (last?.type === 'STEP_RESERVED') return 'reserved';
    if (last?.type === 'STEP_FAILED') return 'failed';
    if (last?.type === 'STEP_CANCELLED') return 'released';
    if (b.status === 'IN_PROGRESS' && !activeTaken) {
      activeTaken = true;
      return 'working';
    }
    return 'waiting';
  });
}

const STATE_TEXT: Record<StepState, (working: string) => string> = {
  waiting: () => 'Waiting',
  working: (w) => w,
  reserved: () => 'Reserved',
  failed: () => 'Could not reserve',
  released: () => 'Released, rolled back',
};

const HEADLINE: Record<Booking['status'], { title: string; sub: (b: Booking) => string }> = {
  IN_PROGRESS: { title: 'Booking your trip', sub: () => 'Each step is reserved one after the other.' },
  CONFIRMED: { title: 'Trip confirmed', sub: () => 'Flight, hotel and cab are all reserved.' },
  COMPENSATING: { title: 'Rolling back', sub: () => 'One step failed. Releasing what was already reserved.' },
  CANCELLED: {
    title: 'Booking cancelled',
    sub: (b) => (b.failureReason ? `${b.failureReason}. Nothing stays reserved.` : 'Nothing stays reserved.'),
  },
};

export function SagaTracker({ booking }: { booking: Booking }) {
  const states = stepStates(booking);
  const copy = HEADLINE[booking.status];
  return (
    <section className={`tracker ${booking.status.toLowerCase()}`} aria-live="polite">
      <p className="where">{destinationOf(booking)}</p>
      <h2>{copy.title}</h2>
      <p className="sub">{copy.sub(booking)}</p>
      <ol className="route">
        {STEPS.map((s, i) => (
          <li key={s.key} className={`stop ${states[i]}`}>
            <span className="node" aria-hidden="true" />
            <div>
              <strong>{s.label}</strong>
              <span className="state">{STATE_TEXT[states[i]](s.working)}</span>
            </div>
          </li>
        ))}
      </ol>
    </section>
  );
}
