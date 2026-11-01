import axios from "axios";
import { API_URL, addRole, UserRow } from "./api";
import {
  AcademicProgram,
  AcademicTerm,
  AuditEvent,
  Course,
  CourseOffering,
  Enrollment,
  GroupMembership,
  Room,
  RosterRow,
  StudentGroup,
  TeachingAssignment,
  User,
  UserRole,
} from "./types";

export async function getUsers() {
  const users = await axios.get<UserRow[]>(`${API_URL}/users`);
  const roles = await axios.get<UserRole[]>(`${API_URL}/user_roles`);
  return users.data.map((row) => addRole(row, roles.data));
}

export async function addUser(user: Omit<User, "id">) {
  const response = await axios.post<UserRow>(`${API_URL}/users`, {
    university_id: user.university_id,
    email: user.email,
    created_at: user.created_at,
    name: user.name,
    password: user.password,
    must_change_password: user.must_change_password,
    department: user.department,
    active: user.active,
  });
  const role = await axios.post<UserRole>(`${API_URL}/user_roles`, {
    user_id: response.data.id,
    role: user.role,
  });
  return addRole(response.data, [role.data]);
}

export async function getCourses() {
  const response = await axios.get<Course[]>(`${API_URL}/courses`);
  return response.data;
}

export async function getTerms() {
  const response = await axios.get<AcademicTerm[]>(`${API_URL}/academic_terms`);
  return response.data;
}

export async function addTerm(term: Omit<AcademicTerm, "id">) {
  const response = await axios.post<AcademicTerm>(
    `${API_URL}/academic_terms`,
    term,
  );
  return response.data;
}

export async function getPrograms() {
  const response = await axios.get<AcademicProgram[]>(
    `${API_URL}/academic_programs`,
  );
  return response.data;
}

export async function addProgram(program: Omit<AcademicProgram, "id">) {
  const response = await axios.post<AcademicProgram>(
    `${API_URL}/academic_programs`,
    program,
  );
  return response.data;
}

export async function updateProgram(
  id: number,
  changes: Partial<AcademicProgram>,
) {
  const response = await axios.patch<AcademicProgram>(
    `${API_URL}/academic_programs/${id}`,
    changes,
  );
  return response.data;
}

export async function getOfferings() {
  const response = await axios.get<CourseOffering[]>(
    `${API_URL}/course_offerings`,
  );
  return response.data;
}

export async function getGroups() {
  const response = await axios.get<StudentGroup[]>(`${API_URL}/student_groups`);
  return response.data;
}

export async function getTeachingAssignments() {
  const response = await axios.get<TeachingAssignment[]>(
    `${API_URL}/teaching_assignments`,
  );
  return response.data;
}

export async function getEnrollments() {
  const response = await axios.get<Enrollment[]>(`${API_URL}/enrollments`);
  return response.data;
}

export async function getStudentEnrollments(studentId: number) {
  const response = await axios.get<Enrollment[]>(`${API_URL}/enrollments`, {
    params: { student_user_id: studentId },
  });
  return response.data;
}

export async function getMemberships() {
  const response = await axios.get<GroupMembership[]>(
    `${API_URL}/group_memberships`,
  );
  return response.data;
}

export async function getRooms() {
  const response = await axios.get<Room[]>(`${API_URL}/rooms`);
  return response.data;
}

export async function getAuditEvents() {
  const response = await axios.get<AuditEvent[]>(`${API_URL}/audit_events`, {
    params: { _sort: "created_at", _order: "desc" },
  });
  return response.data;
}

export async function addAuditEvent(event: Omit<AuditEvent, "id">) {
  const response = await axios.post<AuditEvent>(
    `${API_URL}/audit_events`,
    event,
  );
  return response.data;
}

export async function addOffering(offering: Omit<CourseOffering, "id">) {
  const response = await axios.post<CourseOffering>(
    `${API_URL}/course_offerings`,
    offering,
  );
  return response.data;
}

export async function getSampleRoster() {
  const response = await axios.get<RosterRow[]>(`${API_URL}/sample_roster`);
  return response.data;
}
