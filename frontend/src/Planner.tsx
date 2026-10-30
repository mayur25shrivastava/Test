import { useState } from 'react';

interface Props {
  busy: boolean;
  error: string | null;
  onPlan: (prompt: string) => void;
}

export function Planner({ busy, error, onPlan }: Props) {
  const [prompt, setPrompt] = useState('4 days in Goa under ₹30k, beaches and good food');
  return (
    <div className="form">
      <label>
        Where do you want to go?
        <textarea rows={3} value={prompt} maxLength={500} onChange={(e) => setPrompt(e.target.value)} />
      </label>
      <button disabled={busy || prompt.trim().length < 3} onClick={() => onPlan(prompt.trim())}>
        {busy ? 'Planning…' : 'Plan my trip'}
      </button>
      {error && <p className="error" role="alert">{error}</p>}
    </div>
  );
}
