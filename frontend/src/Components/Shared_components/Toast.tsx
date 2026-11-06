import Icon from "./Icon";

type ToastProps = {
  text: string;
  onClose: () => void;
};

function Toast({ text, onClose }: ToastProps) {
  return (
    <div className="toast">
      <Icon name="check" />
      <span className="toast-text">{text}</span>
      <button className="toast-close" onClick={onClose}>
        <Icon name="x" />
      </button>
    </div>
  );
}

export default Toast;
