import { useState } from "react";
import Icon from "../Shared_components/Icon";
import { CatalogItem, slotText } from "./StudentEnroll";
import { addEnrollment, addMembership } from "../../academicApi";
import { Enrollment, StudentGroup, User } from "../../types";
import { hourText } from "../../format";

type CourseDetailsProps = {
  user: User;
  item: CatalogItem;
  held: CatalogItem[];
  groups: StudentGroup[];
  mine: Enrollment[];
  onClose: () => void;
  onEnrolled: (enrollment: Enrollment) => void;
  onSchedule: () => void;
};

function clashWith(item: CatalogItem, held: CatalogItem[]) {
  return held.find(
    (other) =>
      other.offering.id !== item.offering.id &&
      other.offering.days.some((day) => item.offering.days.includes(day)) &&
      other.offering.start_hour < item.offering.end_hour &&
      item.offering.start_hour < other.offering.end_hour,
  );
}

function CourseDetails({
  user,
  item,
  held,
  groups,
  mine,
  onClose,
  onEnrolled,
  onSchedule,
}: CourseDetailsProps) {
  const [phase, setPhase] = useState("idle");
  const [error, setError] = useState("");

  const full = item.seatsLeft === 0;
  const clash = clashWith(item, held);
  const already = mine.some((e) => e.offering_id === item.offering.id);
  const fill =
    ((item.offering.capacity - item.seatsLeft) / item.offering.capacity) * 100;

  async function handleEnroll() {
    if (clash !== undefined) {
      setPhase("conflict");
      return;
    }
    if (full) {
      setPhase("waitlisted");
      return;
    }
    setPhase("checking");
    try {
      const enrollment = await addEnrollment({
        student_user_id: user.id,
        offering_id: item.offering.id,
        program_id: 1,
        status: "active",
      });
      const lecture = groups.find(
        (g) => g.offering_id === item.offering.id && g.group_type === "lecture",
      );
      if (lecture !== undefined) {
        await addMembership({
          enrollment_id: enrollment.id,
          group_id: lecture.id,
          starts_at: new Date().toISOString().slice(0, 10),
          ends_at: "2026-12-24",
        });
      }
      onEnrolled(enrollment);
      setPhase("success");
    } catch (err) {
      setError("Couldn't enroll, try again.");
      setPhase("idle");
    }
  }

  const done = phase === "success" || phase === "waitlisted";

  return (
    <div>
      <div className="overlay" onClick={onClose} />
      <div className="drawer details">
        <p className="details-top">
          Course details
          <button className="details-close" onClick={onClose}>
            <Icon name="x" />
          </button>
        </p>

        {done ? (
          <div className="details-done">
            <span className="details-done-icon">✓</span>
            {phase === "success" ? (
              <div>
                <h2 className="details-done-title">
                  Successfully Enrolled in {item.course.title}
                </h2>
                <p className="details-text">
                  {item.course.course_code} · {slotText(item.offering)} ·{" "}
                  {item.room}
                </p>
                <p className="subtle">
                  It's been added to your schedule and Course Hub.
                </p>
              </div>
            ) : (
              <div>
                <h2 className="details-done-title">You're on the waitlist</h2>
                <p className="details-text">
                  We'll notify you and enroll you automatically if a seat in{" "}
                  {item.course.course_code} opens.
                </p>
              </div>
            )}
            <div className="details-done-actions">
              <button className="btn-ghost" onClick={onClose}>
                Back to catalog
              </button>
              <button className="btn-primary" onClick={onSchedule}>
                View My Schedule
              </button>
            </div>
          </div>
        ) : (
          <div>
            <div className="details-body">
              <p className="details-code">
                {item.course.course_code} · {item.course.credit_hours} credits
              </p>
              <h2 className="details-title">{item.course.title}</h2>
              <p className="details-text">
                {item.prof} · {item.course.dept}
              </p>

              <p className="details-seats">
                Seats
                <span className={full ? "enroll-full" : ""}>
                  {full
                    ? "0/" + item.offering.capacity + " · Full"
                    : item.seatsLeft +
                      "/" +
                      item.offering.capacity +
                      " seats left"}
                </span>
              </p>
              <div className="progress">
                <div className="progress-fill" style={{ width: fill + "%" }} />
              </div>

              <h3 className="details-h3">Schedule</h3>
              <div className="details-schedule">
                <div className="details-cell">
                  <p className="subtle">Days</p>
                  <b>{item.offering.days.join(" / ")}</b>
                </div>
                <div className="details-cell">
                  <p className="subtle">Time</p>
                  <b>
                    {hourText(item.offering.start_hour)} –{" "}
                    {hourText(item.offering.end_hour)}
                  </b>
                </div>
                <div className="details-cell">
                  <p className="subtle">Room</p>
                  <b>{item.room}</b>
                </div>
              </div>

              <h3 className="details-h3">Prerequisites</h3>
              {item.course.prereqs.length === 0 ? (
                <p className="details-text">None — open to all students.</p>
              ) : (
                <ul className="details-prereqs">
                  {item.course.prereqs.map((code) => {
                    const inProgress = held.some(
                      (h) => h.course.course_code === code,
                    );
                    return (
                      <li key={code} className="details-prereq">
                        <span
                          className={
                            inProgress ? "details-check ok" : "details-check"
                          }
                        >
                          {inProgress ? "✓" : "!"}
                        </span>
                        <b>{code}</b>
                        <span className="subtle">
                          {inProgress
                            ? "In progress (concurrent)"
                            : "Not completed"}
                        </span>
                      </li>
                    );
                  })}
                </ul>
              )}

              <h3 className="details-h3">Syllabus summary</h3>
              <p className="details-text">{item.course.syllabus}</p>
              <p className="details-topics">
                {item.course.topics.map((topic) => (
                  <span key={topic} className="pill details-topic">
                    {topic}
                  </span>
                ))}
              </p>
            </div>

            <div className="details-foot">
              {error !== "" && <p className="msg-error">{error}</p>}
              {phase === "conflict" && clash !== undefined && (
                <p className="msg-error">
                  <b>Enrollment Failed:</b> This course schedule clashes with
                  your existing enrollment in {clash.course.course_code} (
                  {slotText(clash.offering)}).
                </p>
              )}
              {already ? (
                <button className="btn-ghost details-wide" onClick={onSchedule}>
                  Already enrolled · View My Schedule
                </button>
              ) : phase === "conflict" ? (
                <button className="btn-ghost details-wide" onClick={onClose}>
                  Browse other courses
                </button>
              ) : full ? (
                <button
                  className="btn-ghost details-wide"
                  onClick={handleEnroll}
                >
                  Join Waitlist
                </button>
              ) : (
                <button
                  className="btn-primary details-wide"
                  disabled={phase === "checking"}
                  onClick={handleEnroll}
                >
                  {phase === "checking"
                    ? "Checking schedule…"
                    : "Enroll in Course"}
                </button>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export default CourseDetails;
