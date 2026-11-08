export type Role = "admin" | "doctor" | "student";

export type User = {
  id: number;
  university_id: string;
  email: string;
  created_at: string;
  // ASSUMPTION A1: role lives in the user_roles table (ERD v2 User_Role), the api joins it here so the screens can read user.role.
  role: Role;
  // ASSUMPTION A1: name, password, must_change_password, department and active are not in the ERD User table, the screens need them.
  name: string;
  password: string;
  must_change_password: boolean;
  department: string;
  active: boolean;
};

export type UserRole = {
  id: number;
  user_id: number;
  role: Role;
};

export type AcademicProgram = {
  id: number;
  code: string;
  name: string;
  active: boolean;
  // ASSUMPTION A2: department is shown in the Programs table of the UI only.
  department: string;
};

export type AcademicTerm = {
  id: number;
  academic_year: string;
  term_type: string;
  start_date: string;
  end_date: string;
  // ASSUMPTION A3: add_drop_end, exams_start and status are shown on the Terms screen, the ERD has only start_date and end_date.
  add_drop_end: string;
  exams_start: string;
  status: string;
};

export type Course = {
  id: number;
  course_code: string;
  title: string;
  credit_hours: number;
  // ASSUMPTION A4: dept, prereqs, syllabus and topics come from the Enroll screens, not from the ERD.
  dept: string;
  prereqs: string[];
  syllabus: string;
  topics: string[];
};

export type CourseOffering = {
  id: number;
  course_id: number;
  term_id: number;
  section: string;
  capacity: number;
  coursework_max: number;
  // ASSUMPTION A5: schedule, room, emoji and syllabus progress are UI fields, the ERD has no schedule for an offering.
  days: string[];
  start_hour: number;
  end_hour: number;
  room_id: number;
  emoji: string;
  syllabus_progress: number;
};

export type StudentGroup = {
  id: number;
  offering_id: number;
  group_type: string;
  label: string;
};

export type TeachingAssignment = {
  id: number;
  group_id: number;
  doctor_user_id: number;
  starts_at: string;
  ends_at: string;
};

export type Enrollment = {
  id: number;
  student_user_id: number;
  offering_id: number;
  program_id: number;
  status: string;
};

export type GroupMembership = {
  id: number;
  enrollment_id: number;
  group_id: number;
  starts_at: string;
  ends_at: string;
};

export type Room = {
  id: number;
  room_code: string;
  name: string;
  // ASSUMPTION A6: the ERD Room has only room_code and name, network_mode, building, capacity, network, mac_address and status come from the Rooms screen.
  building: string;
  capacity: number;
  network_mode: string;
  network: string;
  mac_address: string;
  status: string;
};

export type AttendanceSession = {
  id: number;
  group_id: number;
  room_id: number;
  teaching_assignment_id: number;
  method: string;
  starts_at: string;
  ends_at: string;
  state: string;
};

export type QrChallenge = {
  id: number;
  session_id: number;
  token_hash: string;
  expires_at: string;
};

export type AttendanceRecord = {
  id: number;
  session_id: number;
  enrollment_id: number;
  status: string;
  method: string;
  marked_at: string;
};

export type AttendanceChange = {
  id: number;
  record_id: number;
  doctor_user_id: number;
  old_status: string;
  new_status: string;
  reason_code: string;
  changed_at: string;
};

export type RubricItem = {
  name: string;
  desc: string;
  points: number;
};

export type GradeItem = {
  id: number;
  offering_id: number;
  title: string;
  item_type: string;
  scope: string;
  max_score: number;
};

export type Assignment = {
  id: number;
  due_at: string;
  // ASSUMPTION A8: description, opens_at and rubric are needed by the assignment screens, the ERD Assignment has only grade_item_id and due_at.
  description: string;
  opens_at: string;
  rubric: RubricItem[];
};

export type AssignmentInfo = GradeItem & Assignment;

export type Submission = {
  id: number;
  grade_item_id: number;
  enrollment_id: number;
  // ASSUMPTION A9: File_Asset is simplified to a file_name text until the backend decides how files are stored.
  file_name: string;
  attempt_no: number;
  late: boolean;
  submitted_at: string;
  created_at: string;
};

export type StudentGrade = {
  id: number;
  grade_item_id: number;
  enrollment_id: number;
  source_entry_id: number | null;
  score: number;
  updated_at: string;
  deleted_at: string | null;
};

export type CourseMaterial = {
  id: number;
  offering_id: number;
  file_name: string;
  lecture_no: number;
  title: string;
  approved_at: string;
  // ASSUMPTION A12: kind, size and the ai_* fields drive the Knowledge Base screen, the ERD keeps AI state in Knowledge_Chunk instead.
  kind: string;
  size: string;
  ai_state: string;
  chunks: number;
  ai_error: string;
  in_ai: boolean;
};

// ASSUMPTION A13: the ERD has no quiz tables, Quiz and QuizQuestion follow the quiz screens.
export type Quiz = {
  id: number;
  offering_id: number;
  title: string;
  questions: number;
  minutes: number;
  opens_at: string;
  closes_at: string;
  status: string;
  grade_item_id: number;
};

export type TicketCategory = {
  id: number;
  name: string;
  active: boolean;
};

export type Ticket = {
  id: number;
  enrollment_id: number | null;
  doctor_user_id: number;
  category_id: number;
  description: string;
  status: string;
  decision: string;
  created_at: string;
  resolved_at: string;
  // ASSUMPTION A14: requester_id, subject and priority are added for the Helpdesk screens, TicketMessage is a new table for the conversation.
  requester_id: number;
  subject: string;
  priority: string;
};

export type TicketMessage = {
  id: number;
  ticket_id: number;
  from_user_id: number;
  text: string;
  note: boolean;
  created_at: string;
};

export type AiConversation = {
  id: number;
  user_id: number;
  offering_id: number;
  title: string;
  created_at: string;
  updated_at: string;
};

export type AiMessage = {
  id: number;
  conversation_id: number;
  sender: string;
  content: string;
  model: string;
  token_count: number;
  // ASSUMPTION A16: citations is a simple list instead of the ERD AI_Citation and Knowledge_Chunk tables.
  citations: string[];
  created_at: string;
};

export type AuditValue = string | number | boolean | null;

export type AuditEvent = {
  id: number;
  actor_user_id: number;
  target_type: string;
  target_id: number;
  action: string;
  before_json: Record<string, AuditValue> | null;
  after_json: Record<string, AuditValue> | null;
  ip: string;
  created_at: string;
  // ASSUMPTION A15: kind and target_label are needed by the Audit Logs screen, the ERD does not have them.
  kind: string;
  target_label: string;
};

// ASSUMPTION A18: alerts are mock data for the Smart Alerts list on the doctor home.
export type Alert = {
  id: number;
  user_id: number;
  icon: string;
  title: string;
  body: string;
  action: string;
  page: string;
  created_at: string;
  unread: boolean;
};

export type MenuItem = {
  id: string;
  label: string;
  icon: string;
};
