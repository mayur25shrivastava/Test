import { useEffect, useState } from 'react';
import type { Booking } from './types';

/** Live booking state over Server-Sent Events. Closes itself once the saga reaches a final state. */
export function useBookingStream(id: string | null) {
  const [booking, setBooking] = useState<Booking | null>(null);
  const [live, setLive] = useState(false);

  useEffect(() => {
    setBooking(null);
    if (!id) return;
    const source = new EventSource(`/api/stream/bookings/${id}`);
    source.onopen = () => setLive(true);
    source.onerror = () => setLive(false); // the browser reconnects on its own
    source.addEventListener('booking', (e) => setBooking(JSON.parse((e as MessageEvent).data)));
    source.addEventListener('done', () => {
      setLive(false);
      source.close();
    });
    return () => source.close();
  }, [id]);

  return { booking, live };
}
