import { useState } from "react";
import "./CourseTabs.css";
import Icon from "../Shared_components/Icon";
import { RosterRow } from "./CourseManage";
import { GradeItem, StudentGrade } from "../../types";
import { letterGrade } from "../../format";

type CourseRosterProps = {
  rows: RosterRow[];
  gradeItems: GradeItem[];
  grades: StudentGrade[];
};

function CourseRoster({ rows, gradeItems, grades }: CourseRosterProps) {
  const [search, setSearch] = useState("");
  const [sort, setSort] = useState("name");

  function getLetter(enrollmentId: number) {
    let earned = 0;
    let possible = 0;
    grades.forEach((grade) => {
      if (grade.enrollment_id !== enrollmentId) {
        return;
      }
      const item = gradeItems.find((g) => g.id === grade.grade_item_id);
      if (item !== undefined) {
        earned = earned + grade.score;
        possible = possible + item.max_score;
      }
    });
    if (possible === 0) {
      return "—";
    }
    return letterGrade((earned / possible) * 100);
  }

  const text = search.toLowerCase();
  const shown = rows.filter((row) =>
    (row.student.name + row.student.university_id).toLowerCase().includes(text),
  );
  if (sort === "name") {
    shown.sort((a, b) => a.student.name.localeCompare(b.student.name));
  } else {
    shown.sort((a, b) => a.percent - b.percent);
  }

  return (
    <section className="ctab">
      <div className="ctab-bar">
        <label className="ctab-search">
          <Icon name="search" />
          <input
            className="ctab-search-input"
            value={search}
            placeholder="Search students"
            onChange={(e) => setSearch(e.target.value)}
          />
        </label>
        <div className="ctab-sort">
          <button
            className={
              sort === "name" ? "ctab-sort-btn active" : "ctab-sort-btn"
            }
            onClick={() => setSort("name")}
          >
            Name
          </button>
          <button
            className={
              sort === "att" ? "ctab-sort-btn active" : "ctab-sort-btn"
            }
            onClick={() => setSort("att")}
          >
            Lowest attendance
          </button>
        </div>
      </div>

      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Student</th>
              <th>ID</th>
              <th>Sessions</th>
              <th className="ctab-att-col">Attendance</th>
              <th>Current grade</th>
            </tr>
          </thead>
          <tbody>
            {shown.map((row) => (
              <tr key={row.enrollment.id}>
                <td>
                  <p className="ctab-strong">{row.student.name}</p>
                  <p className="ctab-small">{row.student.email}</p>
                </td>
                <td className="muted">{row.student.university_id}</td>
                <td className="muted">
                  {row.attended}/{row.total}
                </td>
                <td>
                  <span className="ctab-bar-track">
                    <span
                      className={
                        row.percent < 75 ? "ctab-bar-fill low" : "ctab-bar-fill"
                      }
                      style={{ width: row.percent + "%" }}
                    />
                  </span>
                  <span
                    className={row.percent < 75 ? "ctab-pct low" : "ctab-pct"}
                  >
                    {row.percent}%
                  </span>
                </td>
                <td>
                  <span className="pill">{getLetter(row.enrollment.id)}</span>
                </td>
              </tr>
            ))}
            {shown.length === 0 && (
              <tr>
                <td colSpan={5} className="empty">
                  No students match.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <p className="ctab-foot">
        Showing {shown.length} of {rows.length} · Students below 75% are flagged
        in amber.
      </p>
    </section>
  );
}

export default CourseRoster;
