import { useEffect, useRef, useState } from 'react';
import api, { errMsg } from '../../api';
import { useAuth } from '../../auth';
import { Panel, useLoad } from '../../components/ui';

const SUGGEST = {
  SUPER_ADMIN: ['Which shops have low stock?', 'Why were shops flagged by the AI?', 'What is the entitlement for an AAY card?', 'How should I review an anomaly alert?'],
  GOVT_OFFICER: ['Which shops have low stock?', 'What alerts are open?', 'Which department handles overcharging complaints?', 'What is the entitlement for an AAY card?'],
  AUDITOR: ['What alerts are open?', 'How does the AI detect anomalies?', 'When is a stock level critical?'],
  WAREHOUSE_MANAGER: ['How much stock do I have?', 'How do I transfer stock to a shop?', 'How do I report damaged stock?'],
  FPS_DEALER: ['How much stock does my shop have?', 'Why was my distribution refused?', 'What checks are made before issuing grain?'],
  BENEFICIARY: ['How much rice do I have left this month?', 'What is my family entitled to?', 'How do I make a complaint?', 'Why does my login not show my ration card?'],
};

export default function Assistant() {
  const { user } = useAuth();
  const status = useLoad(() => api.get('/api/assistant/status').then((r) => r.data));
  const [msgs, setMsgs] = useState([]);
  const [q, setQ] = useState('');
  const [busy, setBusy] = useState(false);
  const end = useRef(null);
  useEffect(() => { end.current?.scrollIntoView({ behavior: 'smooth', block: 'end' }); }, [msgs, busy]);

  async function send(text) {
    const question = (text ?? q).trim();
    if (question.length < 3 || busy) return;
    setMsgs((m) => [...m, { who: 'me', text: question }]);
    setQ(''); setBusy(true);
    try {
      const r = (await api.post('/api/assistant/ask', { question })).data;
      setMsgs((m) => [...m, { who: 'bot', ...r }]);
    } catch (e) {
      setMsgs((m) => [...m, { who: 'bot', error: errMsg(e) }]);
    } finally { setBusy(false); }
  }

  const s = status.data;
  return (
    <Panel title="AI Assistant" icon="bi-robot" actions={s && (
      <span className={`badge text-bg-${s.configuredMode === 'llm' ? 'success' : 'secondary'}`}>
        {s.configuredMode === 'llm' ? 'Language model' : 'Local mode'} · {s.retrieval === 'VECTOR' ? 'semantic search' : 'keyword search'}
      </span>)}>
      <div className="gg-chat">
        {msgs.length === 0 && (
          <div className="text-muted mb-3">
            Ask about entitlement, distribution rules, complaints, AI alerts{user.role === 'BENEFICIARY' ? ', or your own ration' : ' or stock'}.
            Answers use the policy notes and only the data your role may see.
          </div>
        )}
        {msgs.map((m, i) => m.who === 'me' ? (
          <div key={i} className="gg-bubble me">{m.text}</div>
        ) : (
          <div key={i} className="gg-bubble bot">
            {m.error ? <span className="text-danger">{m.error}</span> : (
              <>
                <div style={{ whiteSpace: 'pre-wrap' }}>{m.answer}</div>
                {m.notice && <div className="small text-warning-emphasis mt-2"><i className="bi bi-info-circle me-1" />{m.notice}</div>}
                {m.sources?.length > 0 && (
                  <details className="mt-2 small">
                    <summary>Sources ({m.sources.length}) · answered in {m.mode === 'LLM' ? 'language model' : 'local'} mode</summary>
                    {m.sources.map((s) => <div key={s.n} className="mt-1"><b>[{s.n}] {s.title}</b><div className="text-muted">{s.snippet}</div></div>)}
                  </details>
                )}
                {m.liveData?.length > 0 && (
                  <details className="mt-1 small">
                    <summary>Live data used ({m.liveData.length})</summary>
                    {m.liveData.map((d, k) => <div key={k} className="text-muted mt-1">{d}</div>)}
                  </details>
                )}
              </>
            )}
          </div>
        ))}
        {busy && <div className="gg-bubble bot"><span className="spinner-border spinner-border-sm me-2" />Thinking...</div>}
        <div ref={end} />
      </div>

      <div className="d-flex flex-wrap gap-2 mb-2">
        {(SUGGEST[user.role] || []).map((t) => <button key={t} className="btn btn-sm btn-outline-success" disabled={busy} onClick={() => send(t)}>{t}</button>)}
      </div>
      <form className="input-group" onSubmit={(e) => { e.preventDefault(); send(); }}>
        <input className="form-control" placeholder="Ask a question..." maxLength={500} value={q} onChange={(e) => setQ(e.target.value)} aria-label="Question" />
        <button className="btn btn-success" disabled={busy || q.trim().length < 3}><i className="bi bi-send" /></button>
      </form>
    </Panel>
  );
}
