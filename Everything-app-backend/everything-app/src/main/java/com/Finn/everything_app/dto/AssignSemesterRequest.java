package com.Finn.everything_app.dto;

/**
 * Body von PUT /api/study/courses/{id}/semester.
 *
 * Eigener Endpunkt statt eines Feldes in CourseDTO, weil updateCourse partiell arbeitet
 * (null = unverändert) und „keinem Semester zugeordnet" damit nicht ausdrückbar wäre.
 * Gleiches Muster wie PUT /api/calendar/events/{id}/pin.
 *
 * @param semesterId null = Zuordnung aufheben.
 */
public record AssignSemesterRequest(Long semesterId) {}
