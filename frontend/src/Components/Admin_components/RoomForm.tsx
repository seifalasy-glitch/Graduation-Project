import { useState } from "react";
import Icon from "../Shared_components/Icon";
import { Room } from "../../types";

const subnets = [
  "CS-Labs · 10.12.3.0/24",
  "Engineering · 10.8.0.0/22",
  "Main-Halls · 10.1.0.0/22",
  "Humanities · 10.20.2.0/24",
  "Library · 10.30.0.0/23",
];

type RoomFormProps = {
  existing: string[];
  onClose: () => void;
  onSave: (room: Omit<Room, "id">) => void;
};

function isMacAddress(text: string) {
  const parts = text.toUpperCase().split(":");
  if (parts.length !== 6) {
    return false;
  }
  return parts.every(
    (part) =>
      part.length === 2 &&
      "0123456789ABCDEF".includes(part[0]) &&
      "0123456789ABCDEF".includes(part[1]),
  );
}

function RoomForm({ existing, onClose, onSave }: RoomFormProps) {
  const [name, setName] = useState("");
  const [roomCode, setRoomCode] = useState("");
  const [building, setBuilding] = useState("");
  const [capacity, setCapacity] = useState("");
  const [mode, setMode] = useState("router");
  const [ssid, setSsid] = useState("");
  const [mac, setMac] = useState("");
  const [subnet, setSubnet] = useState("");
  const [touched, setTouched] = useState(false);
  const [saving, setSaving] = useState(false);

  const duplicate = existing.includes(name.trim().toLowerCase());
  const macBad = mac.length > 0 && !isMacAddress(mac);
  const seats = Number(capacity);
  const basicOk =
    name.trim() !== "" &&
    !duplicate &&
    roomCode.trim() !== "" &&
    building.trim() !== "" &&
    seats > 0;
  let networkOk = subnet !== "";
  if (mode === "router") {
    networkOk = ssid.trim() !== "" && isMacAddress(mac);
  }

  function handleSubmit() {
    setTouched(true);
    if (!basicOk || !networkOk) {
      return;
    }
    setSaving(true);
    onSave({
      room_code: roomCode.trim().toUpperCase(),
      name: name.trim(),
      building: building.trim(),
      capacity: seats,
      network_mode: mode,
      network: mode === "router" ? ssid.trim() : subnet,
      mac_address: mode === "router" ? mac.toUpperCase() : "",
      status: "Active",
    });
  }

  return (
    <div>
      <div className="overlay" onClick={onClose} />
      <div className="drawer room-drawer">
        <div className="room-head">
          <p className="room-head-small">Rooms Setup</p>
          <h2 className="room-head-title">Add New Room Profile</h2>
          <button className="modal-close" onClick={onClose}>
            <Icon name="x" />
          </button>
        </div>

        <div className="room-body">
          <p className="room-label">Basic info</p>

          <label className="field-label room-gap">Room Name</label>
          <input
            className="input"
            value={name}
            placeholder="e.g., Hall C-110"
            onChange={(e) => setName(e.target.value)}
          />
          {touched && name.trim() === "" && (
            <p className="room-error">Room name is required.</p>
          )}
          {name !== "" && duplicate && (
            <p className="room-error">A room with this name already exists.</p>
          )}

          <label className="field-label room-gap">Room Code</label>
          <input
            className="input"
            value={roomCode}
            placeholder="e.g., C-110"
            onChange={(e) => setRoomCode(e.target.value)}
          />
          {touched && roomCode.trim() === "" && (
            <p className="room-error">Room code is required.</p>
          )}

          <div className="room-row room-gap">
            <div className="room-building">
              <label className="field-label">Building</label>
              <input
                className="input"
                value={building}
                placeholder="e.g., Engineering C"
                onChange={(e) => setBuilding(e.target.value)}
              />
              {touched && building.trim() === "" && (
                <p className="room-error">Building is required.</p>
              )}
            </div>
            <div className="room-capacity">
              <label className="field-label">Capacity</label>
              <input
                className="input"
                type="number"
                min={1}
                value={capacity}
                placeholder="Seats"
                onChange={(e) => setCapacity(e.target.value)}
              />
              {touched && !(seats > 0) && (
                <p className="room-error">Enter seats.</p>
              )}
            </div>
          </div>

          <p className="room-label room-section">Network Infrastructure Mode</p>
          <p className="room-desc">
            Students must be on this network for their QR scan to count as
            present.
          </p>
          <div className="room-modes">
            <button
              type="button"
              className={mode === "router" ? "room-mode on" : "room-mode"}
              onClick={() => setMode("router")}
            >
              <span className="room-mode-icon">
                <Icon name="wifi" size={20} />
              </span>
              <span className="room-mode-text">
                <b>Local Router</b>
                <span>A dedicated access point installed in the room.</span>
              </span>
              <span className="room-radio">
                {mode === "router" && <span className="room-radio-dot" />}
              </span>
            </button>
            <button
              type="button"
              className={
                mode === "campus" ? "room-mode on right" : "room-mode right"
              }
              onClick={() => setMode("campus")}
            >
              <span className="room-mode-icon">
                <Icon name="wifi" size={20} />
              </span>
              <span className="room-mode-text">
                <b>Campus Wi-Fi</b>
                <span>Use a predefined subnet of the campus network.</span>
              </span>
              <span className="room-radio">
                {mode === "campus" && <span className="room-radio-dot" />}
              </span>
            </button>
          </div>

          <div className="room-network">
            {mode === "router" ? (
              <div>
                <label className="field-label">Local Router SSID</label>
                <input
                  className="input"
                  value={ssid}
                  placeholder="e.g., EDU-C110"
                  onChange={(e) => setSsid(e.target.value)}
                />
                {touched && ssid.trim() === "" && (
                  <p className="room-error">SSID is required.</p>
                )}
                <label className="field-label room-gap">
                  MAC Address
                  <span className="field-hint">Format AA:BB:CC:DD:EE:FF</span>
                </label>
                <input
                  className={macBad ? "input bad room-mono" : "input room-mono"}
                  value={mac}
                  placeholder="00:1A:2B:3C:4D:5E"
                  onChange={(e) => setMac(e.target.value)}
                />
                {macBad && (
                  <p className="room-error">Enter a valid MAC address.</p>
                )}
                {touched && mac === "" && (
                  <p className="room-error">MAC address is required.</p>
                )}
              </div>
            ) : (
              <div>
                <label className="field-label">
                  Select Predefined Campus Wi-Fi Subnet
                </label>
                <select
                  className={subnet === "" ? "input placeholder" : "input"}
                  value={subnet}
                  onChange={(e) => setSubnet(e.target.value)}
                >
                  <option value="">Choose a subnet…</option>
                  {subnets.map((item) => (
                    <option key={item}>{item}</option>
                  ))}
                </select>
                {touched && subnet === "" && (
                  <p className="room-error">Select a subnet.</p>
                )}
              </div>
            )}
          </div>
        </div>

        <div className="room-foot">
          <button className="btn-ghost" onClick={onClose}>
            Cancel
          </button>
          <button
            className="btn-primary"
            disabled={saving}
            onClick={handleSubmit}
          >
            {saving ? "Saving…" : "✓ Save Configuration"}
          </button>
        </div>
      </div>
    </div>
  );
}

export default RoomForm;
