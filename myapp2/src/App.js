import { useState } from "react";
import "./App.css";

import Login from "./Components/Login_components/Login";
import StudentDashboard from "./Components/Student_components/StudentDashboard";
import DoctorDashboard from "./Components/Doctor_components/DoctorDashboard";
import AdminDashboard from "./Components/Admin_components/AdminDashboard";

function App() {
  const [user, setUser] = useState(null);

  function handleLogout() {
    setUser(null);
  }

  if (user === null) {
    return <Login onLogin={setUser} />;
  }

  if (user.role === "student") {
    return <StudentDashboard user={user} onLogout={handleLogout} />;
  }

  if (user.role === "doctor") {
    return <DoctorDashboard user={user} onLogout={handleLogout} />;
  }

  if (user.role === "admin") {
    return <AdminDashboard user={user} onLogout={handleLogout} />;
  }

  return <h1>Unknown role</h1>;
}

export default App;
