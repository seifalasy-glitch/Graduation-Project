import { useEffect, useState } from "react";
import "./AdminUsers.css";
import PageHead from "../Shared_components/PageHead";
import Toast from "../Shared_components/Toast";
import Icon from "../Shared_components/Icon";
import { addAuditEvent, addUser, getUsers } from "../../academicApi";
import { Role, User } from "../../types";
import { shortDate } from "../../format";

const errorsLog = [
  { row: 5, col: "Email", value: "omar.k@uni", issue: "Invalid Email Format" },
  {
    row: 9,
    col: "Role",
    value: "Profesor",
    issue: "Unknown role (expected Student, Doctor, Admin)",
  },
  { row: 14, col: "Student ID", value: "—", issue: "Required field is empty" },
  {
    row: 22,
    col: "Email",
    value: "layla@edusync.edu",
    issue: "Duplicate email already in database",
  },
  {
    row: 31,
    col: "Student ID",
    value: "2023-0A41",
    issue: "ID must be 8 digits",
  },
];

const preview: { name: string; email: string; role: Role; id: string }[] = [
  {
    name: "Rami Fawzi",
    email: "rami.fawzi@edusync.edu",
    role: "student",
    id: "20230502",
  },
  {
    name: "Hala Nabil",
    email: "hala.nabil@edusync.edu",
    role: "student",
    id: "20230511",
  },
  {
    name: "Dr. Tarek Saad",
    email: "t.saad@edusync.edu",
    role: "doctor",
    id: "FAC-2088",
  },
  {
    name: "Dina Samir",
    email: "dina.samir@edusync.edu",
    role: "student",
    id: "20230519",
  },
  {
    name: "Youssef Adel",
    email: "youssef.adel@edusync.edu",
    role: "student",
    id: "20230524",
  },
];

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

  function loadFile(name: string) {
    setFileName(name);
    setToast("");
    const lower = name.toLowerCase();
    if (lower.includes("error") || lower.includes("bad")) {
      setStage("errors");
    } else {
      setStage("valid");
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
      for (const row of preview) {
        const saved = await addUser({
          university_id: row.id,
          email: row.email,
          role: row.role,
          name: row.name,
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
                Try a sample:
                <button
                  className="users-sample"
                  onClick={() => loadFile("fall_roster_2026.xlsx")}
                >
                  Valid file
                </button>
                <button
                  className="users-sample"
                  onClick={() => loadFile("roster_with_errors.xlsx")}
                >
                  File with errors
                </button>
              </p>
            </div>
          )}

          {stage === "errors" && (
            <div>
              <div className="users-alert">
                <Icon name="alert" />
                <span className="users-alert-text">
                  <b>
                    {fileName} has {errorsLog.length} validation errors
                  </b>
                  <span className="users-alert-sub">
                    Fix the rows below in your spreadsheet and re-upload. No
                    users were saved.
                  </span>
                </span>
              </div>
              <h3 className="users-h3">Validation Errors</h3>
              <div className="table-wrap">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Row</th>
                      <th>Column</th>
                      <th>Value</th>
                      <th>Issue</th>
                    </tr>
                  </thead>
                  <tbody>
                    {errorsLog.map((row) => (
                      <tr key={row.row}>
                        <td className="users-strong">Row {row.row}</td>
                        <td className="muted">{row.col}</td>
                        <td>
                          <code className="users-code">{row.value}</code>
                        </td>
                        <td className="users-issue">{row.issue}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="users-actions">
                <button className="btn-ghost" onClick={reset}>
                  Cancel
                </button>
                <button className="btn-primary" onClick={reset}>
                  <Icon name="refresh" />
                  Re-upload
                </button>
              </div>
            </div>
          )}

          {stage === "valid" && (
            <div>
              <div className="users-ok">
                <Icon name="check" />
                <span className="users-alert-text">
                  <b>{fileName}</b> passed validation · {preview.length} rows
                  ready
                </span>
                <button className="users-replace" onClick={reset}>
                  Replace file
                </button>
              </div>
              <h3 className="users-h3">
                Preview <span className="subtle">· {preview.length} rows</span>
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
                    {preview.map((row) => (
                      <tr key={row.email}>
                        <td className="users-strong">{row.name}</td>
                        <td className="muted">{row.email}</td>
                        <td>
                          <span className={"users-role " + row.role}>
                            {roleNames[row.role]}
                          </span>
                        </td>
                        <td className="muted">{row.id}</td>
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
