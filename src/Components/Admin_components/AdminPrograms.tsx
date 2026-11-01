import { useEffect, useState } from "react";
import "./AdminPrograms.css";
import Icon from "../Shared_components/Icon";
import Switch from "../Shared_components/Switch";
import AddProgramModal from "./AddProgramModal";
import {
  addProgram,
  getEnrollments,
  getPrograms,
  updateProgram,
} from "../../academicApi";
import { AcademicProgram, Enrollment } from "../../types";

const filters = ["All", "Active", "Inactive"];

function AdminPrograms() {
  const [programs, setPrograms] = useState<AcademicProgram[]>([]);
  const [enrollments, setEnrollments] = useState<Enrollment[]>([]);
  const [filter, setFilter] = useState("All");
  const [showModal, setShowModal] = useState(false);
  const [toast, setToast] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    async function loadData() {
      try {
        setPrograms(await getPrograms());
        setEnrollments(await getEnrollments());
      } catch (err) {
        setError("Can't load programs. Did you run: npm run server ?");
      }
    }
    loadData();
  }, []);

  useEffect(() => {
    if (toast === "") {
      return;
    }
    const timer = setTimeout(() => setToast(""), 2600);
    return () => clearTimeout(timer);
  }, [toast]);

  function countFor(name: string) {
    if (name === "All") {
      return programs.length;
    }
    return programs.filter((p) => p.active === (name === "Active")).length;
  }

  function getStudents(programId: number) {
    const ids: number[] = [];
    enrollments.forEach((enrollment) => {
      if (
        enrollment.program_id === programId &&
        !ids.includes(enrollment.student_user_id)
      ) {
        ids.push(enrollment.student_user_id);
      }
    });
    return ids.length;
  }

  function getCourses(programId: number) {
    const ids: number[] = [];
    enrollments.forEach((enrollment) => {
      if (
        enrollment.program_id === programId &&
        !ids.includes(enrollment.offering_id)
      ) {
        ids.push(enrollment.offering_id);
      }
    });
    return ids.length;
  }

  async function handleToggle(program: AcademicProgram, value: boolean) {
    try {
      const updated = await updateProgram(program.id, { active: value });
      setPrograms(programs.map((p) => (p.id === program.id ? updated : p)));
    } catch (err) {
      setError("Couldn't update the program, try again.");
    }
  }

  async function handleCreate(code: string, name: string, active: boolean) {
    try {
      const created = await addProgram({
        code: code,
        name: name,
        active: active,
        department: "Unassigned faculty",
      });
      setPrograms([created, ...programs]);
      setShowModal(false);
      setToast(name + " added");
    } catch (err) {
      setError("Couldn't create the program, try again.");
    }
  }

  const shown = programs.filter(
    (p) => filter === "All" || p.active === (filter === "Active"),
  );

  let activeStudents = 0;
  programs.forEach((p) => {
    if (p.active) {
      activeStudents = activeStudents + getStudents(p.id);
    }
  });

  return (
    <div>
      <p className="programs-eyebrow">Academic structure</p>
      <div className="programs-head">
        <h1 className="programs-title">Academic Programs</h1>
        <p className="programs-desc">
          Majors and degree programs students can enroll in. Inactive programs
          stay on record but are hidden from new enrollment.
        </p>
        <button
          className="btn-primary programs-add"
          onClick={() => setShowModal(true)}
        >
          + Add Program
        </button>
      </div>

      {error !== "" && <p className="msg-error">{error}</p>}

      <div className="programs-bar">
        <div className="programs-filters">
          {filters.map((name) => (
            <button
              key={name}
              className={
                filter === name ? "programs-filter active" : "programs-filter"
              }
              onClick={() => setFilter(name)}
            >
              {name}{" "}
              <span className="programs-filter-count">{countFor(name)}</span>
            </button>
          ))}
        </div>
        <p className="programs-total">
          {activeStudents} students in active programs
        </p>
      </div>

      <div className="programs-grid">
        {shown.map((program) => (
          <div
            key={program.id}
            className={
              program.active ? "card programs-card" : "card programs-card off"
            }
          >
            <span className="programs-code">{program.code}</span>
            <span className="programs-switch">
              <Switch
                on={program.active}
                onChange={(v) => handleToggle(program, v)}
              />
            </span>
            <p className="programs-name">{program.name}</p>
            <p className="programs-dept">{program.department}</p>
            <p className="programs-foot">
              <span className="programs-stat">
                <Icon name="users" size={14} /> {getStudents(program.id)}{" "}
                students
              </span>
              <span className="programs-stat">
                <Icon name="book" size={14} /> {getCourses(program.id)} courses
              </span>
              <span
                className={
                  program.active ? "programs-state on" : "programs-state"
                }
              >
                {program.active ? "Active" : "Inactive"}
              </span>
            </p>
          </div>
        ))}
        {shown.length === 0 && (
          <p className="programs-empty">No {filter.toLowerCase()} programs.</p>
        )}
      </div>

      {showModal && (
        <AddProgramModal
          existing={programs.map((p) => p.code)}
          onClose={() => setShowModal(false)}
          onSave={handleCreate}
        />
      )}

      {toast !== "" && (
        <div className="programs-toast">
          <span className="programs-toast-icon">
            <Icon name="check" />
          </span>
          {toast}
        </div>
      )}
    </div>
  );
}

export default AdminPrograms;
