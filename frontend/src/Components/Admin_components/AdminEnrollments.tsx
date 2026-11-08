import { useEffect, useState } from "react";
import "./AdminEnrollments.css";
import PageHead from "../Shared_components/PageHead";
import Toast from "../Shared_components/Toast";
import Icon from "../Shared_components/Icon";
import {
  addEnrollment,
  addMembership,
  addTeachingAssignment,
  getCourses,
  getEnrollments,
  getGroups,
  getOfferings,
  getUsersByRole,
} from "../../academicApi";
import {
  Course,
  CourseOffering,
  Enrollment,
  StudentGroup,
  User,
} from "../../types";
import { lastName } from "../../format";

function AdminEnrollments() {
  const [groups, setGroups] = useState<StudentGroup[]>([]);
  const [offerings, setOfferings] = useState<CourseOffering[]>([]);
  const [courses, setCourses] = useState<Course[]>([]);
  const [doctors, setDoctors] = useState<User[]>([]);
  const [students, setStudents] = useState<User[]>([]);
  const [enrollments, setEnrollments] = useState<Enrollment[]>([]);
  const [error, setError] = useState("");

  const [groupId, setGroupId] = useState("");
  const [doctorSearch, setDoctorSearch] = useState("");
  const [doctorOpen, setDoctorOpen] = useState(false);
  const [doctor, setDoctor] = useState<User | null>(null);
  const [studentSearch, setStudentSearch] = useState("");
  const [picked, setPicked] = useState<number[]>([]);
  const [saving, setSaving] = useState(false);
  const [toast, setToast] = useState("");

  useEffect(() => {
    async function loadData() {
      try {
        setGroups(await getGroups());
        setOfferings(await getOfferings());
        setCourses(await getCourses());
        setDoctors(await getUsersByRole("doctor"));
        setStudents(await getUsersByRole("student"));
        setEnrollments(await getEnrollments());
      } catch (err) {
        setError("Can't load enrollments. Did you run: npm run server ?");
      }
    }
    loadData();
  }, []);

  function getGroupLabel(group: StudentGroup) {
    const offering = offerings.find((item) => item.id === group.offering_id);
    if (offering === undefined) {
      return group.label;
    }
    const course = courses.find((item) => item.id === offering.course_id);
    if (course === undefined) {
      return group.label;
    }
    return course.course_code + " · " + course.title + " — " + group.label;
  }

  const selectedGroup = groups.find((group) => String(group.id) === groupId);
  let selectedOffering: CourseOffering | undefined = undefined;
  if (selectedGroup !== undefined) {
    selectedOffering = offerings.find(
      (item) => item.id === selectedGroup.offering_id,
    );
  }
  let capacity = 60;
  if (selectedOffering !== undefined) {
    capacity = selectedOffering.capacity;
  }

  const doctorMatches = doctors.filter((item) =>
    item.name.toLowerCase().includes(doctorSearch.toLowerCase()),
  );

  const studentText = studentSearch.toLowerCase();
  const visibleStudents = students.filter((item) =>
    (item.name + item.university_id).toLowerCase().includes(studentText),
  );

  const allOn =
    visibleStudents.length > 0 &&
    visibleStudents.every((item) => picked.includes(item.id));

  const ready =
    selectedGroup !== undefined && doctor !== null && picked.length > 0;

  function toggleStudent(studentId: number) {
    if (picked.includes(studentId)) {
      setPicked(picked.filter((id) => id !== studentId));
    } else {
      setPicked([...picked, studentId]);
    }
  }

  function toggleAll() {
    if (allOn) {
      setPicked(
        picked.filter((id) => !visibleStudents.some((item) => item.id === id)),
      );
    } else {
      const next = [...picked];
      visibleStudents.forEach((item) => {
        if (!next.includes(item.id)) {
          next.push(item.id);
        }
      });
      setPicked(next);
    }
  }

  function reset() {
    setGroupId("");
    setDoctor(null);
    setDoctorSearch("");
    setPicked([]);
    setStudentSearch("");
  }

  async function handleSave() {
    if (selectedGroup === undefined || doctor === null) {
      return;
    }
    setSaving(true);
    try {
      await addTeachingAssignment({
        group_id: selectedGroup.id,
        doctor_user_id: doctor.id,
        starts_at: "2026-09-14",
        ends_at: "2026-12-24",
      });
      const added: Enrollment[] = [];
      for (const studentId of picked) {
        let enrollment = enrollments.find(
          (item) =>
            item.student_user_id === studentId &&
            item.offering_id === selectedGroup.offering_id,
        );
        if (enrollment === undefined) {
          enrollment = await addEnrollment({
            student_user_id: studentId,
            offering_id: selectedGroup.offering_id,
            program_id: 1,
            status: "active",
          });
          added.push(enrollment);
        }
        await addMembership({
          enrollment_id: enrollment.id,
          group_id: selectedGroup.id,
          starts_at: "2026-09-14",
          ends_at: "2026-12-24",
        });
      }
      setEnrollments([...enrollments, ...added]);
      setToast(
        "Enrollments saved. Students will see the course in their hub immediately.",
      );
      reset();
    } catch (err) {
      setError("Couldn't save the enrollments, try again.");
    }
    setSaving(false);
  }

  return (
    <div>
      <PageHead
        eyebrow="Admin · Academic structure"
        title="Enrollments & Assignments"
        desc="Link a course section to its doctor and enrolled students."
      />

      {toast !== "" && (
        <div className="enroll-gap">
          <Toast text={toast} onClose={() => setToast("")} />
        </div>
      )}
      {error !== "" && <p className="msg-error">{error}</p>}

      <div className="enroll-layout">
        <div className="enroll-main">
          <label className="field-label">Select Course & Section</label>
          <select
            className={groupId === "" ? "input placeholder" : "input"}
            value={groupId}
            onChange={(e) => setGroupId(e.target.value)}
          >
            <option value="">Choose a course section…</option>
            {groups.map((group) => (
              <option key={group.id} value={group.id}>
                {getGroupLabel(group)}
              </option>
            ))}
          </select>

          <label className="field-label enroll-block">Assign Doctor</label>
          {doctor !== null ? (
            <div className="enroll-doctor">
              <span className="enroll-doctor-avatar">
                {lastName(doctor.name).charAt(0)}
              </span>
              <span className="enroll-doctor-name">{doctor.name}</span>
              <button
                className="enroll-change"
                onClick={() => {
                  setDoctor(null);
                  setDoctorSearch("");
                }}
              >
                Change
              </button>
            </div>
          ) : (
            <div className="enroll-search-box">
              <span className="enroll-search-icon">
                <Icon name="search" />
              </span>
              <input
                className="input enroll-search-input"
                value={doctorSearch}
                placeholder="Search doctors by name…"
                onChange={(e) => {
                  setDoctorSearch(e.target.value);
                  setDoctorOpen(true);
                }}
                onFocus={() => setDoctorOpen(true)}
                onBlur={() => setTimeout(() => setDoctorOpen(false), 150)}
              />
              {doctorOpen && (
                <ul className="enroll-dropdown">
                  {doctorMatches.length === 0 && (
                    <li className="enroll-dropdown-empty">
                      No doctors match "{doctorSearch}"
                    </li>
                  )}
                  {doctorMatches.map((item) => (
                    <li key={item.id}>
                      <button
                        className="enroll-dropdown-item"
                        onClick={() => {
                          setDoctor(item);
                          setDoctorOpen(false);
                        }}
                      >
                        {item.name}
                        <span className="enroll-dropdown-dept">
                          {item.department}
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}

          <label className="field-label enroll-block">
            Enroll Students
            <span className="field-hint">{picked.length} selected</span>
          </label>
          <div className="enroll-students">
            <div className="enroll-students-bar">
              <span className="subtle">
                <Icon name="search" />
              </span>
              <input
                className="enroll-students-input"
                value={studentSearch}
                placeholder="Filter by name or student ID"
                onChange={(e) => setStudentSearch(e.target.value)}
              />
              <button className="enroll-select-all" onClick={toggleAll}>
                {allOn ? "Clear visible" : "Select all"}
              </button>
            </div>
            <ul className="enroll-students-list">
              {visibleStudents.map((item) => {
                const on = picked.includes(item.id);
                return (
                  <li key={item.id}>
                    <label
                      className={on ? "enroll-student on" : "enroll-student"}
                    >
                      <input
                        className="checkbox"
                        type="checkbox"
                        checked={on}
                        onChange={() => toggleStudent(item.id)}
                      />
                      <span className="enroll-student-name">{item.name}</span>
                      <span className="enroll-student-id">
                        {item.university_id}
                      </span>
                    </label>
                  </li>
                );
              })}
              {visibleStudents.length === 0 && (
                <li className="enroll-dropdown-empty">No students match.</li>
              )}
            </ul>
          </div>
        </div>

        <aside className="card enroll-summary">
          <p className="enroll-summary-title">Summary</p>
          <p className="enroll-summary-label">Section</p>
          <p className="enroll-summary-value">
            {selectedGroup !== undefined ? getGroupLabel(selectedGroup) : "—"}
          </p>
          <p className="enroll-summary-label">Doctor</p>
          <p className="enroll-summary-value">
            {doctor !== null ? doctor.name : "—"}
          </p>
          <p className="enroll-summary-label">Students</p>
          <p className="enroll-summary-value">
            {picked.length}{" "}
            <span className="enroll-capacity">/ {capacity} capacity</span>
          </p>
          <div className="progress enroll-progress">
            <div
              className="progress-fill"
              style={{ width: (picked.length / capacity) * 100 + "%" }}
            />
          </div>
          <button
            className="btn-primary enroll-full"
            disabled={!ready || saving}
            onClick={handleSave}
          >
            <Icon name="check" />
            Save Enrollments
          </button>
          <button className="btn-ghost enroll-full" onClick={reset}>
            Reset
          </button>
          {!ready && (
            <p className="enroll-hint">
              Choose a section, a doctor, and at least one student.
            </p>
          )}
        </aside>
      </div>
    </div>
  );
}

export default AdminEnrollments;
