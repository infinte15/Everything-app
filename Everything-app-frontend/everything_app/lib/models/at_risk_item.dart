/// Eine Aufgabe (oder Gewohnheit), die der Scheduler nicht mehr unterbringen konnte.
///
/// Das Backend rechnet diese Liste bei jedem Lauf aus. Sichtbar war sie bisher nirgends — die
/// Aufgabe blieb einfach liegen, ohne dass irgendwo stand, warum. Genau das ist die Beschwerde
/// "Deadlines werden gerissen": nicht, dass die App es nicht merkt, sondern dass sie es nicht sagt.
class AtRiskItem {
  final int? taskId;
  final int? habitId;
  final String title;
  final int minutes;
  final String? reason;

  const AtRiskItem({
    this.taskId,
    this.habitId,
    required this.title,
    required this.minutes,
    this.reason,
  });

  factory AtRiskItem.fromJson(Map<String, dynamic> json) => AtRiskItem(
        taskId: json['taskId'] as int?,
        habitId: json['habitId'] as int?,
        title: (json['title'] as String?) ?? 'Ohne Titel',
        minutes: (json['minutes'] as int?) ?? 0,
        reason: json['reason'] as String?,
      );

  /// Der Grund in der Sprache des Nutzers. Die Enum-Namen kommen unverändert vom Server;
  /// ein unbekannter Wert darf nicht dazu führen, dass gar nichts dasteht.
  String get reasonText {
    switch (reason) {
      case 'NO_ROOM':
        return 'kein Platz im Zeitplan';
      case 'PAST_DEADLINE':
        return 'Deadline ist bereits vorbei';
      case 'WOULD_MISS_DEADLINE':
        return 'Deadline nicht mehr zu halten';
      case 'OUTSIDE_HORIZON':
        return 'liegt hinter dem Planungsfenster';
      default:
        return 'konnte nicht eingeplant werden';
    }
  }
}
