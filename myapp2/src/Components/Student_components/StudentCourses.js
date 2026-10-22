import { useState, useEffect } from "react";
import "../Shared_components/Pages.css";
import "./StudentCourses.css";
import CourseCard from "../Shared_components/CourseCard";
import {
  getCourses,
  getDoctors,
  getAssignments,
  getStudentEnrollments,
} from "../../coursesApi";

function StudentCourses({ user, onGoToEnroll }) {
  const [courses, setCourses] = useState([]);
  const [doctors, setDoctors] = useState([]);
  const [assignments, setAssignments] = useState([]);
  const [enrollments, setEnrollments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    async function loadData() {
      try {
        const coursesData = await getCourses();
        const doctorsData = await getDoctors();
        const assignmentsData = await getAssignments();
        const enrollmentsData = await getStudentEnrollments(user.id);
        setCourses(coursesData);
        setDoctors(doctorsData);
        setAssignments(assignmentsData);
        setEnrollments(enrollmentsData);
      } catch (err) {
        setError("Can't load your courses. Did you run: npm run server ?");
      }
      setLoading(false);
    }
    loadData();
  }, [user.id]);

  function getDoctorName(id) {
    const doctor = doctors.find((item) => item.id === id);
    if (doctor === undefined) {
      return "Unknown doctor";
    }
    return "Dr. " + doctor.name;
  }

  const myCourses = [];
  enrollments.forEach((enrollment) => {
    const assignment = assignments.find(
      (a) => a.id === enrollment.assignmentId,
    );
    if (assignment === undefined) {
      return;
    }
    const course = courses.find((c) => c.id === assignment.courseId);
    if (course === undefined) {
      return;
    }
    myCourses.push({
      id: enrollment.id,
      term: assignment.term,
      doctorId: assignment.doctorId,
      course: course,
    });
  });

  return (
    <div>
      <h1 className="coming-soon">My Courses</h1>
      <p className="coming-soon-text">The courses you enrolled in.</p>

      {loading && <p className="page-empty">Loading...</p>}
      {error !== "" && <p className="page-msg error">{error}</p>}

      {!loading && error === "" && myCourses.length === 0 && (
        <div className="student-empty">
          <p className="page-empty">You didn't enroll in any course yet.</p>
          <button className="student-empty-btn" onClick={onGoToEnroll}>
            + Enroll in Courses
          </button>
        </div>
      )}

      <div className="student-courses">
        {myCourses.map((item) => (
          <CourseCard
            key={item.id}
            name={item.course.name}
            code={item.course.code}
            hours={item.course.hours}
            term={item.term}
          >
            <p className="course-card-doctor">{getDoctorName(item.doctorId)}</p>
          </CourseCard>
        ))}
      </div>
    </div>
  );
}

export default StudentCourses;
