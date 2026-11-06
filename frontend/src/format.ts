const months = [
  "Jan",
  "Feb",
  "Mar",
  "Apr",
  "May",
  "Jun",
  "Jul",
  "Aug",
  "Sep",
  "Oct",
  "Nov",
  "Dec",
];

export function shortDate(text: string) {
  const date = new Date(text);
  return months[date.getMonth()] + " " + date.getDate();
}

export function longDate(text: string) {
  const date = new Date(text);
  return shortDate(text) + ", " + date.getFullYear();
}

export function timeText(text: string) {
  const date = new Date(text);
  let hours = date.getHours();
  const minutes = date.getMinutes();
  let suffix = "AM";
  if (hours >= 12) {
    suffix = "PM";
  }
  if (hours > 12) {
    hours = hours - 12;
  }
  if (hours === 0) {
    hours = 12;
  }
  let minutesText = String(minutes);
  if (minutes < 10) {
    minutesText = "0" + minutes;
  }
  return hours + ":" + minutesText + " " + suffix;
}

export function hourText(hour: number) {
  const whole = Math.floor(hour);
  const minutes = Math.round((hour - whole) * 60);
  let shown = whole;
  if (whole > 12) {
    shown = whole - 12;
  }
  let minutesText = String(minutes);
  if (minutes < 10) {
    minutesText = "0" + minutes;
  }
  let suffix = "AM";
  if (whole >= 12) {
    suffix = "PM";
  }
  return shown + ":" + minutesText + " " + suffix;
}

export function timeAgo(text: string) {
  const diff = Date.now() - new Date(text).getTime();
  const minutes = Math.floor(diff / 60000);
  if (minutes < 60) {
    return minutes + "m ago";
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    return hours + "h ago";
  }
  const days = Math.floor(hours / 24);
  if (days === 1) {
    return "Yesterday";
  }
  if (days < 7) {
    return days + "d ago";
  }
  return Math.floor(days / 7) + "w ago";
}

export function termName(termType: string, academicYear: string) {
  const years = academicYear.split("/");
  let year = years[0];
  if (termType !== "fall" && years.length > 1) {
    year = years[1];
  }
  return termType.charAt(0).toUpperCase() + termType.slice(1) + " " + year;
}

export function weekOfTerm(start: string, end: string) {
  const day = 24 * 60 * 60 * 1000;
  const startTime = new Date(start).getTime();
  const total = Math.round((new Date(end).getTime() - startTime) / (7 * day));
  let week = Math.floor((Date.now() - startTime) / (7 * day)) + 1;
  if (week < 1) {
    week = 1;
  }
  if (week > total) {
    week = total;
  }
  return { week: week, total: total };
}

export function greetingText() {
  const hour = new Date().getHours();
  if (hour < 12) {
    return "Good morning";
  }
  if (hour < 18) {
    return "Good afternoon";
  }
  return "Good evening";
}

export function firstName(name: string) {
  const parts = name.split(" ");
  if (parts[0] === "Dr." || parts[0] === "Prof.") {
    return parts[1];
  }
  return parts[0];
}

export function lastName(name: string) {
  const parts = name.split(" ");
  return parts[parts.length - 1];
}
