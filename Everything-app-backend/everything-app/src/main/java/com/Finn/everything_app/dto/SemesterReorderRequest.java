package com.Finn.everything_app.dto;

import java.util.List;

/** Neue Reihenfolge als Liste von IDs; die Position in der Liste ist der neue Index. */
public record SemesterReorderRequest(List<Long> semesterIds) {}
