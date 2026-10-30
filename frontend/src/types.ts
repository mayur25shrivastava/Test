export type Status = 'IN_PROGRESS' | 'COMPENSATING' | 'CONFIRMED' | 'CANCELLED';
export type StepName = 'FLIGHT' | 'HOTEL' | 'CAB';

export interface BookingEvent {
  at: string;
  step: StepName | null;
  type: string;
  message: string;
}

export interface Booking {
  id: string;
  status: Status;
  details: string;
  completedSteps: string;
  failureReason: string | null;
  createdAt: string;
  events: BookingEvent[];
}

export const destinationOf = (b: Booking) => b.details.split(' budget=')[0] || 'Trip';

export interface Option { id: string; name: string; price: number }

export interface Plan {
  id: string;
  status: 'awaiting_approval' | 'booked' | 'rejected' | 'failed' | 'booking_failed' | 'unknown';
  error: string | null;
  booking_id: string | null;
  notes: string[];
  source: 'llm' | 'fallback' | null;
  cache_hit: boolean;
  ai_provider: string;
  usage: { input_tokens: number; output_tokens: number } | null;
  request?: { destination: string; days: number; budget: number };
  selection?: { flight: Option; hotel: Option; cab: Option; nights: number };
  itinerary?: { summary: string; days: { day: number; title: string; activities: string[] }[] };
  estimated_total?: number;
  over_budget?: boolean;
}
