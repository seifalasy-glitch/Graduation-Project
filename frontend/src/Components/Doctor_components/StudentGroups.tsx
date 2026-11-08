import { useState } from "react";
import "./CourseTabs.css";
import Icon from "../Shared_components/Icon";
import { RosterRow } from "./CourseManage";
import { addGroup, addMembership, deleteMembership } from "../../academicApi";
import { GroupMembership, StudentGroup } from "../../types";

type StudentGroupsProps = {
  offeringId: number;
  rows: RosterRow[];
  groups: StudentGroup[];
  memberships: GroupMembership[];
  onGroupsChange: (groups: StudentGroup[]) => void;
  onMembershipsChange: (memberships: GroupMembership[]) => void;
};

function getInitials(name: string) {
  let text = "";
  name.split(" ").forEach((part) => {
    if (text.length < 2) {
      text = text + part[0];
    }
  });
  return text;
}

function StudentGroups({
  offeringId,
  rows,
  groups,
  memberships,
  onGroupsChange,
  onMembershipsChange,
}: StudentGroupsProps) {
  const [selectedId, setSelectedId] = useState(0);
  const [creating, setCreating] = useState(false);
  const [name, setName] = useState("");
  const [search, setSearch] = useState("");
  const [error, setError] = useState("");

  let found = groups.find((g) => g.id === selectedId);
  if (found === undefined && groups.length > 0) {
    found = groups[0];
  }
  const selected = found;

  function membersOf(groupId: number) {
    return memberships.filter((m) => m.group_id === groupId);
  }

  function isSection(group: StudentGroup) {
    return group.group_type === "lecture";
  }

  async function handleCreate(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (name.trim() === "") {
      return;
    }
    try {
      const saved = await addGroup({
        offering_id: offeringId,
        group_type: name.toLowerCase().startsWith("section")
          ? "lecture"
          : "study",
        label: name.trim(),
      });
      onGroupsChange([...groups, saved]);
      setSelectedId(saved.id);
      setName("");
      setCreating(false);
    } catch (err) {
      setError("Couldn't create the group, try again.");
    }
  }

  async function toggle(row: RosterRow) {
    if (selected === undefined) {
      return;
    }
    const existing = memberships.find(
      (m) =>
        m.group_id === selected.id && m.enrollment_id === row.enrollment.id,
    );
    try {
      if (existing !== undefined) {
        await deleteMembership(existing.id);
        onMembershipsChange(memberships.filter((m) => m.id !== existing.id));
      } else {
        const saved = await addMembership({
          enrollment_id: row.enrollment.id,
          group_id: selected.id,
          starts_at: "2026-09-14",
          ends_at: "2026-12-24",
        });
        onMembershipsChange([...memberships, saved]);
      }
    } catch (err) {
      setError("Couldn't update the group, try again.");
    }
  }

  function otherSection(row: RosterRow) {
    if (selected === undefined || !isSection(selected)) {
      return "";
    }
    const other = groups.find(
      (g) =>
        isSection(g) &&
        g.id !== selected.id &&
        membersOf(g.id).some((m) => m.enrollment_id === row.enrollment.id),
    );
    return other !== undefined ? other.label : "";
  }

  const unassigned = rows.filter(
    (row) =>
      !groups.some(
        (g) =>
          isSection(g) &&
          membersOf(g.id).some((m) => m.enrollment_id === row.enrollment.id),
      ),
  ).length;

  const text = search.toLowerCase();
  const list = rows.filter((row) =>
    (row.student.name + " " + row.student.university_id)
      .toLowerCase()
      .includes(text),
  );

  return (
    <section className="ctab">
      <div className="ctab-head">
        <h2 className="ctab-title">Student Groups</h2>
        <p className="ctab-desc">
          Split the roster into sections or study groups.{" "}
          {unassigned > 0 && (
            <span className="ctab-warn">
              {unassigned} students have no section.
            </span>
          )}
        </p>
        <button
          className="btn-ghost ctab-head-btn"
          onClick={() => setCreating(true)}
        >
          + Create Group
        </button>
      </div>

      {error !== "" && <p className="msg-error">{error}</p>}

      <div className="groups-layout">
        <div className="groups-list">
          {groups.map((group) => (
            <button
              key={group.id}
              className={
                selected !== undefined && selected.id === group.id
                  ? "groups-item active"
                  : "groups-item"
              }
              onClick={() => setSelectedId(group.id)}
            >
              <span className="groups-item-top">
                <b>{group.label}</b>
                <span className="groups-count">
                  {membersOf(group.id).length}
                </span>
              </span>
              <span className="groups-item-bottom">
                <span className="ctab-small">
                  {isSection(group) ? "Section" : "Study group"}
                </span>
                <span className="groups-faces">
                  {membersOf(group.id)
                    .slice(0, 4)
                    .map((m) => {
                      const row = rows.find(
                        (r) => r.enrollment.id === m.enrollment_id,
                      );
                      return (
                        <span key={m.id} className="groups-face">
                          {row !== undefined
                            ? getInitials(row.student.name)
                            : "?"}
                        </span>
                      );
                    })}
                </span>
              </span>
            </button>
          ))}
          {creating && (
            <form className="groups-create" onSubmit={handleCreate}>
              <input
                className="input"
                value={name}
                placeholder="Group name, e.g. Section C"
                onChange={(e) => setName(e.target.value)}
              />
              <div className="groups-create-actions">
                <button
                  type="button"
                  className="btn-ghost btn-small"
                  onClick={() => setCreating(false)}
                >
                  Cancel
                </button>
                <button
                  className="btn-primary btn-small"
                  disabled={name.trim() === ""}
                >
                  Create
                </button>
              </div>
            </form>
          )}
        </div>

        {selected !== undefined && (
          <div className="card groups-assign">
            <div className="groups-assign-head">
              <p>
                <b>Assign to {selected.label}</b>
                <span className="ctab-small groups-selected">
                  {membersOf(selected.id).length} selected
                </span>
              </p>
              <label className="ctab-search groups-search">
                <Icon name="search" />
                <input
                  className="ctab-search-input"
                  value={search}
                  placeholder="Search roster"
                  onChange={(e) => setSearch(e.target.value)}
                />
              </label>
            </div>
            <ul className="groups-roster">
              {list.map((row) => {
                const on = membersOf(selected.id).some(
                  (m) => m.enrollment_id === row.enrollment.id,
                );
                const other = otherSection(row);
                return (
                  <li key={row.enrollment.id}>
                    <label className={on ? "groups-row on" : "groups-row"}>
                      <input
                        className="checkbox"
                        type="checkbox"
                        checked={on}
                        onChange={() => toggle(row)}
                      />
                      <span className="groups-face big">
                        {getInitials(row.student.name)}
                      </span>
                      <span className="groups-row-text">
                        <b>{row.student.name}</b>
                        <span className="ctab-small">
                          {row.student.university_id}
                        </span>
                      </span>
                      {other !== "" && (
                        <span className="groups-other">
                          {on ? "Also in " + other : "In " + other}
                        </span>
                      )}
                    </label>
                  </li>
                );
              })}
            </ul>
          </div>
        )}
      </div>
    </section>
  );
}

export default StudentGroups;
