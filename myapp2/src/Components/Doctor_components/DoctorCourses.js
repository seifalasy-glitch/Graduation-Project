import { useState, useEffect } from "react";
import "../Shared_components/Pages.css";
import "./DoctorCourses.css";
import CourseCard from "../Shared_components/CourseCard";
import { getCourses, getDoctorAssignments } from "../../coursesApi";

function DoctorCourses({ user }) {
  const [courses, setCourses] = useState([]);
  const [assignments, setAssignments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    async function loadData() {
      try {
        const coursesData = await getCourses();
        const assignmentsData = await getDoctorAssignments(user.id);
        setCourses(coursesData);
        setAssignments(assignmentsData);
      } catch (err) {
        setError("Can't load your courses. Did you run: npm run server ?");
      }
      setLoading(false);
    }
    loadData();
  }, [user.id]);

  const myCourses = [];
  assignments.forEach((item) => {
    const course = courses.find((c) => c.id === item.courseId);
    if (course !== undefined) {
      myCourses.push({ id: item.id, term: item.term, course: course });
    }
  });

  return (
    <div>
      <h1 className="coming-soon">My Courses</h1>
      <p className="coming-soon-text">The courses the admin assigned to you.</p>

      {loading && <p className="page-empty">Loading...</p>}
      {error !== "" && <p className="page-msg error">{error}</p>}

      {!loading && error === "" && myCourses.length === 0 && (
        <p className="page-empty doctor-empty">
          No courses assigned to you yet.
        </p>
      )}

      <div className="doctor-courses">
        {myCourses.map((item) => (
          <CourseCard
            key={item.id}
            name={item.course.name}
            code={item.course.code}
            hours={item.course.hours}
            term={item.term}
          />
        ))}
      </div>
    </div>
  );
}

export default DoctorCourses;
