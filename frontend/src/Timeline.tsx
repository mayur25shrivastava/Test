import type { BookingEvent } from './types';

export function Timeline({ events }: { events: BookingEvent[] }) {
  return (
    <section className="timeline">
      <h3>What happened</h3>
      <ul>
        {events.map((e, i) => (
          <li key={i} className={e.type.toLowerCase()}>
            <time>{new Date(e.at).toLocaleTimeString()}</time>
            <span>{e.message}</span>
          </li>
        ))}
      </ul>
    </section>
  );
}
