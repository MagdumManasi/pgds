import { useState } from 'react';
import { Bar } from 'react-chartjs-2';
import { Chart as ChartJS, CategoryScale, LinearScale, BarElement, Tooltip, Legend } from 'chart.js';
import api, { downloadFile, errMsg } from '../../api';
import { useAuth, ROLES } from '../../auth';
import { Panel, Loading, ErrorBox, Empty, Flash, useLoad, kg, num, GRAIN_COLOR } from '../../components/ui';

ChartJS.register(CategoryScale, LinearScale, BarElement, Tooltip, Legend);

const TABS = [
  { key: 'monthly', label: 'Monthly', file: 'monthly-report.csv' },
  { key: 'districts', label: 'District-wise', file: 'district-report.csv' },
  { key: 'shops', label: 'Shop performance', file: 'shop-report.csv' },
  { key: 'complaints', label: 'Complaints', file: 'complaint-report.csv' },
];

export default function Reports() {
  const { user } = useAuth();
  const base = user.role === ROLES.AUDITOR ? '/api/audit/reports' : '/api/officer/reports';
  const [tab, setTab] = useState('monthly');
  const [period, setPeriod] = useState(new Date().toISOString().slice(0, 7));
  const [months, setMonths] = useState(12);
  const [flash, setFlash] = useState(null);

  const query = tab === 'monthly' ? `?months=${months}` : tab === 'complaints' ? '' : `?period=${period}`;
  const data = useLoad(() => api.get(`${base}/${tab}${query}`).then((r) => r.data), [tab, period, months, base]);
  const current = TABS.find((t) => t.key === tab);

  async function csv() {
    try { await downloadFile(`${base}/${tab}.csv${query}`, current.file); }
    catch (e) { setFlash({ type: 'danger', text: errMsg(e) }); }
  }

  const chart = tab === 'monthly' && data.data && {
    labels: data.data.map((r) => r.period),
    datasets: [
      { label: 'Rice', data: data.data.map((r) => Number(r.riceKg)), backgroundColor: GRAIN_COLOR.RICE },
      { label: 'Wheat', data: data.data.map((r) => Number(r.wheatKg)), backgroundColor: GRAIN_COLOR.WHEAT },
    ],
  };

  return (
    <>
      <Flash flash={flash} onClose={() => setFlash(null)} />
      <Panel title="Reports" icon="bi-file-earmark-bar-graph" actions={
        <>
          {tab === 'monthly' && (
            <select className="form-select form-select-sm" style={{ width: 130 }} value={months} onChange={(e) => setMonths(Number(e.target.value))}>
              {[3, 6, 12, 24].map((m) => <option key={m} value={m}>Last {m} months</option>)}
            </select>
          )}
          {(tab === 'districts' || tab === 'shops') && (
            <input type="month" className="form-control form-control-sm" style={{ width: 160 }} value={period} onChange={(e) => e.target.value && setPeriod(e.target.value)} />
          )}
          <button className="btn btn-sm btn-outline-success" onClick={csv}><i className="bi bi-download" /> CSV</button>
        </>}>
        <ul className="nav nav-pills mb-3">
          {TABS.map((t) => <li key={t.key} className="nav-item"><button className={`nav-link ${tab === t.key ? 'active' : ''}`} onClick={() => setTab(t.key)}>{t.label}</button></li>)}
        </ul>

        {data.loading && !data.data ? <Loading /> : <ErrorBox error={data.error} />}

        {tab === 'monthly' && data.data && (
          <>
            <div style={{ height: 260 }}><Bar data={chart} options={{ responsive: true, maintainAspectRatio: false, scales: { x: { stacked: true }, y: { stacked: true, beginAtZero: true } }, plugins: { legend: { position: 'bottom' } } }} /></div>
            <div className="table-responsive mt-3">
              <table className="table table-sm gg-table">
                <thead><tr><th>Month</th><th className="text-end">Rice</th><th className="text-end">Wheat</th><th className="text-end">Total</th><th className="text-end">Cards served</th></tr></thead>
                <tbody>{data.data.map((r) => <tr key={r.period}><td>{r.period}</td><td className="text-end">{kg(r.riceKg)}</td><td className="text-end">{kg(r.wheatKg)}</td><td className="text-end fw-semibold">{kg(r.totalKg)}</td><td className="text-end">{num(r.cardsServed)}</td></tr>)}</tbody>
              </table>
            </div>
          </>
        )}

        {tab === 'districts' && data.data && (data.data.length === 0 ? <Empty /> : (
          <div className="table-responsive">
            <table className="table table-sm gg-table">
              <thead><tr><th>District</th><th className="text-end">Shops</th><th className="text-end">Beneficiaries</th><th className="text-end">Cards served</th><th className="text-end">Issued</th><th className="text-end">Requirement</th><th className="text-end">Performance</th><th className="text-end">Stock</th></tr></thead>
              <tbody>{data.data.map((r) => <tr key={r.district}><td><strong>{r.district}</strong></td><td className="text-end">{r.shops}</td><td className="text-end">{num(r.beneficiaries)}</td><td className="text-end">{num(r.cardsServed)}</td><td className="text-end">{kg(r.totalKg)}</td><td className="text-end">{kg(r.requirementKg)}</td><td className="text-end">{r.performancePercent}%</td><td className="text-end">{kg(r.stockKg)}</td></tr>)}</tbody>
            </table>
          </div>
        ))}

        {tab === 'shops' && data.data && (data.data.length === 0 ? <Empty /> : (
          <div className="table-responsive">
            <table className="table table-sm gg-table align-middle">
              <thead><tr><th>Shop</th><th>District</th><th className="text-end">Beneficiaries</th><th className="text-end">Cards served</th><th className="text-end">Rice</th><th className="text-end">Wheat</th><th className="text-end">Requirement</th><th style={{ minWidth: 130 }}>Performance</th><th className="text-end">Stock</th></tr></thead>
              <tbody>{data.data.map((r) => (
                <tr key={r.shopCode}>
                  <td><strong>{r.shopCode}</strong><div className="small text-muted">{r.shopName}</div></td><td>{r.district}</td>
                  <td className="text-end">{num(r.beneficiaries)}</td><td className="text-end">{num(r.cardsServed)}</td>
                  <td className="text-end">{kg(r.riceKg)}</td><td className="text-end">{kg(r.wheatKg)}</td><td className="text-end">{kg(r.requirementKg)}</td>
                  <td><div className="progress" style={{ height: 8 }}><div className={`progress-bar bg-${r.performancePercent >= 80 ? 'success' : r.performancePercent >= 60 ? 'warning' : 'danger'}`} style={{ width: `${r.performancePercent}%` }} /></div><small>{r.performancePercent}%</small></td>
                  <td className="text-end">{kg(r.stockKg)}</td>
                </tr>))}</tbody>
            </table>
          </div>
        ))}

        {tab === 'complaints' && data.data && (
          <div className="row g-3">
            <div className="col-md-7">
              <h6>By category ({data.data.total} total)</h6>
              {data.data.byCategory.length === 0 ? <Empty /> : data.data.byCategory.map((c) => (
                <div key={c.label} className="mb-2">
                  <div className="d-flex justify-content-between small"><span>{c.label}</span><span>{c.count}</span></div>
                  <div className="progress" style={{ height: 8 }}><div className="progress-bar bg-success" style={{ width: `${(c.count / data.data.total) * 100}%` }} /></div>
                </div>
              ))}
            </div>
            <div className="col-md-5">
              <h6>By status</h6>
              <ul className="list-group">{data.data.byStatus.map((s) => <li key={s.label} className="list-group-item d-flex justify-content-between"><span>{s.label.replace('_', ' ')}</span><strong>{s.count}</strong></li>)}</ul>
            </div>
          </div>
        )}
      </Panel>
    </>
  );
}
