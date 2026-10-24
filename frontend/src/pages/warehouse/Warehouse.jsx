import { useState } from 'react';
import api, { errMsg } from '../../api';
import { useAuth } from '../../auth';
import StockPanel from '../../components/StockPanel';
import { Panel, Flash, useLoad, GRAINS, Loading, ErrorBox, Empty, kg } from '../../components/ui';

const blank = { grainType: 'RICE', quantityKg: '', remarks: '', fpsId: '' };

export default function Warehouse() {
  const { user } = useAuth();
  const isAdmin = user.role === 'SUPER_ADMIN';
  const [selectedWarehouseId, setSelectedWarehouseId] = useState('');
  const warehouses = useLoad(() => (isAdmin ? api.get('/api/admin/warehouses').then((r) => r.data) : Promise.resolve([])), [isAdmin]);
  const id = user.warehouseId || selectedWarehouseId || warehouses.data?.[0]?.id;
  const base = `/api/warehouse/${id}`;
  const [tab, setTab] = useState('receipts');
  const [f, setF] = useState(blank);
  const [flash, setFlash] = useState(null);
  const [refresh, setRefresh] = useState(0);
  const shops = useLoad(() => (id ? api.get(`${base}/shops`).then((r) => r.data) : Promise.resolve([])), [id]);
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });

  if (!isAdmin && !id) return <div className="alert alert-warning">Your account is not linked to a warehouse. Ask the administrator to assign one.</div>;

  function useSuggestedTransfer(shop, grainType, quantityKg) {
    if (Number(quantityKg) <= 0) return;
    setF({ grainType, quantityKg: String(quantityKg), remarks: 'Monthly allocation net of current FPS stock', fpsId: String(shop.id) });
    setTab('transfers');
  }

  async function submit(e) {
    e.preventDefault();
    const body = { grainType: f.grainType, quantityKg: Number(f.quantityKg), remarks: f.remarks || null };
    try {
      if (tab === 'transfers') await api.post(`${base}/transfers`, { ...body, fpsId: Number(f.fpsId) });
      else await api.post(`${base}/${tab}`, body);
      setFlash({ type: 'success', text: 'Recorded.' }); setF({ ...blank, grainType: f.grainType }); setRefresh((n) => n + 1);
    } catch (err) { setFlash({ type: 'danger', text: errMsg(err) }); }
  }

  const titles = { receipts: 'Receive stock', transfers: 'Transfer to shop', damage: 'Report damage' };
  if (isAdmin && !id && !warehouses.loading && !warehouses.error)
    return <div className="alert alert-warning">Create a warehouse before recording stock movements.</div>;

  return (
    <>
      <Flash flash={flash} onClose={() => setFlash(null)} />
      {isAdmin && (
        <Panel title="Select warehouse" icon="bi-building" className="mb-3">
          {warehouses.loading ? <Loading /> : <ErrorBox error={warehouses.error} />}
          {warehouses.data?.length > 0 && (
            <select className="form-select" value={id || ''} onChange={(e) => setSelectedWarehouseId(e.target.value)} aria-label="Warehouse">
              {warehouses.data.map((warehouse) => <option key={warehouse.id} value={warehouse.id}>{warehouse.name} · {warehouse.district}</option>)}
            </select>
          )}
          {warehouses.data?.length === 0 && !warehouses.loading && !warehouses.error && <Empty text="No warehouses have been configured." />}
        </Panel>
      )}
      {id && (
        <Panel title="Monthly FPS restock suggestions" icon="bi-arrow-left-right" className="mb-3">
          <p className="small text-muted mb-2">Each row represents one FPS village/shop served by this warehouse. The planner subtracts current FPS stock from eligible cards' monthly entitlement, so uncollected grain reduces the next suggestion. "Sent this month" and "Sent total" show completed warehouse-to-shop transfers; submit a suggested quantity below to record a new shipment.</p>
          {shops.loading && !shops.data ? <Loading /> : <ErrorBox error={shops.error} />}
          {shops.data && (shops.data.length === 0 ? <Empty text="No shops are assigned to this warehouse." /> : (
            <div className="table-responsive">
              <table className="table table-sm align-middle gg-table">
                <thead><tr><th>Village / FPS dealer</th><th>Rice need</th><th>Rice at shop</th><th>Rice sent this month</th><th>Rice to send</th><th>Wheat need</th><th>Wheat at shop</th><th>Wheat sent this month</th><th>Wheat to send</th></tr></thead>
                <tbody>{shops.data.map((shop) => (
                  <tr key={shop.id}>
                    <td><strong>{shop.shopCode} · {shop.name}</strong><div className="small text-muted">{[shop.address, shop.district].filter(Boolean).join(', ')}</div></td>
                    <td>{kg(shop.monthlyRiceRequirementKg)}</td><td>{kg(shop.riceStockKg)}</td>
                    <td>{kg(shop.riceSentThisMonthKg)}<div className="small text-muted">Total: {kg(shop.riceSentTotalKg)}</div></td>
                    <td>{kg(shop.suggestedRiceKg)} <button className="btn btn-sm btn-outline-success ms-1" disabled={Number(shop.suggestedRiceKg) <= 0} onClick={() => useSuggestedTransfer(shop, 'RICE', shop.suggestedRiceKg)}>Use</button></td>
                    <td>{kg(shop.monthlyWheatRequirementKg)}</td><td>{kg(shop.wheatStockKg)}</td>
                    <td>{kg(shop.wheatSentThisMonthKg)}<div className="small text-muted">Total: {kg(shop.wheatSentTotalKg)}</div></td>
                    <td>{kg(shop.suggestedWheatKg)} <button className="btn btn-sm btn-outline-success ms-1" disabled={Number(shop.suggestedWheatKg) <= 0} onClick={() => useSuggestedTransfer(shop, 'WHEAT', shop.suggestedWheatKg)}>Use</button></td>
                  </tr>
                ))}</tbody>
              </table>
            </div>
          ))}
        </Panel>
      )}
      {id && <Panel title="Record stock movement" icon="bi-plus-slash-minus">
        <ul className="nav nav-pills mb-3">
          {Object.entries(titles).map(([k, v]) => <li className="nav-item" key={k}><button className={`nav-link ${tab === k ? 'active' : ''}`} onClick={() => setTab(k)}>{v}</button></li>)}
        </ul>
        <form onSubmit={submit} className="row g-2">
          {tab === 'transfers' && (
            <div className="col-md-3"><select className="form-select" value={f.fpsId} onChange={set('fpsId')} required>
              <option value="">Shop...</option>{(shops.data || []).map((s) => <option key={s.id} value={s.id}>{s.shopCode} - {s.name}</option>)}
            </select></div>
          )}
          <div className="col-6 col-md-2"><select className="form-select" value={f.grainType} onChange={set('grainType')}>{GRAINS.map((g) => <option key={g}>{g}</option>)}</select></div>
          <div className="col-6 col-md-2"><input type="number" min="0.01" step="0.01" className="form-control" placeholder="Kg" value={f.quantityKg} onChange={set('quantityKg')} required /></div>
          <div className="col-md-3"><input className="form-control" placeholder="Remarks (optional)" maxLength={255} value={f.remarks} onChange={set('remarks')} /></div>
          <div className="col-md-2"><button className="btn btn-success w-100">{titles[tab]}</button></div>
        </form>
        <div className="small text-muted mt-2">Stock is never edited directly: each entry is added to the ledger below, and the balance is calculated from it. A suggested transfer fills in the shop and quantity; submit it here to record the movement.</div>
      </Panel>}
      {id && <div className="mt-3"><StockPanel base={base} refreshKey={refresh} /></div>}
    </>
  );
}
