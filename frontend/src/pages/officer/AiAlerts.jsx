import { useState } from 'react';
import api, { errMsg } from '../../api';
import { useAuth, ROLES } from '../../auth';
import { Panel, Badge, Loading, ErrorBox, Empty, Flash, Pager, useLoad, dt } from '../../components/ui';

const STATUSES = ['OPEN', 'UNDER_REVIEW', 'RESOLVED', 'DISMISSED'];

export default function AiAlerts() {
  const { user } = useAuth();
  const canAct = user.role !== ROLES.AUDITOR;
  const base = canAct ? '/api/officer/ai/alerts' : '/api/audit/ai/alerts';
  const [status, setStatus] = useState('OPEN');
  const [page, setPage] = useState(0);
  const [running, setRunning] = useState(false);
  const [flash, setFlash] = useState(null);
  const [evalResult, setEvalResult] = useState(null);
  const list = useLoad(() => api.get(`${base}?page=${page}&size=10${status ? `&status=${status}` : ''}`).then((r) => r.data), [page, status]);

  async function run() {
    setRunning(true);
    try {
      const r = (await api.post('/api/officer/ai/run')).data;
      setFlash({ type: 'success', text: `Analysis complete: ${r.anomalyAlerts} anomaly, ${r.stockAlerts} low-stock, ${r.shortfallAlerts} shortfall alerts raised.` });
      list.reload();
    } catch (e) { setFlash({ type: 'danger', text: errMsg(e) }); } finally { setRunning(false); }
  }
  async function evaluate() {
    try { setEvalResult((await api.get('/api/officer/ai/evaluation')).data); }
    catch (e) { setFlash({ type: 'danger', text: errMsg(e) }); }
  }
  async function review(a, next) {
    const note = next === 'RESOLVED' || next === 'DISMISSED' ? window.prompt('Note (optional):') : null;
    try { await api.patch(`/api/officer/ai/alerts/${a.id}`, { status: next, note }); list.reload(); }
    catch (e) { setFlash({ type: 'danger', text: errMsg(e) }); }
  }

  return (
    <>
      <Flash flash={flash} onClose={() => setFlash(null)} />
      <Panel title="AI Alerts" icon="bi-shield-exclamation" actions={
        <>
          <select className="form-select form-select-sm" style={{ width: 150 }} value={status} onChange={(e) => { setPage(0); setStatus(e.target.value); }}>
            <option value="">All</option>{STATUSES.map((s) => <option key={s}>{s}</option>)}
          </select>
          {canAct && <button className="btn btn-sm btn-success" onClick={run} disabled={running}>{running ? 'Running...' : <><i className="bi bi-lightning-charge-fill" /> Run analysis</>}</button>}
        </>}>
        {list.loading && !list.data ? <Loading /> : <ErrorBox error={list.error} />}
        {list.data && (list.data.items.length === 0 ? <Empty text="No alerts for this filter." /> : (
          <div className="table-responsive">
            <table className="table table-sm align-middle gg-table">
              <thead><tr><th>Severity</th><th>Alert</th><th>Shop</th><th>Score</th><th>Status</th>{canAct && <th />}</tr></thead>
              <tbody>
                {list.data.items.map((a) => (
                  <tr key={a.id}>
                    <td><Badge value={a.severity} /><div className="small text-muted mt-1">{a.type.replace('_', ' ')}</div></td>
                    <td style={{ maxWidth: 420 }}><div className="fw-semibold">{a.title}</div><div className="small text-muted">{a.details}</div>
                      <div className="small text-muted">{dt(a.detectedAt)}{a.reviewedBy ? ` · reviewed by ${a.reviewedBy}` : ''}{a.reviewNote ? ` · ${a.reviewNote}` : ''}</div></td>
                    <td>{a.shopCode || '-'}</td>
                    <td>{a.score != null ? a.score.toFixed(2) : '-'}</td>
                    <td><Badge value={a.status} /></td>
                    {canAct && (
                      <td className="text-nowrap">
                        {a.status === 'OPEN' && <button className="btn btn-sm btn-outline-warning me-1" onClick={() => review(a, 'UNDER_REVIEW')}>Review</button>}
                        {(a.status === 'OPEN' || a.status === 'UNDER_REVIEW') && (
                          <>
                            <button className="btn btn-sm btn-outline-success me-1" onClick={() => review(a, 'RESOLVED')}>Resolve</button>
                            <button className="btn btn-sm btn-outline-secondary" onClick={() => review(a, 'DISMISSED')}>Dismiss</button>
                          </>
                        )}
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ))}
        {list.data && <Pager page={page} totalPages={list.data.totalPages} onPage={setPage} />}
      </Panel>

      {canAct && (
        <Panel title="Model evaluation on demo data" icon="bi-clipboard-data" className="mt-3"
          actions={<button className="btn btn-sm btn-outline-success" onClick={evaluate}>Evaluate detectors</button>}>
          <p className="small text-muted mb-2">Compares the detectors with the anomalies the demo generator injected (ground truth). Generate demo history first on the Users & Tools page.</p>
          {evalResult && (
            <>
              <div className="small mb-2">{evalResult.rowsEvaluated} distributions evaluated, {evalResult.labelledAnomalies} injected anomalies.</div>
              <div className="table-responsive">
                <table className="table table-sm gg-table">
                  <thead><tr><th>Detector</th><th className="text-end">Threshold</th><th className="text-end">Flagged</th><th className="text-end">True +</th><th className="text-end">False +</th><th className="text-end">Missed</th><th className="text-end">Precision</th><th className="text-end">Recall</th><th className="text-end">F1</th></tr></thead>
                  <tbody>
                    {[...evalResult.isolationForest, evalResult.rules, evalResult.combined].map((m, i) => (
                      <tr key={i}><td>{m.detector.replaceAll('_', ' ')}</td><td className="text-end">{m.threshold || '-'}</td><td className="text-end">{m.flagged}</td><td className="text-end">{m.truePositives}</td><td className="text-end">{m.falsePositives}</td><td className="text-end">{m.falseNegatives}</td><td className="text-end">{m.precision}</td><td className="text-end">{m.recall}</td><td className="text-end">{m.f1}</td></tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}
        </Panel>
      )}
    </>
  );
}
