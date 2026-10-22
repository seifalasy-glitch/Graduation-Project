import "./CourseCard.css";
import TermBadge from "./TermBadge";

function CourseCard({ name, code, hours, term = "none", children }) {
  return (
    <div className="course-card">
      <div className="course-card-top">
        <span className="course-card-code">{code}</span>
        <span className="course-card-term">
          <TermBadge term={term} />
        </span>
      </div>
      <h3 className="course-card-name">{name}</h3>
      <p className="course-card-hours">{hours} credit hours</p>
      {children}
    </div>
  );
}

export default CourseCard;
