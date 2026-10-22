import "./TermBadge.css";

function TermBadge({ term = "none" }) {
  let text = term;
  if (term === "none") {
    text = "Not offered yet";
  }

  return <span className={"term-badge " + term}>{text}</span>;
}

export default TermBadge;
