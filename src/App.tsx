import { useState } from "react";
import "./App.css";
import { User } from "./types";
import Login from "./Components/Login_components/Login";
import AdminDashboard from "./Components/Admin_components/AdminDashboard";
import DoctorDashboard from "./Components/Doctor_components/DoctorDashboard";
import StudentDashboard from "./Components/Student_components/StudentDashboard";

function App() {
  const [user, setUser] = useState<User | null>(null);

  function handleLogout() {
    setUser(null);
  }

  if (user === null) {
    return <Login onLogin={setUser} />;
  }

  if (user.role === "admin") {
    return (
      <AdminDashboard
        user={user}
        onUserChange={setUser}
        onLogout={handleLogout}
      />
    );
  }

  if (user.role === "doctor") {
    return (
      <DoctorDashboard
        user={user}
        onUserChange={setUser}
        onLogout={handleLogout}
      />
    );
  }

  return (
    <StudentDashboard
      user={user}
      onUserChange={setUser}
      onLogout={handleLogout}
    />
  );
}

export default App;
