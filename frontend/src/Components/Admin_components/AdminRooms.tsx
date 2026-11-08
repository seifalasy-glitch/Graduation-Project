import { useEffect, useState } from "react";
import "./AdminRooms.css";
import PageHead from "../Shared_components/PageHead";
import Icon from "../Shared_components/Icon";
import RoomForm from "./RoomForm";
import { addRoom, getRooms } from "../../academicApi";
import { Room } from "../../types";

function AdminRooms() {
  const [rooms, setRooms] = useState<Room[]>([]);
  const [showForm, setShowForm] = useState(false);
  const [banner, setBanner] = useState("");
  const [search, setSearch] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    async function loadRooms() {
      try {
        setRooms(await getRooms());
      } catch (err) {
        setError("Can't load rooms. Did you run: npm run server ?");
      }
    }
    loadRooms();
  }, []);

  useEffect(() => {
    if (banner === "") {
      return;
    }
    const timer = setTimeout(() => setBanner(""), 4500);
    return () => clearTimeout(timer);
  }, [banner]);

  async function handleSave(room: Omit<Room, "id">) {
    try {
      const saved = await addRoom(room);
      setRooms([saved, ...rooms]);
      setShowForm(false);
      setBanner(saved.name);
    } catch (err) {
      setError("Couldn't save the room, try again.");
    }
  }

  const text = search.toLowerCase();
  const visible = rooms.filter((room) =>
    (room.name + room.building + room.network).toLowerCase().includes(text),
  );
  const routers = rooms.filter((room) => room.network_mode === "router").length;

  const stats = [
    { label: "Active rooms", value: rooms.length },
    { label: "Local router", value: routers },
    { label: "Campus Wi-Fi", value: rooms.length - routers },
  ];

  return (
    <div>
      <PageHead
        eyebrow="Admin · Infrastructure"
        title="Rooms Setup"
        desc="Rooms and the network profile used to verify in-class QR attendance."
      >
        <button className="btn-primary" onClick={() => setShowForm(true)}>
          + Add New Room Profile
        </button>
      </PageHead>

      {error !== "" && <p className="msg-error">{error}</p>}

      {banner !== "" && (
        <div className="rooms-banner">
          <span className="rooms-banner-check">✓</span>
          <span className="rooms-banner-text">
            <b>{banner}</b> — Room is Active & Ready for Doctors' Attendance
            Sessions
          </span>
          <button className="toast-close" onClick={() => setBanner("")}>
            <Icon name="x" />
          </button>
        </div>
      )}

      <div className="rooms-stats">
        {stats.map((stat) => (
          <div key={stat.label} className="card rooms-stat">
            <p className="rooms-stat-label">{stat.label}</p>
            <p className="rooms-stat-value">{stat.value}</p>
          </div>
        ))}
      </div>

      <section className="rooms-section">
        <div className="rooms-section-head">
          <h2 className="rooms-h2">Active rooms</h2>
          <label className="rooms-filter">
            <Icon name="search" />
            <input
              className="rooms-filter-input"
              value={search}
              placeholder="Filter rooms"
              onChange={(e) => setSearch(e.target.value)}
            />
          </label>
        </div>
        <div className="table-wrap rooms-table">
          <table className="table">
            <thead>
              <tr>
                <th>Room</th>
                <th>Building</th>
                <th>Capacity</th>
                <th>Network mode</th>
                <th>Network</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {visible.map((room) => (
                <tr
                  key={room.id}
                  className={banner === room.name ? "rooms-new" : ""}
                >
                  <td className="rooms-strong">{room.name}</td>
                  <td className="muted">{room.building}</td>
                  <td className="muted">{room.capacity}</td>
                  <td>
                    <span
                      className={
                        room.network_mode === "router"
                          ? "pill purple"
                          : "pill blue"
                      }
                    >
                      {room.network_mode === "router"
                        ? "Local Router"
                        : "Campus Wi-Fi"}
                    </span>
                  </td>
                  <td className="rooms-network">
                    {room.network}
                    {room.mac_address !== "" && (
                      <span className="rooms-mac">{room.mac_address}</span>
                    )}
                  </td>
                  <td className="muted">
                    <span
                      className={
                        room.status === "Active"
                          ? "rooms-dot"
                          : "rooms-dot pending"
                      }
                    />
                    {room.status}
                  </td>
                </tr>
              ))}
              {visible.length === 0 && (
                <tr>
                  <td colSpan={6} className="empty">
                    No rooms match "{search}".
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      {showForm && (
        <RoomForm
          existing={rooms.map((room) => room.name.toLowerCase())}
          onClose={() => setShowForm(false)}
          onSave={handleSave}
        />
      )}
    </div>
  );
}

export default AdminRooms;
