import axios from "axios";
import { API_URL } from "./api";
import {
  Alert,
  Assignment,
  AssignmentInfo,
  AttendanceRecord,
  AttendanceSession,
  GradeItem,
  Quiz,
  QrChallenge,
  StudentGrade,
  Submission,
} from "./types";

export async function getAssignments() {
  const items = await axios.get<GradeItem[]>(`${API_URL}/grade_items`, {
    params: { item_type: "assignment" },
  });
  const rows = await axios.get<Assignment[]>(`${API_URL}/assignments`);
  const list: AssignmentInfo[] = [];
  items.data.forEach((item) => {
    const row = rows.data.find((a) => a.id === item.id);
    if (row !== undefined) {
      list.push({ ...item, ...row });
    }
  });
  return list;
}

export async function getSubmissions() {
  const response = await axios.get<Submission[]>(`${API_URL}/submissions`);
  return response.data;
}

export async function getGradeItems() {
  const response = await axios.get<GradeItem[]>(`${API_URL}/grade_items`);
  return response.data;
}

export async function getStudentGrades() {
  const response = await axios.get<StudentGrade[]>(`${API_URL}/student_grades`);
  return response.data;
}

export async function getQuizzes() {
  const response = await axios.get<Quiz[]>(`${API_URL}/quizzes`);
  return response.data;
}

export async function getAlerts(userId: number) {
  const response = await axios.get<Alert[]>(`${API_URL}/alerts`, {
    params: { user_id: userId },
  });
  return response.data;
}

export async function getSessions() {
  const response = await axios.get<AttendanceSession[]>(
    `${API_URL}/attendance_sessions`,
  );
  return response.data;
}

export async function getRecords() {
  const response = await axios.get<AttendanceRecord[]>(
    `${API_URL}/attendance_records`,
  );
  return response.data;
}

export async function addRecord(record: Omit<AttendanceRecord, "id">) {
  const response = await axios.post<AttendanceRecord>(
    `${API_URL}/attendance_records`,
    record,
  );
  return response.data;
}

export async function getQrChallenges(sessionId: number) {
  const response = await axios.get<QrChallenge[]>(`${API_URL}/qr_challenges`, {
    params: { session_id: sessionId },
  });
  return response.data;
}
