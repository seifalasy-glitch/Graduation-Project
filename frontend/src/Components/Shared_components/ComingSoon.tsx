type ComingSoonProps = {
  title: string;
  onBack: () => void;
};

function ComingSoon({ title, onBack }: ComingSoonProps) {
  return (
    <div className="coming-soon">
      <p className="coming-soon-title">{title}</p>
      <p className="coming-soon-text">This workflow screen is coming next.</p>
      <button className="btn-ghost coming-soon-btn" onClick={onBack}>
        ← Back to dashboard
      </button>
    </div>
  );
}

export default ComingSoon;
