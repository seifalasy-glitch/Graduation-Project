import axios from "axios";

const API_URL = "http://localhost:3001";

export async function getCourses() {
  const response = await axios.get(`${API_URL}/courses`);
  return response.data;
}

export async function addCourse(course) {
  const response = await axios.post(`${API_URL}/courses`, course);
  return response.data;
}

export async function deleteCourse(courseId) {
  await axios.delete(`${API_URL}/courses/${courseId}`);
}

export async function getDoctors() {
  const response = await axios.get(`${API_URL}/users`, {
    params: { role: "doctor" },
  });
  return response.data;
}

export async function getAssignments() {
  const response = await axios.get(`${API_URL}/assignments`);
  return response.data;
}

export async function getDoctorAssignments(doctorId) {
  const response = await axios.get(`${API_URL}/assignments`, {
    params: { doctorId: doctorId },
  });
  return response.data;
}

export async function addAssignment(assignment) {
  const response = await axios.post(`${API_URL}/assignments`, assignment);
  return response.data;
}

export async function deleteAssignment(assignmentId) {
  await axios.delete(`${API_URL}/assignments/${assignmentId}`);
}

export async function getStudentEnrollments(studentId) {
  const response = await axios.get(`${API_URL}/enrollments`, {
    params: { studentId: studentId },
  });
  return response.data;
}

export async function addEnrollment(enrollment) {
  const response = await axios.post(`${API_URL}/enrollments`, enrollment);
  return response.data;
}
