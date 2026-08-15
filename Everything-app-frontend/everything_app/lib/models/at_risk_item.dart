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

  /// Wann der erste geplante Block liegt — bei einer überfälligen Aufgabe der Nachholtermin.
  ///
  /// Null, wenn der Scheduler gar keinen Platz gefunden hat.
  final DateTime? plannedStart;

  const AtRiskItem({
    this.taskId,
    this.habitId,
    required this.title,
    required this.minutes,
    this.reason,
    this.plannedStart,
  });

  factory AtRiskItem.fromJson(Map<String, dynamic> json) => AtRiskItem(
        taskId: json['taskId'] as int?,
        habitId: json['habitId'] as int?,
        title: (json['title'] as String?) ?? 'Ohne Titel',
        minutes: (json['minutes'] as int?) ?? 0,
        reason: json['reason'] as String?,
        plannedStart: json['plannedStart'] != null
            ? DateTime.parse(json['plannedStart'] as String)
            : null,
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
        return 'kommt später dran';
      default:
        return 'konnte nicht eingeplant werden';
    }
  }

  /// Ist hier wirklich ein Termin in Gefahr — oder ist die Aufgabe nur noch nicht dran?
  ///
  /// Der Unterschied ist der ganze Punkt: gewarnt wird ausschließlich bei einer Deadline, die
  /// der Scheduler nicht mehr halten kann. Vorher landete auch jede Aufgabe OHNE Deadline im
  /// Warnband, sobald sie im 14-Tage-Fenster keinen Platz fand — sie kann aber gar nichts
  /// reißen, sie kommt schlicht später. Genau das las sich als „eine Aufgabe passt nicht rein".
  bool get isDeadlineRisk => isOverdue || reason == 'WOULD_MISS_DEADLINE';

  /// Die Deadline ist bereits verstrichen und die Aufgabe steht immer noch offen.
  ///
  /// Die schärfste der drei Lagen und deshalb überall eigenständig behandelt: rot statt amber,
  /// eigenes Band, und mit dem Nachholtermin statt einer Klage. Vorher stand darüber
  /// „schafft ihre Deadline nicht" — in der falschen Zeitform für etwas, das schon passiert ist.
  bool get isOverdue => reason == 'PAST_DEADLINE';

  /// Der Nachholtermin in Worten, oder null, wenn es keinen gibt.
  String? get plannedStartText {
    final s = plannedStart;
    if (s == null) return null;

    final now = DateTime.now();
    final heute = DateTime(now.year, now.month, now.day);
    final tag = DateTime(s.year, s.month, s.day);
    final uhr = '${s.hour.toString().padLeft(2, '0')}:'
        '${s.minute.toString().padLeft(2, '0')}';

    final tageHin = tag.difference(heute).inDays;
    if (tageHin == 0) return 'heute $uhr';
    if (tageHin == 1) return 'morgen $uhr';
    return '${s.day}.${s.month}. $uhr';
  }
}
