import { ReactNode } from "react";

type PageHeadProps = {
  eyebrow: string;
  title: string;
  desc: string;
  children?: ReactNode;
};

function PageHead({ eyebrow, title, desc, children }: PageHeadProps) {
  return (
    <div className="page-head">
      <div className="page-head-text">
        <p className="page-eyebrow">{eyebrow}</p>
        <h1 className="page-title">{title}</h1>
        <p className="page-desc">{desc}</p>
      </div>
      {children && <div className="page-head-right">{children}</div>}
    </div>
  );
}

export default PageHead;
