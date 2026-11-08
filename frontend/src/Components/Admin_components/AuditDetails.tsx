import Icon from "../Shared_components/Icon";
import { AuditEvent, AuditValue } from "../../types";

type AuditDetailsProps = {
  event: AuditEvent;
  actor: { name: string; role: string };
  onClose: () => void;
};

function showValue(value: AuditValue | undefined) {
  if (value === undefined) {
    return "—";
  }
  if (value === null) {
    return "null";
  }
  return String(value);
}

function AuditDetails({ event, actor, onClose }: AuditDetailsProps) {
  const keys: string[] = [];
  [event.before_json, event.after_json].forEach((data) => {
    if (data !== null) {
      Object.keys(data).forEach((key) => {
        if (!keys.includes(key)) {
          keys.push(key);
        }
      });
    }
  });

  const changed = keys.filter(
    (key) =>
      event.before_json === null ||
      event.after_json === null ||
      showValue(event.before_json[key]) !== showValue(event.after_json[key]),
  );

  function renderSide(
    title: string,
    data: Record<string, AuditValue> | null,
    tone: string,
  ) {
    return (
      <div className="audit-side">
        <p className="audit-side-title">
          <span className={"audit-side-dot " + tone} />
          {title}
        </p>
        {data === null ? (
          <p className="audit-side-empty">
            {tone === "red" ? "Record did not exist" : "Record removed"}
          </p>
        ) : (
          <dl className="audit-side-list">
            {keys.map((key) => (
              <div key={key} className="audit-side-row">
                <dt className="audit-side-key">{key}</dt>
                <dd className="audit-side-value">
                  <span
                    className={
                      changed.includes(key)
                        ? "audit-value " + tone
                        : "audit-value"
                    }
                  >
                    {showValue(data[key])}
                  </span>
                </dd>
              </div>
            ))}
          </dl>
        )}
      </div>
    );
  }

  return (
    <div>
      <div className="overlay" onClick={onClose} />
      <aside className="drawer audit-drawer">
        <div className="audit-drawer-head">
          <p className="audit-small">
            EVT-{event.id} · {event.created_at.replace("T", " ").slice(0, 19)}
          </p>
          <h2 className="audit-drawer-title">{event.action}</h2>
          <p className="audit-drawer-sub">
            {actor.name} ({actor.role}) → {event.target_label}
          </p>
          <button className="modal-close" onClick={onClose}>
            <Icon name="x" />
          </button>
        </div>
        <div className="audit-drawer-body">
          <span className="audit-changed">
            {changed.length} field{changed.length === 1 ? "" : "s"} changed
          </span>
          {changed.map((key) => (
            <span key={key} className="audit-changed-key">
              {key}
            </span>
          ))}
          <div className="audit-sides">
            {renderSide("Before State", event.before_json, "red")}
            {renderSide("After State", event.after_json, "green")}
          </div>
          <div className="audit-meta">
            <div className="audit-meta-item">
              <p className="subtle">Entity type</p>
              <p className="audit-meta-value">{event.target_type}</p>
            </div>
            <div className="audit-meta-item">
              <p className="subtle">Source IP</p>
              <p className="audit-meta-value">{event.ip}</p>
            </div>
            <div className="audit-meta-item">
              <p className="subtle">Integrity</p>
              <p className="audit-meta-value">Signed · verified</p>
            </div>
          </div>
        </div>
        <div className="audit-drawer-foot">
          <button className="btn-ghost" onClick={onClose}>
            Close
          </button>
          <button className="btn-primary">Export event JSON</button>
        </div>
      </aside>
    </div>
  );
}

export default AuditDetails;
