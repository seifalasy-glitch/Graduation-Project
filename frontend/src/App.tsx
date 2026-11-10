import { useState } from "react";
import { Navigate, Route, Routes, useNavigate } from "react-router-dom";
import "./App.css";
import { User } from "./types";
import Login from "./Components/Login_components/Login";
import AdminDashboard from "./Components/Admin_components/AdminDashboard";
import DoctorDashboard from "./Components/Doctor_components/DoctorDashboard";
import StudentDashboard from "./Components/Student_components/StudentDashboard";

function App() {
  const [user, setUser] = useState<User | null>(null);
  const navigate = useNavigate();

  function handleLogin(loggedIn: User) {
    setUser(loggedIn);
    navigate("/" + loggedIn.role);
  }

  function handleLogout() {
    setUser(null);
    navigate("/login");
  }

  let homePage = "/login";
  if (user !== null) {
    homePage = "/" + user.role;
  }

  return (
    <Routes>
      <Route
        path="/login"
        element={
          user === null ? (
            <Login onLogin={handleLogin} />
          ) : (
            <Navigate to={homePage} />
          )
        }
      />
      <Route
        path="/admin/*"
        element={
          user !== null && user.role === "admin" ? (
            <AdminDashboard
              user={user}
              onUserChange={setUser}
              onLogout={handleLogout}
            />
          ) : (
            <Navigate to={homePage} />
          )
        }
      />
      <Route
        path="/doctor/*"
        element={
          user !== null && user.role === "doctor" ? (
            <DoctorDashboard
              user={user}
              onUserChange={setUser}
              onLogout={handleLogout}
            />
          ) : (
            <Navigate to={homePage} />
          )
        }
      />
      <Route
        path="/student/*"
        element={
          user !== null && user.role === "student" ? (
            <StudentDashboard
              user={user}
              onUserChange={setUser}
              onLogout={handleLogout}
            />
          ) : (
            <Navigate to={homePage} />
          )
        }
      />
      <Route path="*" element={<Navigate to={homePage} />} />
    </Routes>
  );
}

export default App;
