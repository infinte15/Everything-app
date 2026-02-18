
class User {
  final int id;
  final String username;
  final String email;
  final DateTime? createdAt;
  final DateTime? lastLogin;

  User({
    required this.id,
    required this.username,
    required this.email,
    this.createdAt,
    this.lastLogin,
  });

  // JSON zu User Object
  factory User.fromJson(Map<String, dynamic> json) {
    return User(
      id: json['id'],
      username: json['username'],
      email: json['email'],
      createdAt: json['createdAt'] != null 
          ? DateTime.parse(json['createdAt']) 
          : null,
      lastLogin: json['lastLogin'] != null 
          ? DateTime.parse(json['lastLogin']) 
          : null,
    );
  }

  // User Object zu JSON
  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'username': username,
      'email': email,
      'createdAt': createdAt?.toIso8601String(),
      'lastLogin': lastLogin?.toIso8601String(),
    };
  }

  // Copy with (für State Updates)
  User copyWith({
    int? id,
    String? username,
    String? email,
    DateTime? createdAt,
    DateTime? lastLogin,
  }) {
    return User(
      id: id ?? this.id,
      username: username ?? this.username,
      email: email ?? this.email,
      createdAt: createdAt ?? this.createdAt,
      lastLogin: lastLogin ?? this.lastLogin,
    );
  }

  @override
  String toString() => 'User(id: $id, username: $username, email: $email)';
}