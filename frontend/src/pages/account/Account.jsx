import { useState } from 'react';
import api, { errMsg } from '../../api';
import { useAuth, ROLE_LABEL } from '../../auth';
import { Panel, Flash } from '../../components/ui';

export default function Account() {
  const { user } = useAuth();
  const [f, setF] = useState({ currentPassword: '', newPassword: '', confirm: '' });
  const [flash, setFlash] = useState(null);

  async function submit(e) {
    e.preventDefault();
    if (f.newPassword !== f.confirm) { setFlash({ type: 'warning', text: 'The new passwords do not match.' }); return; }
    try {
      await api.post('/api/users/me/password', { currentPassword: f.currentPassword, newPassword: f.newPassword });
      setFlash({ type: 'success', text: 'Password changed.' });
      setF({ currentPassword: '', newPassword: '', confirm: '' });
    } catch (err) { setFlash({ type: 'danger', text: errMsg(err) }); }
  }

  return (
    <div className="row">
      <div className="col-12 col-lg-6">
        <Flash flash={flash} onClose={() => setFlash(null)} />
        <Panel title="My account" icon="bi-person-circle">
          <div className="mb-3"><strong>{user.fullName}</strong><div className="text-muted">{user.username} · {ROLE_LABEL[user.role]}</div></div>
          <h6>Change password</h6>
          <form onSubmit={submit}>
            <input type="password" className="form-control mb-2" placeholder="Current password" value={f.currentPassword} onChange={(e) => setF({ ...f, currentPassword: e.target.value })} required />
            <input type="password" className="form-control mb-2" placeholder="New password (min 8 characters)" minLength={8} maxLength={72} value={f.newPassword} onChange={(e) => setF({ ...f, newPassword: e.target.value })} required />
            <input type="password" className="form-control mb-3" placeholder="Repeat new password" value={f.confirm} onChange={(e) => setF({ ...f, confirm: e.target.value })} required />
            <button className="btn btn-success">Update password</button>
          </form>
        </Panel>
      </div>
    </div>
  );
}
