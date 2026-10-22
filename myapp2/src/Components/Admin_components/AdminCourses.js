import { useState, useEffect } from "react";
import "../Shared_components/Pages.css";
import "./AdminCourses.css";
import SearchBox from "../Shared_components/SearchBox";
import { getCourses, addCourse, deleteCourse } from "../../coursesApi";

function AdminCourses() {
  const [courses, setCourses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");

  const [showForm, setShowForm] = useState(false);
  const [name, setName] = useState("");
  const [code, setCode] = useState("");
  const [hours, setHours] = useState("");
  const [formError, setFormError] = useState("");

  useEffect(() => {
    async function loadCourses() {
      try {
        const data = await getCourses();
        setCourses(data);
      } catch (err) {
        setError("Can't load courses. Did you run: npm run server ?");
      }
      setLoading(false);
    }
    loadCourses();
  }, []);

  async function handleAdd(e) {
    e.preventDefault();
    setFormError("");

    if (name === "" || code === "" || hours === "") {
      setFormError("Please fill all fields");
      return;
    }
    if (Number(hours) <= 0) {
      setFormError("Hours must be more than 0");
      return;
    }

    const sameCode = courses.find(
      (course) => course.code.toLowerCase() === code.toLowerCase(),
    );
    if (sameCode) {
      setFormError("There is already a course with this code");
      return;
    }

    try {
      const newCourse = await addCourse({
        name: name,
        code: code.toUpperCase(),
        hours: Number(hours),
      });
      setCourses([...courses, newCourse]);
      setName("");
      setCode("");
      setHours("");
      setShowForm(false);
    } catch (err) {
      setFormError("Something went wrong, try again");
    }
  }

  async function handleDelete(courseId) {
    setError("");
    try {
      await deleteCourse(courseId);
      setCourses(courses.filter((course) => course.id !== courseId));
    } catch (err) {
      setError("Couldn't delete the course, try again");
    }
  }

  function handleCancel() {
    setShowForm(false);
    setFormError("");
    setName("");
    setCode("");
    setHours("");
  }

  const text = search.toLowerCase();
  const filteredCourses = courses.filter(
    (course) =>
      course.name.toLowerCase().includes(text) ||
      course.code.toLowerCase().includes(text),
  );

  return (
    <div>
      <div className="page-header">
        <h1 className="page-header-title">Courses</h1>
        <SearchBox
          value={search}
          onChange={setSearch}
          placeholder="Search by name or code"
        />
      </div>

      {!showForm && (
        <button className="add-course-btn" onClick={() => setShowForm(true)}>
          <span className="add-course-plus">+</span>Add new course
        </button>
      )}

      {showForm && (
        <form className="add-course-form" onSubmit={handleAdd}>
          <h2 className="add-course-title">New course</h2>

          <label className="add-course-label">Course name</label>
          <input
            className="add-course-input"
            type="text"
            placeholder="Data Structures"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />

          <label className="add-course-label">Course code</label>
          <input
            className="add-course-input"
            type="text"
            placeholder="CS201"
            value={code}
            onChange={(e) => setCode(e.target.value)}
          />

          <label className="add-course-label">Credit hours</label>
          <input
            className="add-course-input"
            type="number"
            placeholder="3"
            value={hours}
            onChange={(e) => setHours(e.target.value)}
          />

          {formError !== "" && <p className="page-msg error">{formError}</p>}

          <button className="add-course-save" type="submit">
            Add course
          </button>
          <button
            className="add-course-cancel"
            type="button"
            onClick={handleCancel}
          >
            Cancel
          </button>
        </form>
      )}

      {error !== "" && <p className="page-msg error">{error}</p>}

      {loading && <p className="page-empty">Loading courses...</p>}

      {!loading && filteredCourses.length === 0 && (
        <p className="page-empty">No courses found.</p>
      )}

      {!loading && filteredCourses.length > 0 && (
        <table className="courses-table">
          <thead>
            <tr>
              <th>Code</th>
              <th>Course name</th>
              <th>Hours</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {filteredCourses.map((course) => (
              <tr key={course.id}>
                <td className="courses-table-code">{course.code}</td>
                <td>{course.name}</td>
                <td>{course.hours}</td>
                <td className="courses-table-action">
                  <button
                    className="delete-btn"
                    onClick={() => handleDelete(course.id)}
                  >
                    Delete
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

export default AdminCourses;
