
class StudyNote {
  final int? id;
  final String title;
  final String content;
  final int? courseId;
  final String? courseName;
  final String? category;
  final String? tags;
  final bool isFavorite;
  final DateTime? createdAt;
  final DateTime? updatedAt;
  final DateTime? lastReviewedAt;

  StudyNote({
    this.id,
    required this.title,
    required this.content,
    this.courseId,
    this.courseName,
    this.category,
    this.tags,
    this.isFavorite = false,
    this.createdAt,
    this.updatedAt,
    this.lastReviewedAt,
  });

  // JSON zu StudyNote
  factory StudyNote.fromJson(Map<String, dynamic> json) {
    return StudyNote(
      id: json['id'],
      title: json['title'],
      content: json['content'],
      courseId: json['courseId'],
      courseName: json['courseName'],
      category: json['category'],
      tags: json['tags'],
      isFavorite: json['isFavorite'] ?? false,
      createdAt: json['createdAt'] != null
          ? DateTime.parse(json['createdAt'])
          : null,
      updatedAt: json['updatedAt'] != null
          ? DateTime.parse(json['updatedAt'])
          : null,
      lastReviewedAt: json['lastReviewedAt'] != null
          ? DateTime.parse(json['lastReviewedAt'])
          : null,
    );
  }

  // StudyNote zu JSON
  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'title': title,
      'content': content,
      'courseId': courseId,
      'category': category,
      'tags': tags,
      'isFavorite': isFavorite,
    };
  }

  // Copy with
  StudyNote copyWith({
    int? id,
    String? title,
    String? content,
    int? courseId,
    String? courseName,
    String? category,
    String? tags,
    bool? isFavorite,
    DateTime? createdAt,
    DateTime? updatedAt,
    DateTime? lastReviewedAt,
  }) {
    return StudyNote(
      id: id ?? this.id,
      title: title ?? this.title,
      content: content ?? this.content,
      courseId: courseId ?? this.courseId,
      courseName: courseName ?? this.courseName,
      category: category ?? this.category,
      tags: tags ?? this.tags,
      isFavorite: isFavorite ?? this.isFavorite,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
      lastReviewedAt: lastReviewedAt ?? this.lastReviewedAt,
    );
  }

  // Parse Tags als Liste
  List<String> get tagList {
    if (tags == null || tags!.isEmpty) return [];
    return tags!.split(',').map((t) => t.trim()).toList();
  }

  @override
  String toString() => 'StudyNote(id: $id, title: $title)';
}