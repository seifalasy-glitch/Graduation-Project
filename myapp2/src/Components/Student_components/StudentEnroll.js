import { useState, useEffect } from "react";
import "../Shared_components/Pages.css";
import "./StudentEnroll.css";
import SearchBox from "../Shared_components/SearchBox";
import CourseCard from "../Shared_components/CourseCard";
import {
  getCourses,
  getDoctors,
  getAssignments,
  getStudentEnrollments,
  addEnrollment,
} from "../../coursesApi";

function StudentEnroll({ user }) {
  const [courses, setCourses] = useState([]);
  const [doctors, setDoctors] = useState([]);
  const [assignments, setAssignments] = useState([]);
  const [enrollments, setEnrollments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [search, setSearch] = useState("");

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
        setError("Can't load courses. Did you run: npm run server ?");
      }
      setLoading(false);
    }
    loadData();
  }, [user.id]);

  async function handleEnroll(item) {
    setError("");
    setSuccess("");
    try {
      const newEnrollment = await addEnrollment({
        studentId: user.id,
        assignmentId: item.assignmentId,
        courseId: item.course.id,
      });
      setEnrollments([...enrollments, newEnrollment]);
      setSuccess(`You enrolled in ${item.course.name} (${item.term})`);
    } catch (err) {
      setError("Couldn't enroll, try again");
    }
  }

  function getDoctorName(id) {
    const doctor = doctors.find((item) => item.id === id);
    if (doctor === undefined) {
      return "Unknown doctor";
    }
    return "Dr. " + doctor.name;
  }

  function getButtonText(item) {
    const sameAssignment = enrollments.find(
      (enrollment) => enrollment.assignmentId === item.assignmentId,
    );
    if (sameAssignment) {
      return "Enrolled ✓";
    }
    const sameCourse = enrollments.find(
      (enrollment) => enrollment.courseId === item.course.id,
    );
    if (sameCourse) {
      return "Already in another term";
    }
    return "Enroll";
  }

  const offered = [];
  assignments.forEach((assignment) => {
    const course = courses.find((c) => c.id === assignment.courseId);
    if (course !== undefined) {
      offered.push({
        assignmentId: assignment.id,
        term: assignment.term,
        doctorId: assignment.doctorId,
        course: course,
      });
    }
  });

  const notOffered = courses.filter(
    (course) => !assignments.find((a) => a.courseId === course.id),
  );

  const text = search.toLowerCase();
  const filteredOffered = offered.filter(
    (item) =>
      item.course.name.toLowerCase().includes(text) ||
      item.course.code.toLowerCase().includes(text) ||
      item.term.includes(text),
  );
  const filteredNotOffered = notOffered.filter(
    (course) =>
      course.name.toLowerCase().includes(text) ||
      course.code.toLowerCase().includes(text),
  );

  return (
    <div>
      <div className="page-header">
        <h1 className="page-header-title">Add Courses</h1>
        <SearchBox
          value={search}
          onChange={setSearch}
          placeholder="Search course, code or term"
        />
      </div>

      {loading && <p className="page-empty">Loading courses...</p>}
      {success !== "" && <p className="page-msg success">{success}</p>}
      {error !== "" && <p className="page-msg error">{error}</p>}

      {!loading &&
        filteredOffered.length === 0 &&
        filteredNotOffered.length === 0 && (
          <p className="page-empty">No courses found.</p>
        )}

      <div className="enroll-cards">
        {filteredOffered.map((item) => (
          <CourseCard
            key={item.assignmentId}
            name={item.course.name}
            code={item.course.code}
            hours={item.course.hours}
            term={item.term}
          >
            <p className="course-card-doctor">{getDoctorName(item.doctorId)}</p>
            <button
              className="course-card-btn"
              disabled={getButtonText(item) !== "Enroll"}
              onClick={() => handleEnroll(item)}
            >
              {getButtonText(item)}
            </button>
          </CourseCard>
        ))}

        {filteredNotOffered.map((course) => (
          <CourseCard
            key={"course-" + course.id}
            name={course.name}
            code={course.code}
            hours={course.hours}
          >
            <p className="course-card-doctor enroll-no-doctor">
              No doctor assigned yet
            </p>
          </CourseCard>
        ))}
      </div>
    </div>
  );
}

export default StudentEnroll;
