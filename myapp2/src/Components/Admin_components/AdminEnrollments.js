import { useState, useEffect } from "react";
import "../Shared_components/Pages.css";
import "./AdminCourses.css";
import "./AdminEnrollments.css";
import SearchBox from "../Shared_components/SearchBox";
import TermBadge from "../Shared_components/TermBadge";
import {
  getCourses,
  getDoctors,
  getAssignments,
  addAssignment,
  deleteAssignment,
} from "../../coursesApi";

const terms = ["winter", "spring", "summer"];

function AdminEnrollments() {
  const [courses, setCourses] = useState([]);
  const [doctors, setDoctors] = useState([]);
  const [assignments, setAssignments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const [view, setView] = useState("list");
  const [search, setSearch] = useState("");
  const [selectedCourse, setSelectedCourse] = useState(null);
  const [doctorId, setDoctorId] = useState("");
  const [term, setTerm] = useState("");
  const [formError, setFormError] = useState("");

  useEffect(() => {
    async function loadData() {
      try {
        const coursesData = await getCourses();
        const doctorsData = await getDoctors();
        const assignmentsData = await getAssignments();
        setCourses(coursesData);
        setDoctors(doctorsData);
        setAssignments(assignmentsData);
      } catch (err) {
        setError("Can't load data. Did you run: npm run server ?");
      }
      setLoading(false);
    }
    loadData();
  }, []);

  function openCoursesList() {
    setSuccess("");
    setSearch("");
    setView("courses");
  }

  function chooseCourse(course) {
    setSelectedCourse(course);
    setDoctorId("");
    setTerm("");
    setFormError("");
    setView("assign");
  }

  function backToList() {
    setSelectedCourse(null);
    setView("list");
  }

  async function handleAssign(e) {
    e.preventDefault();
    setFormError("");

    if (doctorId === "") {
      setFormError("Please choose a doctor");
      return;
    }
    if (term === "") {
      setFormError("Please choose a term: winter, spring or summer");
      return;
    }

    const alreadyAssigned = assignments.find(
      (item) =>
        item.courseId === selectedCourse.id &&
        item.doctorId === Number(doctorId) &&
        item.term === term,
    );
    if (alreadyAssigned) {
      setFormError("This doctor already has this course in this term");
      return;
    }

    try {
      const newAssignment = await addAssignment({
        courseId: selectedCourse.id,
        doctorId: Number(doctorId),
        term: term,
      });
      setAssignments([...assignments, newAssignment]);
      setSuccess(`${selectedCourse.name} was assigned successfully`);
      backToList();
    } catch (err) {
      setFormError("Something went wrong, try again");
    }
  }

  async function handleRemove(assignmentId) {
    setError("");
    setSuccess("");
    try {
      await deleteAssignment(assignmentId);
      setAssignments(assignments.filter((item) => item.id !== assignmentId));
    } catch (err) {
      setError("Couldn't remove it, try again");
    }
  }

  function getCourse(courseId) {
    return courses.find((course) => course.id === courseId);
  }

  function getDoctorName(id) {
    const doctor = doctors.find((item) => item.id === id);
    if (doctor === undefined) {
      return "Unknown doctor";
    }
    return "Dr. " + doctor.name;
  }

  const text = search.toLowerCase();
  const filteredCourses = courses.filter(
    (course) =>
      course.name.toLowerCase().includes(text) ||
      course.code.toLowerCase().includes(text),
  );

  const visibleAssignments = assignments.filter(
    (item) => getCourse(item.courseId) !== undefined,
  );

  if (loading) {
    return <p className="page-empty">Loading...</p>;
  }

  if (view === "courses") {
    return (
      <div>
        <div className="page-header">
          <h1 className="page-header-title">Choose a course</h1>
          <SearchBox
            value={search}
            onChange={setSearch}
            placeholder="Search by name or code"
          />
        </div>

        <button className="enroll-back-btn" onClick={backToList}>
          ← Back
        </button>

        {filteredCourses.length === 0 && (
          <p className="page-empty">No courses found.</p>
        )}

        <ul className="pick-list">
          {filteredCourses.map((course) => (
            <li
              key={course.id}
              className="pick-item"
              onClick={() => chooseCourse(course)}
            >
              <span className="pick-code">{course.code}</span>
              <span className="pick-name">{course.name}</span>
              <span className="pick-hours">{course.hours} hours</span>
            </li>
          ))}
        </ul>
      </div>
    );
  }

  if (view === "assign") {
    return (
      <div>
        <h1 className="coming-soon">Assign course</h1>

        <button className="enroll-back-btn" onClick={() => setView("courses")}>
          ← Back to courses
        </button>

        <form className="assign-form" onSubmit={handleAssign}>
          <p className="assign-course">
            <span className="pick-code">{selectedCourse.code}</span>
            {selectedCourse.name}
          </p>

          <label className="assign-label">Doctor</label>
          <select
            className="assign-select"
            value={doctorId}
            onChange={(e) => setDoctorId(e.target.value)}
          >
            <option value="">Choose a doctor</option>
            {doctors.map((doctor) => (
              <option key={doctor.id} value={doctor.id}>
                Dr. {doctor.name}
              </option>
            ))}
          </select>

          <label className="assign-label">Term</label>
          <div className="assign-terms">
            {terms.map((item) => (
              <label
                key={item}
                className={
                  term === item ? "assign-term checked" : "assign-term"
                }
              >
                <input
                  type="radio"
                  name="term"
                  value={item}
                  checked={term === item}
                  onChange={(e) => setTerm(e.target.value)}
                />
                {item}
              </label>
            ))}
          </div>

          {formError !== "" && <p className="page-msg error">{formError}</p>}

          <button className="assign-btn" type="submit">
            Assign
          </button>
        </form>
      </div>
    );
  }

  return (
    <div>
      <h1 className="coming-soon">Enrollments</h1>
      <p className="coming-soon-text">
        Assign every course to a doctor and a term.
      </p>

      <button className="assign-open-btn" onClick={openCoursesList}>
        <span className="add-course-plus">+</span>Assign
      </button>

      {success !== "" && <p className="page-msg success">{success}</p>}
      {error !== "" && <p className="page-msg error">{error}</p>}

      {visibleAssignments.length === 0 && (
        <p className="page-empty">No course is assigned yet.</p>
      )}

      {visibleAssignments.length > 0 && (
        <table className="courses-table">
          <thead>
            <tr>
              <th>Code</th>
              <th>Course name</th>
              <th>Doctor</th>
              <th>Term</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {visibleAssignments.map((item) => (
              <tr key={item.id}>
                <td className="courses-table-code">
                  {getCourse(item.courseId).code}
                </td>
                <td>{getCourse(item.courseId).name}</td>
                <td>{getDoctorName(item.doctorId)}</td>
                <td>
                  <TermBadge term={item.term} />
                </td>
                <td className="courses-table-action">
                  <button
                    className="delete-btn"
                    onClick={() => handleRemove(item.id)}
                  >
                    Remove
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

export default AdminEnrollments;
