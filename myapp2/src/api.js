import axios from "axios";

const API_URL = "http://localhost:3001";

export async function loginRequest(email, password) {
  const response = await axios.get(`${API_URL}/users`, {
    params: { email: email, password: password },
  });

  if (response.data.length === 0) {
    throw new Error("Wrong email or password");
  }

  return response.data[0];
}

export async function changePasswordRequest(userId, oldPassword, newPassword) {
  const response = await axios.get(`${API_URL}/users/${userId}`);

  if (response.data.password !== oldPassword) {
    throw new Error("Current password is wrong");
  }

  const updated = await axios.patch(`${API_URL}/users/${userId}`, {
    password: newPassword,
  });

  return updated.data;
}
