# Learning Path

This guide is for understanding the project without diving into implementation details first.

## 1. Product Flow

Understand the user journey:

```text
project -> drawing -> version upload -> review task -> issues -> remediation -> report -> version comparison
```

## 2. Service Responsibilities

- Frontend: user workspace and drawing preview.
- Backend: business workflow, database, rules, reports, and audit trail.
- CAD Worker: CAD file parsing.
- Vision Worker: YOLOv8 symbol detection.
- Database: persistent state.
- Object storage: future location for drawings, reports, rendered images, and parser artifacts.

## 3. Data Objects

Start with these concepts:

- Project
- Drawing
- DrawingVersion
- ParsedEntity
- ReviewTask
- ReviewIssue
- ReviewRule
- ReportDocument

## 4. Troubleshooting Order

When something is wrong, locate the layer first:

1. UI problem.
2. API problem.
3. Task queue problem.
4. CAD Worker problem.
5. Rule behavior problem.
6. Data or deployment problem.

## 5. Requirement Writing Template

Use this format:

```text
In which scenario,
the system should read which data,
check which condition,
produce which result,
show it where,
and save it how.
```
