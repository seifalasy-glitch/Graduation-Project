# EduSync (Graduation Project - Frontend training)

React (create-react-app) frontend with a json-server mock backend.

## Run

```bash
npm run setup     # once: installs everything inside myapp2
npm run server    # terminal 1: mock server on http://localhost:3001
npm start         # terminal 2: React on http://localhost:3000
```

## Test accounts

| Email | Password | Role |
|---|---|---|
| admin@edusync.com | 123456 | admin |
| ahmed@edusync.com | 123456 | doctor |
| mona@edusync.com | 123456 | doctor |
| omar@edusync.com | 123456 | student |
| maya@edusync.com | 098765 | student |

## Features

- Login by role (student / doctor / admin), Settings (change password)
- Admin > Courses: add (+), search, delete courses
- Admin > Enrollments: assign a course to a doctor in one term (winter / spring / summer)
- Doctor > My Courses: assigned courses with a term badge
- Student > + Enroll in Courses: all courses, search, enroll; My Courses shows enrolled ones with their term
