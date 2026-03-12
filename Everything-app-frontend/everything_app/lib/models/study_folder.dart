class StudyFolder {
  final String id;
  final String name;
  final String emoji;
  final String? color;
  final String? parentId;
  final List<String> childIds;
  final List<String> noteIds;
  final DateTime createdAt;

  StudyFolder({
    required this.id,
    required this.name,
    this.emoji = '📁',
    this.color,
    this.parentId,
    List<String>? childIds,
    List<String>? noteIds,
    DateTime? createdAt,
  })  : childIds = childIds ?? [],
        noteIds = noteIds ?? [],
        createdAt = createdAt ?? DateTime.now();

  StudyFolder copyWith({
    String? id,
    String? name,
    String? emoji,
    String? color,
    String? parentId,
    List<String>? childIds,
    List<String>? noteIds,
    DateTime? createdAt,
  }) {
    return StudyFolder(
      id: id ?? this.id,
      name: name ?? this.name,
      emoji: emoji ?? this.emoji,
      color: color ?? this.color,
      parentId: parentId ?? this.parentId,
      childIds: childIds ?? List.from(this.childIds),
      noteIds: noteIds ?? List.from(this.noteIds),
      createdAt: createdAt ?? this.createdAt,
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'emoji': emoji,
        'color': color,
        'parentId': parentId,
        'childIds': childIds,
        'noteIds': noteIds,
        'createdAt': createdAt.toIso8601String(),
      };

  factory StudyFolder.fromJson(Map<String, dynamic> json) => StudyFolder(
        id: json['id'],
        name: json['name'],
        emoji: json['emoji'] ?? '📁',
        color: json['color'],
        parentId: json['parentId'],
        childIds: List<String>.from(json['childIds'] ?? []),
        noteIds: List<String>.from(json['noteIds'] ?? []),
        createdAt: json['createdAt'] != null
            ? DateTime.parse(json['createdAt'])
            : DateTime.now(),
      );
}
