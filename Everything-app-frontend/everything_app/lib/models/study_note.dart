
class StudyNote {
  final int? id;
  final String title;
  final String content;
  final int? courseId;
  final String? courseName;
  final String? category;
  final String? tags;
  final bool isFavorite;

  /// Seitenbaum: `null` heisst Wurzelseite. Es gibt keinen Ordner-Typ — jeder Knoten ist eine
  /// Seite, eine Seite mit Kindern bekommt ein Aufklapp-Dreieck.
  final int? parentId;
  final int orderIndex;
  final String? icon;

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
    this.parentId,
    this.orderIndex = 0,
    this.icon,
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
      parentId: json['parentId'],
      orderIndex: json['orderIndex'] ?? 0,
      icon: json['icon'],
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

  /// Ohne parentId und orderIndex: die Baumstruktur gehoert dem Server und aendert sich nur
  /// ueber /move und /reorder. Beim ANLEGEN wird parentId separat mitgegeben.
  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'title': title,
      'content': content,
      'courseId': courseId,
      'category': category,
      'tags': tags,
      'icon': icon,
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
    int? parentId,
    int? orderIndex,
    String? icon,
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
      parentId: parentId ?? this.parentId,
      orderIndex: orderIndex ?? this.orderIndex,
      icon: icon ?? this.icon,
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