import { useEffect, useState } from "react";
import "./AdminUsers.css";
import PageHead from "../Shared_components/PageHead";
import Toast from "../Shared_components/Toast";
import Icon from "../Shared_components/Icon";
import {
  addAuditEvent,
  addUser,
  getSampleRoster,
  getUsers,
} from "../../academicApi";
import { Role, RosterRow, User } from "../../types";
import { shortDate } from "../../format";

const roleNames: Record<Role, string> = {
  student: "Student",
  doctor: "Doctor",
  admin: "Admin",
};

function AdminUsers() {
  const [stage, setStage] = useState("idle");
  const [fileName, setFileName] = useState("");
  const [drag, setDrag] = useState(false);
  const [saving, setSaving] = useState(false);
  const [toast, setToast] = useState("");
  const [users, setUsers] = useState<User[]>([]);
  const [roster, setRoster] = useState<RosterRow[]>([]);
  const [error, setError] = useState("");

  useEffect(() => {
    async function loadUsers() {
      try {
        setUsers(await getUsers());
      } catch (err) {
        setError("Can't load users. Did you run: npm run server ?");
      }
    }
    loadUsers();
  }, []);

  // The file itself is not read (ASSUMPTION A21): we keep its name and show
  // the rows from sample_roster in mock-data/db.json as if they came from it.
  async function loadFile(name: string) {
    setFileName(name);
    setToast("");
    setError("");
    try {
      setRoster(await getSampleRoster());
      setStage("uploaded");
    } catch (err) {
      setError("Can't load the sample rows. Did you run: npm run server ?");
    }
  }

  function handlePick(e: React.ChangeEvent<HTMLInputElement>) {
    if (e.target.files && e.target.files.length > 0) {
      loadFile(e.target.files[0].name);
    }
    e.target.value = "";
  }

  function handleDrop(e: React.DragEvent<HTMLLabelElement>) {
    e.preventDefault();
    setDrag(false);
    if (e.dataTransfer.files.length > 0) {
      loadFile(e.dataTransfer.files[0].name);
    }
  }

  function reset() {
    setStage("idle");
    setFileName("");
  }

  async function handleSave() {
    setSaving(true);
    try {
      const added: User[] = [];
      for (const row of roster) {
        const saved = await addUser({
          university_id: row.university_id,
          email: row.email,
          role: row.role,
          name: row.full_name,
          password: "123456",
          must_change_password: true,
          department: row.role === "doctor" ? "Computer Science" : "Student",
          active: false,
          created_at: new Date().toISOString(),
        });
        added.push(saved);
      }
      await addAuditEvent({
        actor_user_id: 1,
        target_type: "user",
        target_id: added[0].id,
        action: "Users Imported",
        kind: "create",
        target_label: added.length + " users · " + fileName,
        ip: "10.4.1.5",
        before_json: null,
        after_json: { file: fileName, rows: added.length, status: "Invited" },
        created_at: new Date().toISOString(),
      });
      setUsers([...users, ...added]);
      setToast(
        added.length +
          " users saved to the database. Invitations will be emailed within a few minutes.",
      );
      reset();
    } catch (err) {
      setError("Couldn't save the users, try again.");
    }
    setSaving(false);
  }

  return (
    <div>
      <PageHead
        eyebrow="Admin · People"
        title="User Management"
        desc="Import students, doctors, and admins in bulk, then review before writing to the database."
      />

      {toast !== "" && (
        <div className="users-gap">
          <Toast text={toast} onClose={() => setToast("")} />
        </div>
      )}
      {error !== "" && <p className="msg-error">{error}</p>}

      <section className="card users-import">
        <div className="users-import-head">
          <h2 className="users-h2">Import Users via Excel</h2>
          <p className="users-small">
            Columns: Full Name, Email, Role, Student ID. Max 5,000 rows.
          </p>
          <button className="users-template">Download template</button>
        </div>

        <div className="users-import-body">
          {stage === "idle" && (
            <div>
              <label
                className={drag ? "users-drop over" : "users-drop"}
                onDragOver={(e) => {
                  e.preventDefault();
                  setDrag(true);
                }}
                onDragLeave={() => setDrag(false)}
                onDrop={handleDrop}
              >
                <span className="users-drop-icon">
                  <Icon name="upload" size={24} />
                </span>
                <span className="users-drop-title">
                  Drag and drop your Excel file here
                </span>
                <span className="users-drop-sub">
                  or <b>browse</b> · .xlsx, .xls, .csv
                </span>
                <input
                  className="hidden-input"
                  type="file"
                  accept=".xlsx,.xls,.csv"
                  onChange={handlePick}
                />
              </label>
              <p className="users-samples">
                No file at hand?
                <button
                  className="users-sample"
                  onClick={() => loadFile("fall_roster_2026.xlsx")}
                >
                  Use sample file
                </button>
              </p>
            </div>
          )}

          {stage === "uploaded" && (
            <div>
              <div className="users-ok">
                <Icon name="check" />
                <span className="users-alert-text">
                  <b>{fileName}</b> uploaded · {roster.length} rows ready
                </span>
                <button className="users-replace" onClick={reset}>
                  Replace file
                </button>
              </div>
              <h3 className="users-h3">
                Preview <span className="subtle">· {roster.length} rows</span>
              </h3>
              <div className="table-wrap">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Full name</th>
                      <th>Email</th>
                      <th>Role</th>
                      <th>ID</th>
                    </tr>
                  </thead>
                  <tbody>
                    {roster.map((row) => (
                      <tr key={row.id}>
                        <td className="users-strong">{row.full_name}</td>
                        <td className="muted">{row.email}</td>
                        <td>
                          <span className={"users-role " + row.role}>
                            {roleNames[row.role]}
                          </span>
                        </td>
                        <td className="muted">{row.university_id}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="users-actions">
                <button className="btn-ghost" onClick={reset}>
                  Cancel
                </button>
                <button
                  className="btn-primary"
                  disabled={saving}
                  onClick={handleSave}
                >
                  <Icon name="db" />
                  {saving ? "Saving users…" : "Save Users to Database"}
                </button>
              </div>
            </div>
          )}
        </div>
      </section>

      <section className="users-directory">
        <h2 className="users-h2">
          Directory <span className="users-count">· {users.length} users</span>
        </h2>
        <div className="table-wrap users-table">
          <table className="table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Email</th>
                <th>Role</th>
                <th>Status</th>
                <th>Added</th>
              </tr>
            </thead>
            <tbody>
              {users.map((item) => (
                <tr key={item.id}>
                  <td className="users-strong">{item.name}</td>
                  <td className="muted">{item.email}</td>
                  <td>
                    <span className={"users-role " + item.role}>
                      {roleNames[item.role]}
                    </span>
                  </td>
                  <td className="muted">
                    <span
                      className={
                        item.active ? "users-dot active" : "users-dot invited"
                      }
                    />
                    {item.active ? "Active" : "Invited"}
                  </td>
                  <td className="subtle">{shortDate(item.created_at)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}

export default AdminUsers;
