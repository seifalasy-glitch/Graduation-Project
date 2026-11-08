import { useState } from "react";
import "./CourseTabs.css";
import Icon from "../Shared_components/Icon";
import Switch from "../Shared_components/Switch";
import { updateMaterial } from "../../learningApi";
import { CourseMaterial } from "../../types";
import { shortDate } from "../../format";

type KnowledgeBaseProps = {
  offeringId: number;
  materials: CourseMaterial[];
  onChange: (materials: CourseMaterial[]) => void;
};

function KnowledgeBase({
  offeringId,
  materials,
  onChange,
}: KnowledgeBaseProps) {
  const [error, setError] = useState("");

  async function handleToggle(material: CourseMaterial, value: boolean) {
    try {
      const updated = await updateMaterial(material.id, { in_ai: value });
      onChange(materials.map((m) => (m.id === material.id ? updated : m)));
    } catch (err) {
      setError("Couldn't update the file, try again.");
    }
  }

  const active = materials.filter((m) => m.in_ai && m.ai_state === "parsed");
  let chunks = 0;
  active.forEach((m) => {
    chunks = chunks + m.chunks;
  });

  const stats = [
    {
      label: "Files in AI context",
      value: active.length + " / " + materials.length,
    },
    { label: "Knowledge chunks", value: String(chunks) },
    { label: "Last re-index", value: "Today · 08:14" },
  ];

  return (
    <section className="ctab" key={offeringId}>
      <div className="ctab-head">
        <h2 className="ctab-title">AI Knowledge Base</h2>
        <p className="ctab-desc">
          Choose exactly which materials the Course AI Assistant can use when
          answering students. Changes apply to new conversations right away.
        </p>
        <button className="btn-ghost ctab-head-btn">
          <Icon name="upload" />
          Upload file
        </button>
      </div>

      {error !== "" && <p className="msg-error">{error}</p>}

      <div className="kb-stats">
        {stats.map((stat) => (
          <div key={stat.label} className="card kb-stat">
            <p className="ctab-small">{stat.label}</p>
            <p className="kb-stat-value">{stat.value}</p>
          </div>
        ))}
      </div>

      <ul className="card kb-list">
        {materials.map((m) => {
          const usable = m.ai_state === "parsed";
          return (
            <li key={m.id} className="kb-row">
              <span className="kb-file-icon">
                <Icon name="file" />
              </span>
              <div className="kb-text">
                <p className={usable ? "kb-name" : "kb-name off"}>
                  {m.file_name}
                </p>
                <p className="ctab-small">
                  {m.kind} · {m.size} · Added {shortDate(m.approved_at)}
                  {m.ai_state === "parsed" && (
                    <span className="kb-badge parsed">
                      <Icon name="spark" size={12} /> Parsed into {m.chunks}{" "}
                      knowledge chunks
                    </span>
                  )}
                  {m.ai_state === "processing" && (
                    <span className="kb-badge">● Processing…</span>
                  )}
                  {m.ai_state === "error" && (
                    <span className="kb-badge error">
                      <Icon name="alert" size={12} /> {m.ai_error}
                    </span>
                  )}
                </p>
              </div>
              <span className="kb-toggle">
                Include in AI Context
                {usable ? (
                  <Switch on={m.in_ai} onChange={(v) => handleToggle(m, v)} />
                ) : (
                  <span className="kb-switch-off" />
                )}
              </span>
            </li>
          );
        })}
      </ul>
      <p className="ctab-foot">
        Files that failed to parse or are still processing can't be included
        until they're ready.
      </p>
    </section>
  );
}

export default KnowledgeBase;
