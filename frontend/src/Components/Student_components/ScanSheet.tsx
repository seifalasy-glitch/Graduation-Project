import { useEffect, useState } from "react";
import Icon from "../Shared_components/Icon";
import {
  addRecord,
  getQrChallenges,
  getRecords,
  getSessions,
} from "../../learningApi";
import { CourseInfo } from "../../courseHelpers";
import { Enrollment, GroupMembership } from "../../types";
import { timeText } from "../../format";

type ScanSheetProps = {
  enrollments: Enrollment[];
  memberships: GroupMembership[];
  courses: CourseInfo[];
  onClose: () => void;
  onSuccess: (course: string) => void;
};

function ScanSheet({
  enrollments,
  memberships,
  courses,
  onClose,
  onSuccess,
}: ScanSheetProps) {
  const [phase, setPhase] = useState("checking");
  const [course, setCourse] = useState("");
  const [time, setTime] = useState("");
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    // ASSUMPTION A20: there is no camera or QR reader, scanning checks the student into the open session if its latest QR challenge has not expired.
    async function checkIn() {
      setPhase("checking");
      try {
        const sessions = await getSessions();
        const records = await getRecords();
        const now = Date.now();
        const open = sessions.find((session) => {
          if (session.state !== "open") {
            return false;
          }
          if (new Date(session.ends_at).getTime() < now) {
            return false;
          }
          return memberships.some(
            (m) =>
              m.group_id === session.group_id &&
              enrollments.some((e) => e.id === m.enrollment_id),
          );
        });
        if (open === undefined) {
          setPhase("expired");
          return;
        }
        const challenges = await getQrChallenges(open.id);
        const valid = challenges.some(
          (c) => new Date(c.expires_at).getTime() >= now,
        );
        if (!valid) {
          setPhase("expired");
          return;
        }
        const membership = memberships.find(
          (m) =>
            m.group_id === open.group_id &&
            enrollments.some((e) => e.id === m.enrollment_id),
        );
        if (membership === undefined) {
          setPhase("expired");
          return;
        }
        const enrollment = enrollments.find(
          (e) => e.id === membership.enrollment_id,
        );
        const info = courses.find(
          (c) =>
            enrollment !== undefined &&
            c.offering.id === enrollment.offering_id,
        );
        const already = records.some(
          (r) =>
            r.session_id === open.id &&
            r.enrollment_id === membership.enrollment_id,
        );
        const marked = new Date().toISOString();
        if (!already) {
          await addRecord({
            session_id: open.id,
            enrollment_id: membership.enrollment_id,
            status: "present",
            method: "qr",
            marked_at: marked,
          });
        }
        if (info !== undefined) {
          setCourse(info.course.course_code + ": " + info.course.title);
        }
        setTime(timeText(marked));
        setPhase("success");
      } catch (err) {
        setPhase("network");
      }
    }
    checkIn();
  }, [attempt, enrollments, memberships, courses]);

  return (
    <div>
      <div className="overlay" onClick={onClose} />
      <div className="scan-sheet">
        {phase !== "success" && (
          <p className="scan-head">
            Scan Attendance QR
            <button className="scan-cancel" onClick={onClose}>
              Cancel
            </button>
          </p>
        )}

        {phase === "checking" && (
          <div className="scan-center">
            <span className="scan-ring" />
            <p className="scan-strong">Verifying lecture hall network…</p>
            <p className="scan-small">
              Looking for an open session in your rooms
            </p>
          </div>
        )}

        {phase === "network" && (
          <div>
            <div className="scan-warning">
              <span className="scan-warning-icon">
                <Icon name="wifi" size={24} />
              </span>
              <p className="scan-strong">Connection Error</p>
              <p className="scan-text">
                Please connect to the lecture hall network to register
                attendance.
              </p>
            </div>
            <button
              className="scan-btn"
              onClick={() => setAttempt(attempt + 1)}
            >
              Try Again
            </button>
          </div>
        )}

        {phase === "expired" && (
          <div>
            <div className="scan-camera">
              <span className="scan-camera-icon">
                <Icon name="refresh" size={24} />
              </span>
            </div>
            <div className="scan-center">
              <p className="scan-strong">QR Expired</p>
              <p className="scan-text">
                No open attendance session right now. Please scan the new code
                on the projector when your instructor starts one.
              </p>
            </div>
            <button
              className="scan-btn"
              onClick={() => setAttempt(attempt + 1)}
            >
              Rescan
            </button>
          </div>
        )}

        {phase === "success" && (
          <div className="scan-center">
            <span className="scan-ok">
              <Icon name="check" size={24} />
            </span>
            <p className="scan-strong">You're marked present</p>
            <p className="scan-text">
              {course} · {time}
            </p>
            <button className="scan-btn" onClick={() => onSuccess(course)}>
              Done
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

export default ScanSheet;
