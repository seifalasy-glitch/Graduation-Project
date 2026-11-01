import { useState } from "react";
import Icon from "../Shared_components/Icon";
import Switch from "../Shared_components/Switch";

type AddProgramModalProps = {
  existing: string[];
  onClose: () => void;
  onSave: (code: string, name: string, active: boolean) => void;
};

function AddProgramModal({ existing, onClose, onSave }: AddProgramModalProps) {
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [active, setActive] = useState(true);

  const cleanCode = code.trim().toUpperCase();
  const duplicate = existing.includes(cleanCode);
  const valid = cleanCode.length >= 2 && name.trim().length >= 3 && !duplicate;

  function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (valid) {
      onSave(cleanCode, name.trim(), active);
    }
  }

  return (
    <div>
      <div className="overlay" onClick={onClose} />
      <form className="modal" onSubmit={handleSubmit}>
        <div className="modal-head">
          <h2 className="modal-title">Add program</h2>
          <p className="section-desc">Create a new major or degree program.</p>
          <button type="button" className="modal-close" onClick={onClose}>
            <Icon name="x" />
          </button>
        </div>
        <div className="modal-body">
          <label className="field-label">Program Code</label>
          <input
            className="input programs-mono"
            value={code}
            placeholder="e.g. BSC-AI"
            onChange={(e) => setCode(e.target.value)}
          />
          {duplicate && (
            <p className="error-text">
              A program with this code already exists.
            </p>
          )}
          <label className="field-label programs-gap">Program Name</label>
          <input
            className="input"
            value={name}
            placeholder="e.g. BSc Artificial Intelligence"
            onChange={(e) => setName(e.target.value)}
          />
          <div className="programs-active-box">
            <p className="programs-active-title">Active</p>
            <p className="programs-active-sub">
              Open for new student enrollment
            </p>
            <span className="programs-active-switch">
              <Switch on={active} onChange={setActive} />
            </span>
          </div>
        </div>
        <div className="modal-foot">
          <button type="button" className="btn-ghost" onClick={onClose}>
            Cancel
          </button>
          <button className="btn-primary" disabled={!valid}>
            Create program
          </button>
        </div>
      </form>
    </div>
  );
}

export default AddProgramModal;
