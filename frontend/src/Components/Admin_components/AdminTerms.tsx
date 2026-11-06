import { useEffect, useState } from "react";
import "./AdminTerms.css";
import PageHead from "../Shared_components/PageHead";
import Toast from "../Shared_components/Toast";
import Icon from "../Shared_components/Icon";
import {
  addOffering,
  addTerm,
  getCourses,
  getOfferings,
  getTerms,
} from "../../academicApi";
import { AcademicTerm, Course, CourseOffering } from "../../types";
import { longDate, shortDate, termName } from "../../format";

const termTypes = ["fall", "spring", "summer"];

function AdminTerms() {
  const [courses, setCourses] = useState<Course[]>([]);
  const [terms, setTerms] = useState<AcademicTerm[]>([]);
  const [offerings, setOfferings] = useState<CourseOffering[]>([]);
  const [error, setError] = useState("");

  const [name, setName] = useState("");
  const [start, setStart] = useState("");
  const [end, setEnd] = useState("");
  const [search, setSearch] = useState("");
  const [picked, setPicked] = useState<number[]>([]);
  const [toast, setToast] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    async function loadData() {
      try {
        setCourses(await getCourses());
        setTerms(await getTerms());
        setOfferings(await getOfferings());
      } catch (err) {
        setError("Can't load terms. Did you run: npm run server ?");
      }
    }
    loadData();
  }, []);

  const words = name.trim().split(" ");
  const termType = words[0].toLowerCase();
  const year = Number(words[1]);
  const nameOk =
    words.length === 2 && termTypes.includes(termType) && year > 2000;
  const badRange = start !== "" && end !== "" && end <= start;
  const ready =
    nameOk && start !== "" && end !== "" && !badRange && picked.length > 0;

  const text = search.toLowerCase();
  const visible = courses.filter((course) =>
    (course.course_code + course.title).toLowerCase().includes(text),
  );

  function toggle(courseId: number) {
    if (picked.includes(courseId)) {
      setPicked(picked.filter((id) => id !== courseId));
    } else {
      setPicked([...picked, courseId]);
    }
  }

  function getCode(courseId: number) {
    const course = courses.find((item) => item.id === courseId);
    if (course === undefined) {
      return "";
    }
    return course.course_code;
  }

  function reset() {
    setName("");
    setStart("");
    setEnd("");
    setPicked([]);
    setSearch("");
  }

  async function handleSave() {
    setSaving(true);
    try {
      let academicYear = year - 1 + "/" + year;
      if (termType === "fall") {
        academicYear = year + "/" + (year + 1);
      }
      const term = await addTerm({
        academic_year: academicYear,
        term_type: termType,
        start_date: start,
        end_date: end,
        add_drop_end: start,
        exams_start: end,
        status: "Scheduled",
      });
      const added: CourseOffering[] = [];
      for (const courseId of picked) {
        const offering = await addOffering({
          course_id: courseId,
          term_id: term.id,
          section: "A",
          capacity: 60,
          coursework_max: 40,
          days: [],
          start_hour: 0,
          end_hour: 0,
          room_id: 0,
          emoji: "📘",
          syllabus_progress: 0,
        });
        added.push(offering);
      }
      setTerms([...terms, term]);
      setOfferings([...offerings, ...added]);
      setToast(name.trim() + " saved with its course structure.");
      reset();
    } catch (err) {
      setError("Couldn't save the term, try again.");
    }
    setSaving(false);
  }

  function getStatusClass(status: string) {
    if (status === "Active") {
      return "pill purple terms-status";
    }
    return "pill terms-status";
  }

  return (
    <div>
      <PageHead
        eyebrow="Admin · Academic structure"
        title="Academic Terms"
        desc="Define a term's calendar and the courses offered within it."
      />

      {toast !== "" && (
        <div className="terms-gap">
          <Toast text={toast} onClose={() => setToast("")} />
        </div>
      )}
      {error !== "" && <p className="msg-error">{error}</p>}

      <section className="card terms-form">
        <div className="terms-form-head">
          <h2 className="terms-h2">Add Academic Term</h2>
        </div>
        <div className="terms-form-body">
          <label className="field-label">Term Name</label>
          <input
            className="input"
            value={name}
            placeholder="e.g., Fall 2026"
            onChange={(e) => setName(e.target.value)}
          />
          {name !== "" && !nameOk && (
            <p className="terms-bad">
              Use a season and a year, like Fall 2026 or Spring 2027.
            </p>
          )}

          <div className="terms-dates">
            <div className="terms-date">
              <label className="field-label">Start Date</label>
              <input
                className="input"
                type="date"
                value={start}
                onChange={(e) => setStart(e.target.value)}
              />
            </div>
            <div className="terms-date right">
              <label className="field-label">End Date</label>
              <input
                className={badRange ? "input bad" : "input"}
                type="date"
                value={end}
                min={start}
                onChange={(e) => setEnd(e.target.value)}
              />
            </div>
          </div>
          {badRange && (
            <p className="terms-bad">
              <Icon name="alert" /> End date must be after the start date.
            </p>
          )}

          <label className="field-label terms-courses-label">
            Add Courses
            <span className="field-hint">{picked.length} added</span>
          </label>
          {picked.length > 0 && (
            <div className="terms-chips">
              {picked.map((courseId) => (
                <span key={courseId} className="chip">
                  {getCode(courseId)}
                  <button className="chip-x" onClick={() => toggle(courseId)}>
                    ×
                  </button>
                </span>
              ))}
            </div>
          )}
          <div className="terms-catalog">
            <div className="terms-catalog-search">
              <Icon name="search" />
              <input
                className="terms-catalog-input"
                value={search}
                placeholder="Search course catalog"
                onChange={(e) => setSearch(e.target.value)}
              />
            </div>
            <div className="terms-catalog-list">
              {visible.map((course) => {
                const on = picked.includes(course.id);
                return (
                  <button
                    key={course.id}
                    className={on ? "terms-course on" : "terms-course"}
                    onClick={() => toggle(course.id)}
                  >
                    <span className={on ? "terms-box on" : "terms-box"}>
                      {on ? "✓" : ""}
                    </span>
                    <b className="terms-code">{course.course_code}</b>
                    <span className="muted">{course.title}</span>
                  </button>
                );
              })}
            </div>
          </div>
        </div>
        <div className="terms-form-foot">
          <button className="btn-ghost" onClick={reset}>
            Clear
          </button>
          <button
            className="btn-primary"
            disabled={!ready || saving}
            onClick={handleSave}
          >
            <Icon name="db" />
            Save Academic Structure
          </button>
        </div>
      </section>

      <section className="terms-existing">
        <h2 className="terms-h2">Existing terms</h2>
        <ul className="card terms-list">
          {terms.map((term) => (
            <li key={term.id} className="terms-row">
              <span className="subtle">
                <Icon name="calendar" />
              </span>
              <b className="terms-name">
                {termName(term.term_type, term.academic_year)}
              </b>
              <span className="terms-range">
                {shortDate(term.start_date)} – {longDate(term.end_date)}
              </span>
              <span className="terms-count">
                {
                  offerings.filter((offering) => offering.term_id === term.id)
                    .length
                }{" "}
                courses
              </span>
              <span className={getStatusClass(term.status)}>{term.status}</span>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}

export default AdminTerms;
