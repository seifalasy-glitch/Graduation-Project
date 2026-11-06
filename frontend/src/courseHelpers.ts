import {
  Course,
  CourseOffering,
  StudentGroup,
  TeachingAssignment,
  User,
} from "./types";

export type CourseInfo = {
  offering: CourseOffering;
  course: Course;
};

export function getDoctorOfferingIds(
  doctorId: number,
  teaching: TeachingAssignment[],
  groups: StudentGroup[],
) {
  const ids: number[] = [];
  teaching.forEach((item) => {
    if (item.doctor_user_id !== doctorId) {
      return;
    }
    const group = groups.find((g) => g.id === item.group_id);
    if (group !== undefined && !ids.includes(group.offering_id)) {
      ids.push(group.offering_id);
    }
  });
  return ids;
}

export function getCourseInfo(
  offeringIds: number[],
  offerings: CourseOffering[],
  courses: Course[],
) {
  const list: CourseInfo[] = [];
  offeringIds.forEach((id) => {
    const offering = offerings.find((o) => o.id === id);
    if (offering === undefined) {
      return;
    }
    const course = courses.find((c) => c.id === offering.course_id);
    if (course !== undefined) {
      list.push({ offering: offering, course: course });
    }
  });
  return list;
}

export function getLectureDoctor(
  offeringId: number,
  groups: StudentGroup[],
  teaching: TeachingAssignment[],
  users: User[],
) {
  const group = groups.find(
    (g) => g.offering_id === offeringId && g.group_type === "lecture",
  );
  if (group === undefined) {
    return undefined;
  }
  const assignment = teaching.find((t) => t.group_id === group.id);
  if (assignment === undefined) {
    return undefined;
  }
  return users.find((u) => u.id === assignment.doctor_user_id);
}
