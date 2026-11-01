type SwitchProps = {
  on: boolean;
  onChange: (value: boolean) => void;
};

function Switch({ on, onChange }: SwitchProps) {
  return (
    <button
      type="button"
      className={on ? "switch on" : "switch"}
      onClick={() => onChange(!on)}
    >
      <span className="switch-knob" />
    </button>
  );
}

export default Switch;
