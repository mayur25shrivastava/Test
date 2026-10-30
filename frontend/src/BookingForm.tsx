import { useState } from 'react';

export const SIMULATIONS = [
  { value: '', label: 'Everything works' },
  { value: 'FAIL_FLIGHT', label: 'Flight is sold out' },
  { value: 'FAIL_HOTEL', label: 'Hotel is full' },
  { value: 'FAIL_CAB', label: 'No cab available' },
  { value: 'CRASH_HOTEL', label: 'Hotel service crashes' },
];

interface Props {
  busy: boolean;
  error: string | null;
  onSubmit: (destination: string, budget: number, notes: string) => void;
}

export function BookingForm({ busy, error, onSubmit }: Props) {
  const [destination, setDestination] = useState('Goa');
  const [budget, setBudget] = useState(30000);
  const [notes, setNotes] = useState('');

  return (
    <div className="form">
      <label>
        Destination
        <input value={destination} onChange={(e) => setDestination(e.target.value)} />
      </label>
      <label>
        Budget (₹)
        <input type="number" min={0} value={budget} onChange={(e) => setBudget(Number(e.target.value))} />
      </label>
      <details>
        <summary>Try a failure</summary>
        <label>
          What goes wrong
          <select value={notes} onChange={(e) => setNotes(e.target.value)}>
            {SIMULATIONS.map((s) => (
              <option key={s.value} value={s.value}>{s.label}</option>
            ))}
          </select>
        </label>
      </details>
      <button disabled={busy || !destination.trim()} onClick={() => onSubmit(destination.trim(), budget, notes)}>
        {busy ? 'Booking…' : 'Book trip'}
      </button>
      {error && <p className="error" role="alert">{error}</p>}
    </div>
  );
}
