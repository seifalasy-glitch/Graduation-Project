import { useState } from "react";
import "./CourseTabs.css";
import Icon from "../Shared_components/Icon";
import { addMaterial, deleteMaterial } from "../../learningApi";
import { CourseMaterial } from "../../types";
import { shortDate } from "../../format";

type CourseMaterialsProps = {
  offeringId: number;
  materials: CourseMaterial[];
  onChange: (materials: CourseMaterial[]) => void;
};

function CourseMaterials({
  offeringId,
  materials,
  onChange,
}: CourseMaterialsProps) {
  const [uploading, setUploading] = useState("");
  const [drag, setDrag] = useState(false);
  const [error, setError] = useState("");

  async function upload(file: File) {
    setUploading(file.name);
    setError("");
    try {
      let kind = "Lecture";
      if (file.name.toLowerCase().includes("syllabus")) {
        kind = "Syllabus";
      }
      const saved = await addMaterial({
        offering_id: offeringId,
        file_name: file.name,
        lecture_no: 7,
        title: file.name.replace(".pdf", ""),
        approved_at: new Date().toISOString(),
        kind: kind,
        size: (file.size / 1024 / 1024).toFixed(1) + " MB",
        ai_state: "processing",
        chunks: 0,
        ai_error: "",
        in_ai: false,
      });
      onChange([...materials, saved]);
    } catch (err) {
      setError("Couldn't upload the file, try again.");
    }
    setUploading("");
  }

  function handlePick(e: React.ChangeEvent<HTMLInputElement>) {
    if (e.target.files && e.target.files.length > 0) {
      upload(e.target.files[0]);
    }
    e.target.value = "";
  }

  function handleDrop(e: React.DragEvent<HTMLLabelElement>) {
    e.preventDefault();
    setDrag(false);
    if (e.dataTransfer.files.length > 0) {
      upload(e.dataTransfer.files[0]);
    }
  }

  async function handleRemove(id: number) {
    try {
      await deleteMaterial(id);
      onChange(materials.filter((m) => m.id !== id));
    } catch (err) {
      setError("Couldn't remove the file, try again.");
    }
  }

  const weeks: number[] = [];
  materials.forEach((m) => {
    if (!weeks.includes(m.lecture_no)) {
      weeks.push(m.lecture_no);
    }
  });
  weeks.sort((a, b) => a - b);

  return (
    <section className="ctab">
      <label
        className={drag ? "ctab-drop over" : "ctab-drop"}
        onDragOver={(e) => {
          e.preventDefault();
          setDrag(true);
        }}
        onDragLeave={() => setDrag(false)}
        onDrop={handleDrop}
      >
        <span className="ctab-drop-icon">
          <Icon name="upload" />
        </span>
        <span className="ctab-drop-text">
          <b>
            {uploading !== ""
              ? "Uploading " + uploading + "…"
              : "Upload lecture PDFs or syllabus"}
          </b>
          <span>
            Drag & drop or click to browse · PDF up to 50 MB · Added to Week 7
          </span>
        </span>
        <input
          className="hidden-input"
          type="file"
          accept=".pdf"
          onChange={handlePick}
        />
      </label>

      {error !== "" && <p className="msg-error">{error}</p>}

      {weeks.map((week) => (
        <div key={week} className="ctab-week">
          <h3 className="ctab-week-title">
            {week === 0 ? "Course documents" : "Week " + week}
          </h3>
          <ul className="ctab-files">
            {materials
              .filter((m) => m.lecture_no === week)
              .map((m) => (
                <li key={m.id} className="ctab-file">
                  <span className="ctab-pdf">PDF</span>
                  <span className="ctab-file-text">
                    <b>{m.file_name}</b>
                    <span>
                      {m.size} · {shortDate(m.approved_at)}
                    </span>
                  </span>
                  <span className="ctab-file-right">
                    <span className="pill">{m.kind}</span>
                    <button className="ctab-icon-btn">
                      <Icon name="download" />
                    </button>
                    <button
                      className="ctab-remove"
                      onClick={() => handleRemove(m.id)}
                    >
                      Remove
                    </button>
                  </span>
                </li>
              ))}
          </ul>
        </div>
      ))}
    </section>
  );
}

export default CourseMaterials;
