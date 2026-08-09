/// Wie dringend ein Aufgabenblock im Verhaeltnis zu seiner Deadline ist.
///
/// Der Scheduler plant seit der harten Deadline-Grenze keinen Block mehr hinter die Deadline
/// (Ausnahme: bereits ueberfaellige Aufgaben, die er nachholt). Damit ist jeder Block, der kurz
/// davor liegt, tatsaechlich die letzte Gelegenheit — und genau das soll man im Kalender sehen,
/// ohne die Aufgabenliste aufzumachen.
library;

enum DeadlineUrgency {
  /// Keine Deadline, oder noch reichlich Zeit.
  none,

  /// Faellig innerhalb der naechsten drei Tage.
  soon,

  /// Letzte Chance: nach diesem Block ist praktisch keine Zeit mehr.
  lastChance,

  /// Die Deadline ist bereits verstrichen.
  overdue,
}

/// Grenze fuer [DeadlineUrgency.lastChance] — ein Tag Restzeit nach dem Blockende.
const Duration _lastChanceWindow = Duration(hours: 24);

/// Grenze fuer [DeadlineUrgency.soon].
const Duration _soonWindow = Duration(hours: 72);

/// Stuft einen Block anhand der Deadline seiner Aufgabe ein.
///
/// [blockEnd] statt [blockStart] ist Absicht: entscheidend ist, wie viel Zeit NACH diesem Block
/// noch bleibt. Ein dreistuendiger Block, der zwei Stunden vor der Deadline endet, ist die letzte
/// Chance — sein Beginn liegt aber fuenf Stunden davor und saehe fuer sich genommen harmlos aus.
///
/// Zusaetzlich zaehlt jeder Block AM Deadline-Tag als letzte Chance, auch wenn rechnerisch noch
/// mehr als 24 Stunden bleiben (Block am fruehen Morgen, Deadline am spaeten Abend darauf gibt es
/// nicht — aber Block heute 08:00, Deadline heute 23:59 sind knapp 16 Stunden und muessen
/// leuchten).
DeadlineUrgency urgencyOf(DateTime? deadline, DateTime blockEnd, DateTime now) {
  if (deadline == null) return DeadlineUrgency.none;
  if (deadline.isBefore(now)) return DeadlineUrgency.overdue;

  final sameDay = deadline.year == blockEnd.year &&
      deadline.month == blockEnd.month &&
      deadline.day == blockEnd.day;
  if (sameDay) return DeadlineUrgency.lastChance;

  // Ein Block hinter der Deadline kann nur beim Nachholen entstehen; er ist erst recht die
  // letzte Chance, deshalb faengt die negative Differenz hier mit ab.
  final remaining = deadline.difference(blockEnd);
  if (remaining <= _lastChanceWindow) return DeadlineUrgency.lastChance;
  if (remaining <= _soonWindow) return DeadlineUrgency.soon;
  return DeadlineUrgency.none;
}

/// Kurzes Faelligkeits-Label in Grossbuchstaben, passend zum Kalender-Typlabel.
///
/// Wortlaut und tagesgenaue Rechnung sind bewusst dieselben wie im Studium-Dashboard, damit
/// dieselbe Aufgabe in beiden Ansichten gleich beschriftet ist.
String deadlineLabel(DateTime deadline, DateTime now) {
  final today = DateTime(now.year, now.month, now.day);
  final due = DateTime(deadline.year, deadline.month, deadline.day);
  final days = due.difference(today).inDays;

  if (days < 0) return 'ÜBERFÄLLIG (${-days} T.)';
  if (days == 0) return 'HEUTE FÄLLIG';
  if (days == 1) return 'MORGEN FÄLLIG';
  return 'IN $days TAGEN';
}
