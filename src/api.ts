import axios from "axios";
import { Role, User, UserRole } from "./types";

export const API_URL = "http://localhost:3001";

export type UserRow = Omit<User, "role">;

export function addRole(row: UserRow, roles: UserRole[]) {
  let role: Role = "student";
  const found = roles.find((r) => r.user_id === row.id);
  if (found !== undefined) {
    role = found.role;
  }
  const user: User = { ...row, role: role };
  return user;
}

async function withRole(row: UserRow) {
  const response = await axios.get<UserRole[]>(`${API_URL}/user_roles`, {
    params: { user_id: row.id },
  });
  return addRole(row, response.data);
}

export async function loginRequest(idOrEmail: string, password: string) {
  const byEmail = await axios.get<UserRow[]>(`${API_URL}/users`, {
    params: { email: idOrEmail, password: password },
  });
  if (byEmail.data.length > 0) {
    return withRole(byEmail.data[0]);
  }
  const byId = await axios.get<UserRow[]>(`${API_URL}/users`, {
    params: { university_id: idOrEmail, password: password },
  });
  if (byId.data.length === 0) {
    throw new Error("Invalid credentials. Please try again.");
  }
  return withRole(byId.data[0]);
}

export async function setFirstPassword(userId: number, newPassword: string) {
  const response = await axios.patch<UserRow>(`${API_URL}/users/${userId}`, {
    password: newPassword,
    must_change_password: false,
  });
  return withRole(response.data);
}

export async function changePasswordRequest(
  userId: number,
  oldPassword: string,
  newPassword: string,
) {
  const response = await axios.get<UserRow>(`${API_URL}/users/${userId}`);
  if (response.data.password !== oldPassword) {
    throw new Error("Current password is wrong");
  }
  const updated = await axios.patch<UserRow>(`${API_URL}/users/${userId}`, {
    password: newPassword,
  });
  return withRole(updated.data);
}

export async function updateUser(userId: number, changes: Partial<UserRow>) {
  const response = await axios.patch<UserRow>(
    `${API_URL}/users/${userId}`,
    changes,
  );
  return withRole(response.data);
}
