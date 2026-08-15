import 'dart:async';
import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:intl/intl.dart';
import '../../providers/calendar_provider.dart';
import '../../config/app_theme.dart';
import '../../models/at_risk_item.dart';
import '../../models/calendar_event.dart';
import '../../utils/deadline_urgency.dart';
import '../../widgets/create_event_sheet.dart';
import '../../widgets/pointer_aware_draggable.dart';

// ─── Constants ───────────────────────────────────────────────────────────────

const double kHourHeight = 64.0;
const double kTimeGutterWidth = 52.0;
const int kDayStart = 0;
const int kDayEnd = 24;

// ─── Event Type Colours ──────────────────────────────────────────────────────

Color _typeColor(String type) {
  switch (type.toUpperCase()) {
    case 'TASK':
    case 'STRATEGY':
      return AppTheme.primaryColor;
    case 'HABIT':
    case 'FINANCE':
      return AppTheme.financeColor;
    case 'WORKOUT':
    case 'GYM':
      return AppTheme.sportsColor;
    case 'STUDY':
    case 'CLASS':
      // Nur der Rückfall: das Backend setzt bei Vorlesungen immer die Modulfarbe,
      // und event.color schlägt _typeColor.
      return AppTheme.studyColor;
    case 'PROJECT':
      return AppTheme.projectsColor;
    default:
      return AppTheme.primaryColor;
  }
}

IconData _typeIcon(String type) {
  switch (type.toUpperCase()) {
    case 'TASK':
      return Icons.check_circle_outline_rounded;
    case 'HABIT':
      return Icons.repeat_rounded;
    case 'WORKOUT':
      return Icons.fitness_center_rounded;
    case 'STUDY':
      return Icons.menu_book_rounded;
    case 'CLASS':
      return Icons.school_rounded;
    case 'PROJECT':
      return Icons.folder_special_outlined;
    default:
      return Icons.event_rounded;
  }
}

// ─── Dringlichkeit ───────────────────────────────────────────────────────────
// Ein Aufgabenblock kurz vor seiner Deadline soll auf einen Blick zu erkennen sein. Die Stufe
// wird hier und nicht im Backend berechnet: sie haengt an "jetzt" und wandert im Minutentakt.

/// Die Faelligkeitsstufe eines Termins, oder [DeadlineUrgency.none] wenn er keine Aufgabe ist.
DeadlineUrgency _urgencyFor(CalendarEvent e, DateTime now) =>
    e.isTask && !e.isCompleted
        ? urgencyOf(e.relatedTaskDeadline, e.endTime, now)
        : DeadlineUrgency.none;

/// Hervorhebungswuerdig? Nur die beiden obersten Stufen — sonst leuchtet der halbe Kalender.
bool _isUrgent(CalendarEvent e, DateTime now) {
  final u = _urgencyFor(e, now);
  return u == DeadlineUrgency.lastChance || u == DeadlineUrgency.overdue;
}

/// Akzentfarbe der Hervorhebung, oder null wenn nicht dringlich.
///
/// Die Prioritaetsfarbe deckt sich mit getColorForTask im Backend, der Block wechselt beim
/// Hervorheben also nicht die Farbfamilie. Ueberfaellig gewinnt immer die Fehlerfarbe.
Color? _urgentAccent(CalendarEvent e, DateTime now) {
  final u = _urgencyFor(e, now);
  if (u == DeadlineUrgency.overdue) return AppTheme.errorColor;
  if (u != DeadlineUrgency.lastChance) return null;
  return e.relatedTaskPriority != null
      ? AppTheme.getPriorityColor(e.relatedTaskPriority!)
      : (e.color != null ? e.colorObject : _typeColor(e.eventType));
}

enum _CalView { day, week, month }

// ─── Root Screen ─────────────────────────────────────────────────────────────

class CalendarScreen extends StatefulWidget {
  const CalendarScreen({super.key});

  @override
  State<CalendarScreen> createState() => _CalendarScreenState();
}

class _CalendarScreenState extends State<CalendarScreen> {
  _CalView _view = _CalView.week;
  late PageController _pageController;

  // Nur der Offset-WERT lebt hier, kein ScrollController. Ein einzelner Controller kann nicht
  // funktionieren: die PageView hält ~3 Seiten gleichzeitig am Leben, und ein Controller darf
  // immer nur an genau eine ScrollView gebunden sein. Jede _TimelineGrid besitzt deshalb ihren
  // eigenen Controller und meldet den Offset hierher zurück, damit er beim Blättern und beim
  // Wechsel der Ansicht erhalten bleibt.
  late double _timelineOffset;

  @override
  void initState() {
    super.initState();
    _pageController = PageController(initialPage: 10000);
    final now = DateTime.now();
    // scroll to make current hour visible (~2 hours before now)
    _timelineOffset = ((now.hour - 2).clamp(0, 22)) * kHourHeight;

    WidgetsBinding.instance.addPostFrameCallback((_) {
      final cal = context.read<CalendarProvider>();
      cal.setSelectedDay(now);
      cal.loadEventsForMonth(now);
      cal.ensureScheduleGenerated();
    });
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }
void _navigate(int delta) {
  _pageController.animateToPage(
    _pageController.page!.toInt() + delta,
    duration: const Duration(milliseconds: 300),
    curve: Curves.easeInOut,
  );
}

  @override
  Widget build(BuildContext context) {
    final cal = context.watch<CalendarProvider>();
    final selected = cal.selectedDay ?? DateTime.now();

    return Scaffold(
      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
      body: SafeArea(
        child: Column(
          children: [
            _CalendarHeader(
              view: _view,
              selectedDay: selected,
              onViewChanged: (v) => setState(() => _view = v),
              onNavigate: _navigate,
              onToday: () {
                final now = DateTime.now();
                cal.setSelectedDay(now);
                cal.setFocusedDay(now);
                cal.loadEventsForMonth(now);

                _pageController.jumpToPage(10000);
              },
            ),
            // Zwei schmale Bänder direkt unter dem Kopf: "wird gerade neu geplant" und
            // "diese Deadline ist nicht mehr zu halten". Beides war vorher unsichtbar — der
            // Kalender sortierte sich irgendwann still um, und liegen gebliebene Aufgaben
            // verschwanden wortlos.
            //
            // Das Warnband hängt bewusst an atRiskDeadlines und nicht an atRisk: eine Aufgabe,
            // die nur noch nicht eingeplant ist, gefährdet nichts (siehe AtRiskItem.isDeadlineRisk).
            if (cal.isReplanning) const _ReplanIndicator(),
            // Überfälliges zuerst und in Rot: ein gerissener Termin ist die schärfere Lage.
            if (cal.atRiskOverdue.isNotEmpty)
              _AtRiskBanner(items: cal.atRiskOverdue, overdue: true),
            if (cal.atRiskUpcoming.isNotEmpty)
              _AtRiskBanner(items: cal.atRiskUpcoming, overdue: false),
            if (_view == _CalView.week)
              _WeekStrip(
                selected: selected,
                onDayTap: (d) {
                  cal.setSelectedDay(d);
                  cal.setFocusedDay(d);
                },
              ),
            Expanded(
  child: PageView.builder(
    controller: _pageController,
    onPageChanged: (index) {
      // Nur den Provider informieren, damit der Header (Monat/Jahr) sich aktualisiert
      final cal = context.read<CalendarProvider>();
      final delta = index - 10000;
      final now = DateTime.now(); // Basis ist heute
      
      DateTime targetDate;
      if (_view == _CalView.day) {
        targetDate = now.add(Duration(days: delta));
      } else if (_view == _CalView.week) {
        targetDate = now.add(Duration(days: delta * 7));
      } else {
        targetDate = DateTime(now.year, now.month + delta, now.day);
      }
      
      // Vor setFocusedDay lesen — danach ist der alte Monat weg.
      final previous = cal.focusedDay;
      cal.setSelectedDay(targetDate);
      cal.setFocusedDay(targetDate);
      // Auch die Tagesansicht muss nachladen, sonst ist die Seite jenseits des geladenen
      // Monats leer. Aber nur beim echten Monatswechsel, nicht bei jedem Wisch.
      if (targetDate.month != previous.month || targetDate.year != previous.year) {
        cal.loadEventsForMonth(targetDate);
      }
    },
    itemBuilder: (context, index) {
      final cal = context.watch<CalendarProvider>();
      final delta = index - 10000;
      final now = DateTime.now();

      DateTime pageDate;
      if (_view == _CalView.day) {
        pageDate = now.add(Duration(days: delta));
      } else if (_view == _CalView.week) {
        pageDate = now.add(Duration(days: delta * 7));
      } else {
        pageDate = DateTime(now.year, now.month + delta, 1);
      }


      return _view == _CalView.day
          ? _DayTimeline(
              cal: cal,
              selected: pageDate,
              initialOffset: _timelineOffset,
              onOffsetChanged: (o) => _timelineOffset = o,
            )
          : _view == _CalView.week
              ? _WeekTimeline(
                  cal: cal,
                  selected: pageDate,
                  initialOffset: _timelineOffset,
                  onOffsetChanged: (o) => _timelineOffset = o,
                )
              : _MonthView(
                  cal: cal,
                  selected: pageDate,
                  onDayTap: (d) {
                    final cal = context.read<CalendarProvider>();
                    cal.setSelectedDay(d);
                    cal.setFocusedDay(d);
  
                  final now = DateTime.now();
                  final differenceInDays = DateTime(d.year, d.month, d.day)
                    .difference(DateTime(now.year, now.month, now.day))
                    .inDays;
  

                  setState(() => _view = _CalView.day);
  

                  _pageController.jumpToPage(10000 + differenceInDays);
                },
      );
    },
  ),
),
          ],
        ),
      ),
    );
  }

}

// ─── Neuplanung läuft ────────────────────────────────────────────────────────

/// Zwei Pixel Fortschrittsbalken unter dem Kopf, solange auf eine Neuplanung gewartet wird.
///
/// Bewusst so unauffällig: der Lauf dauert typisch etwa eine Sekunde, blockiert nichts und
/// verlangt keine Entscheidung. Ein Spinner über dem halben Bildschirm wäre für einen
/// Hintergrundvorgang die falsche Lautstärke — sichtbar muss er trotzdem sein, sonst wirkt der
/// Kalender, der sich gleich umsortiert, wie ein Fehler.
class _ReplanIndicator extends StatelessWidget {
  const _ReplanIndicator();

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return SizedBox(
      height: 2,
      child: LinearProgressIndicator(
        minHeight: 2,
        backgroundColor: Colors.transparent,
        valueColor: AlwaysStoppedAnimation<Color>(
          theme.colorScheme.primary.withValues(alpha: 0.7),
        ),
      ),
    );
  }
}

// ─── Überfällig und nicht eingeplant ─────────────────────────────────────────

/// Aufgaben, bei denen ein Termin auf dem Spiel steht — in zwei Schärfegraden.
///
/// Diese Liste rechnet das Backend seit jeher bei jedem Lauf aus; angezeigt wurde sie nie, für
/// den Nutzer sah es aus, als hätte die App die Aufgabe schlicht vergessen. Seit den Nachläufen
/// im Scheduler ist sie eine echte Aussage und kein Rauschen mehr.
///
/// Getrennt wird bewusst nach [AtRiskItem.isOverdue]:
///
/// * **überfällig** — rot. Der Termin ist bereits gerissen; das ist nichts, was noch abgewendet
///   werden kann, sondern etwas, das jetzt nachgeholt wird. Das Band nennt deshalb den
///   Nachholtermin statt einer Klage.
/// * **in Gefahr** — amber. Die Deadline steht noch aus, aber davor war auch mit gelockerten
///   Arbeitszeiten kein Platz.
///
/// Vorher lagen beide in einem Band mit dem Text „schafft ihre Deadline nicht" — in der falschen
/// Zeitform für etwas, das längst passiert ist, und in derselben Farbe wie eine bloße Warnung.
class _AtRiskBanner extends StatelessWidget {
  final List<AtRiskItem> items;

  /// Rot für Überfälliges, Amber für Gefährdetes.
  final bool overdue;

  const _AtRiskBanner({required this.items, required this.overdue});

  static const _rot   = Color(0xFFE5484D);
  static const _amber = Color(0xFFFF9F1C);

  Color get _farbe => overdue ? _rot : _amber;

  IconData get _symbol =>
      overdue ? Icons.error_outline_rounded : Icons.warning_amber_rounded;

  String get _titel {
    if (!overdue) {
      return items.length == 1
          ? '1 Aufgabe schafft ihre Deadline nicht'
          : '${items.length} Aufgaben schaffen ihre Deadline nicht';
    }
    return items.length == 1
        ? '1 Aufgabe ist überfällig'
        : '${items.length} Aufgaben sind überfällig';
  }

  /// Bei genau einer überfälligen Aufgabe steht der Nachholtermin schon im Band — dafür muss
  /// niemand erst tippen. Bei mehreren wäre die Zeile eine Aufzählung, die nicht mehr passt.
  String? get _untertitel {
    if (!overdue || items.length != 1) return null;
    final wann = items.first.plannedStartText;
    return wann == null ? 'Kein Nachholtermin gefunden' : 'Nachholtermin $wann';
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final untertitel = _untertitel;

    return Material(
      color: _farbe.withValues(alpha: overdue ? 0.18 : 0.12),
      child: InkWell(
        onTap: () => _zeigeListe(context),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
          child: Row(
            children: [
              Icon(_symbol, size: 18, color: _farbe),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      _titel,
                      style: theme.textTheme.bodyMedium?.copyWith(
                        fontWeight: overdue ? FontWeight.w800 : FontWeight.w600,
                        color: overdue ? _rot : null,
                      ),
                    ),
                    if (untertitel != null)
                      Text(
                        untertitel,
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: theme.textTheme.bodySmall?.color?.withValues(alpha: 0.8),
                        ),
                      ),
                  ],
                ),
              ),
              Icon(Icons.chevron_right, size: 18, color: _farbe),
            ],
          ),
        ),
      ),
    );
  }

  void _zeigeListe(BuildContext context) {
    showModalBottomSheet<void>(
      context: context,
      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 20, 20, 12),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(overdue ? 'Überfällig' : 'Deadline in Gefahr',
                  style: Theme.of(ctx).textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.bold,
                        color: overdue ? _rot : null,
                      )),
              const SizedBox(height: 4),
              Text(
                overdue
                    ? 'Der Termin ist vorbei. Der Plan holt diese Aufgaben so früh wie möglich nach.'
                    : 'Vor der Deadline war auch mit gelockerten Arbeitszeiten kein Platz mehr.',
                style: Theme.of(ctx).textTheme.bodySmall?.copyWith(
                      color: Theme.of(ctx).textTheme.bodySmall?.color?.withValues(alpha: 0.7),
                    ),
              ),
              const SizedBox(height: 16),
              ...items.map(
                (e) => Padding(
                  padding: const EdgeInsets.only(bottom: 14),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Padding(
                        padding: const EdgeInsets.only(top: 2),
                        child: Icon(_symbol, size: 16, color: _farbe),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(e.title,
                                style: Theme.of(ctx).textTheme.bodyMedium?.copyWith(
                                      fontWeight: FontWeight.w600,
                                    )),
                            Text(
                              _zeile(e),
                              style: Theme.of(ctx).textTheme.bodySmall?.copyWith(
                                    color: Theme.of(ctx)
                                        .textTheme
                                        .bodySmall
                                        ?.color
                                        ?.withValues(alpha: 0.7),
                                  ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  /// Die Zeile unter dem Titel. Bei Überfälligem ist die interessante Angabe der Nachholtermin,
  /// nicht die fehlende Minutenzahl — die ist dort meist 0, weil alles eingeplant ist.
  static String _zeile(AtRiskItem e) {
    if (e.isOverdue) {
      final wann = e.plannedStartText;
      return wann == null
          ? '${e.reasonText} · kein Nachholtermin gefunden'
          : '${e.reasonText} · Nachholtermin $wann';
    }
    return '${e.reasonText} · ${e.minutes} min';
  }
}

// ─── Calendar Header ─────────────────────────────────────────────────────────

class _CalendarHeader extends StatelessWidget {
  final _CalView view;
  final DateTime selectedDay;
  final ValueChanged<_CalView> onViewChanged;
  final ValueChanged<int> onNavigate;
  final VoidCallback onToday;

  const _CalendarHeader({
    required this.view,
    required this.selectedDay,
    required this.onViewChanged,
    required this.onNavigate,
    required this.onToday,
  });

  int _getWeekNumber(DateTime date) {
    int dayOfYear = int.parse(DateFormat('D').format(date));
    return ((dayOfYear - date.weekday + 10) / 7).floor();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final surfaceColor = isDark ? const Color(0xFF0E0E0E) : Colors.white;
    final borderColor = const Color(0xFF484848).withValues(alpha: 0.15);

    String dateTitle;
    String dateSubtitle;
    if (view == _CalView.day) {
      dateTitle = DateFormat('MMMM d').format(selectedDay);
      dateSubtitle = DateFormat('EEEE').format(selectedDay).toUpperCase();
    } else if (view == _CalView.week) {
      dateTitle = DateFormat('MMMM yyyy').format(selectedDay);
      dateSubtitle = 'Week ${_getWeekNumber(selectedDay)}';
    } else {
      dateTitle = DateFormat('MMMM yyyy').format(selectedDay);
      dateSubtitle = 'Month View';
    }

    // Die Kopfzeile ist Chrome mit festen Abständen: bei System-Schriftgröße 2.0
    // wüchse sie über die halbe Displayhöhe und ließe für Raster bzw. Zeitleiste
    // nichts mehr übrig. Deshalb skaliert sie höchstens bis 1.3 mit.
    return MediaQuery.withClampedTextScaling(
      maxScaleFactor: 1.3,
      child: Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Top Bar (unverändert)
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
          decoration: BoxDecoration(
            color: surfaceColor,
            border: Border(bottom: BorderSide(color: borderColor)),
          ),
          child: Row(
            children: [
              Icon(Icons.calendar_today_outlined, color: theme.colorScheme.primary, size: 24),
              const SizedBox(width: 12),
              // Flexible: bei großer System-Schriftgröße passt der Titel sonst nicht
              // mehr neben das Icon.
              const Flexible(
                child: Text('Calendar',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800, color: Colors.white, fontFamily: 'Manrope')),
              ),
            ],
          ),
        ),
        
        // Der neue Sub-header Bereich
        Container(
          padding: const EdgeInsets.fromLTRB(24, 24, 24, 24),
          color: surfaceColor,
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
 

Expanded(
  child: Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      if (view == _CalView.day)
        Text(dateSubtitle, 
          style: TextStyle(fontSize: 14, fontWeight: FontWeight.w500, letterSpacing: 2.0, color: theme.colorScheme.onSurfaceVariant, fontFamily: 'Manrope'))
      else ...[
        // Jahreszahl über dem Monat (nur in Woche/Monat Ansicht)
        Text(DateFormat('yyyy').format(selectedDay), 
          style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, letterSpacing: 1.0, color: theme.colorScheme.primary.withValues(alpha: 0.8), fontFamily: 'Manrope')),
        const SizedBox(height: 2),
        Text(DateFormat('MMMM').format(selectedDay), // Nur der Monat, groß
          overflow: TextOverflow.ellipsis, 
          style: const TextStyle(fontSize: 32, fontWeight: FontWeight.w800, letterSpacing: -1.0, color: Colors.white, fontFamily: 'Manrope')),
      ],
      const SizedBox(height: 4),
      if (view == _CalView.day)
        Text(dateTitle, 
          style: const TextStyle(fontSize: 28, fontWeight: FontWeight.w800, letterSpacing: -2.0, color: Colors.white, fontFamily: 'Manrope', height: 1.0))
      else
        Text(dateSubtitle, 
          style: TextStyle(fontSize: 14, fontWeight: FontWeight.w500, color: theme.colorScheme.onSurfaceVariant, fontFamily: 'Manrope')),
    ],
  ),
),

              
              // Rechte Seite: Kompakter Steuerungsblock
              const SizedBox(width: 12),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      GestureDetector(
                        onTap: onToday,
                        child: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                          child: Text('TODAY', style: TextStyle(fontSize: 14, fontWeight: FontWeight.w900, color: theme.colorScheme.primary, fontFamily: 'Manrope')),
                        ),
                      ),
                      const SizedBox(width: 4),
                      _ViewDropDown(current: view, onChanged: onViewChanged),
                    ],
                  ),
                  // Pfeile direkt unter TODAY & Kalender-Icon
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      IconButton(
                        onPressed: () => onNavigate(-1),
                        icon: const Icon(Icons.chevron_left_rounded, size: 22),
                        padding: EdgeInsets.zero,
                        constraints: const BoxConstraints(),
                        visualDensity: VisualDensity.compact,
                        color: theme.colorScheme.primary,
                      ),
                      const SizedBox(width: 12),
                      IconButton(
                        onPressed: () => onNavigate(1),
                        icon: const Icon(Icons.chevron_right_rounded, size: 22),
                        padding: EdgeInsets.zero,
                        constraints: const BoxConstraints(),
                        visualDensity: VisualDensity.compact,
                        color: theme.colorScheme.primary,
                      ),
                    ],
                  ),
                ],
              ),
            ],
          ),
        ),
      ],
      ),
    );
  }
}

class _ViewDropDown extends StatelessWidget {
  final _CalView current;
  final ValueChanged<_CalView> onChanged;

  const _ViewDropDown({required this.current, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    
    return PopupMenuButton<_CalView>(
      initialValue: current,
      onSelected: onChanged,
      // Das kleine Kalender-Icon oben rechts
      icon: Icon(Icons.calendar_view_day_rounded, color: theme.colorScheme.primary, size: 22),
      padding: EdgeInsets.zero,
      constraints: const BoxConstraints(minWidth: 120), // Sorgt für ein schlichtes, schmales Menü
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero), // Passend zu deinem restlichen Design
      itemBuilder: (context) => _CalView.values.map((view) {
        final isSelected = view == current;
        return PopupMenuItem<_CalView>(
          value: view,
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                view.name[0].toUpperCase() + view.name.substring(1),
                style: const TextStyle(
                  fontFamily: 'Manrope',
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                ),
              ),
              if (isSelected)
                Icon(Icons.check, color: theme.colorScheme.primary, size: 18),
            ],
          ),
        );
      }).toList(),
    );
  }
}

// ─── Week Strip ───────────────────────────────────────────────────────────────

class _WeekStrip extends StatelessWidget {
  final DateTime selected;
  final ValueChanged<DateTime> onDayTap;
  const _WeekStrip({required this.selected, required this.onDayTap});

  DateTime _weekStart(DateTime d) {
    final wd = d.weekday;
    return DateTime(d.year, d.month, d.day - (wd - 1));
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final surfaceColor = theme.scaffoldBackgroundColor;
    final selectedWeekStart = _weekStart(selected);

    return Container(
      color: theme.colorScheme.outlineVariant.withValues(alpha: 0.1), // background for gap-px
      child: Row(
        children: [
          Container(
            width: kTimeGutterWidth,
            height: 60,
            color: surfaceColor,
          ),
          Expanded(
            child: Row(
              children: List.generate(7, (i) {
                final day = selectedWeekStart.add(Duration(days: i));
                
                final isToday = isSameDay(day, DateTime.now());
                return Expanded(
                  child: GestureDetector(
                    onTap: () => onDayTap(day),
                    child: Container(
                      margin: const EdgeInsets.only(left: 1), // gap-px
                      height: 60,
                      color: surfaceColor,
                      // FittedBox um die ganze Spalte: auf schmalen Displays ist eine
                      // Spalte keine 40px breit, und bei großer System-Schriftgröße
                      // passen zwei Zeilen nicht mehr in die 60px Höhe. Skaliert wird
                      // deshalb der komplette Block, nicht die Texte einzeln.
                      child: Center(
                        child: FittedBox(
                          fit: BoxFit.scaleDown,
                          child: Column(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Text(
                                DateFormat('E').format(day).substring(0, 3).toUpperCase(),
                                maxLines: 1,
                                softWrap: false,
                                style: TextStyle(
                                  fontSize: 15,
                                  fontWeight: FontWeight.w800,
                                  color: isToday
                                      ? const Color(0xFF918AFA)
                                      : theme.colorScheme.onSurfaceVariant,

                                  letterSpacing: 1.0,
                                  fontFamily: 'Manrope',
                                ),
                              ),
                              const SizedBox(height: 2),
                              Text(
                                '${day.day}',
                                maxLines: 1,
                                softWrap: false,
                                style: TextStyle(
                                  fontSize: 15,
                                  fontWeight: FontWeight.w800,
                                  fontFamily: 'Manrope',
                                  color: isToday
                                      ? const Color(0xFF918AFA)
                                      : (day.weekday >= 6 ? theme.colorScheme.error : Colors.white),
                                ),
                              ),

                            ],
                          ),
                        ),
                      ),
                    ),
                  ),
                );
              }),
            ),
          ),
        ],
      ),
    );
  }
}

bool isSameDay(DateTime a, DateTime b) =>
    a.year == b.year && a.month == b.month && a.day == b.day;

// ─── Day Timeline ─────────────────────────────────────────────────────────────

class _DayTimeline extends StatelessWidget {
  final CalendarProvider cal;
  final DateTime selected;
  final double initialOffset;
  final ValueChanged<double> onOffsetChanged;

  const _DayTimeline({
    required this.cal,
    required this.selected,
    required this.initialOffset,
    required this.onOffsetChanged,
  });

  @override
  Widget build(BuildContext context) {
    final events = cal.getEventsForDay(selected);
    return _TimelineGrid(
      events: events,
      columns: 1,
      columnDates: [selected],
      initialOffset: initialOffset,
      onOffsetChanged: onOffsetChanged,
    );
  }
}

// ─── Week Timeline ────────────────────────────────────────────────────────────

class _WeekTimeline extends StatelessWidget {
  final CalendarProvider cal;
  final DateTime selected;
  final double initialOffset;
  final ValueChanged<double> onOffsetChanged;

  const _WeekTimeline({
    required this.cal,
    required this.selected,
    required this.initialOffset,
    required this.onOffsetChanged,
  });

  @override
  Widget build(BuildContext context) {
    final wd = selected.weekday;
    final monday = DateTime(selected.year, selected.month, selected.day - (wd - 1));
    final days = List.generate(7, (i) => monday.add(Duration(days: i)));

    // Hier wird bewusst KEIN ScrollController mehr gebaut. Vorher entstand bei jedem
    // notifyListeners() (optimistisches Update nach dem Drop, 30s-Poll) ein neuer Controller,
    // der die Wochenansicht jedes Mal zurück auf "jetzt − 2h" gesprungen hat — und der alte
    // wurde nie disposed.
    return _TimelineGrid(
      events: cal.events,
      columns: 7,
      columnDates: days,
      initialOffset: initialOffset,
      onOffsetChanged: onOffsetChanged,
    );
  }
}

// ─── Shared Timeline Grid ─────────────────────────────────────────────────────

/// Ein Event mit seiner zugewiesenen Spur innerhalb einer Überlappungsgruppe.
class _Placed {
  final CalendarEvent event;
  final int lane;
  final int laneCount;
  const _Placed(this.event, this.lane, this.laneCount);
}

class _TimelineGrid extends StatefulWidget {
  final List<CalendarEvent> events;
  final int columns;
  final List<DateTime> columnDates;
  final double initialOffset;
  final ValueChanged<double> onOffsetChanged;

  const _TimelineGrid({
    required this.events,
    required this.columns,
    required this.columnDates,
    required this.initialOffset,
    required this.onOffsetChanged,
  });

  @override
  State<_TimelineGrid> createState() => _TimelineGridState();
}

class _TimelineGridState extends State<_TimelineGrid> {
  // The event currently being dragged (to show ghost).
  CalendarEvent? _draggingEvent;
  // The snapped DateTime the drag is hovering over.
  DateTime? _hoverTime;
  // GlobalKey lets us convert global coords → local column coords.
  final _columnKey = GlobalKey();
  // Der Viewport (sichtbarer Ausschnitt), nicht die 1536px hohe Leinwand — für den Randscroll.
  final _viewportKey = GlobalKey();

  // Zeigerposition des laufenden Drags. DragTargetDetails.offset ist die Ecke des Blocks,
  // nicht der Finger; für die Randzone braucht es den Finger (siehe _updateEdgeScroll).
  Offset? _dragPointer;

  // Der Grid besitzt seinen ScrollController selbst; nach oben wandert nur der Offset-Wert.
  late final ScrollController _scroll;

  // ── Randscroll während des Ziehens ─────────────────────────────────────────
  Timer? _edgeScrollTimer;
  double _edgeScrollVelocity = 0;
  Offset? _lastDragGlobal;

  static const double _kEdgeBand = 72.0;  // Trigger-Zone oben/unten
  static const double _kMaxStep = 14.0;   // px pro 16ms-Tick

  // ── Dringlichkeit ──────────────────────────────────────────────────────────
  // Die Faelligkeits-Einstufung der Aufgabenbloecke haengt an "jetzt": ohne Ticker bliebe ein
  // Block "MORGEN FÄLLIG", bis der Nutzer das naechste Mal scrollt. Ein Timer fuer das ganze
  // Grid statt einer pro Block — dieselbe Loesung wie in _CurrentTimeLine, nur eine Ebene hoeher,
  // weil _EventBlock zustandslos bleibt.
  Timer? _urgencyTimer;
  DateTime _now = DateTime.now();

  @override
  void initState() {
    super.initState();
    _scroll = ScrollController(initialScrollOffset: widget.initialOffset)
      ..addListener(() => widget.onOffsetChanged(_scroll.offset));
    _urgencyTimer = Timer.periodic(
        const Duration(minutes: 1), (_) => setState(() => _now = DateTime.now()));
  }

  @override
  void dispose() {
    _urgencyTimer?.cancel();
    _edgeScrollTimer?.cancel();
    _scroll.dispose();
    super.dispose();
  }

  // ── Coord helpers ──────────────────────────────────────────────────────────

  /// Convert a global [offset] into a snapped [DateTime] within the timeline column.
  DateTime? _globalToTime(Offset globalOffset) {
    final ro = _columnKey.currentContext?.findRenderObject() as RenderBox?;
    if (ro == null) return null;
    // _columnKey hängt am Stack INNERHALB der SingleChildScrollView, also auf der vollen
    // 1536px-Leinwand. Deren Paint-Transform enthält den Scroll-Offset bereits — globalToLocal
    // liefert daher schon Leinwand-Koordinaten. Ein zusätzliches Aufaddieren von _scroll.offset
    // zählte ihn doppelt: bei der üblichen Startposition (jetzt − 2h) landete damit fast jeder
    // Drop jenseits des Tagesendes und wurde vom clamp unten auf 23:45 gezogen.
    final local = ro.globalToLocal(globalOffset);
    // Clamp y to valid range.
    final clampedY = local.dy.clamp(0.0, kHourHeight * (kDayEnd - kDayStart) - 1);
    final totalMinutes = (clampedY / kHourHeight * 60).round() + kDayStart * 60;
    // Snap to 15-minute grid.
    final snapped = ((totalMinutes / 15).round() * 15).clamp(0, 23 * 60 + 45);
    // Determine which column date the x coord lands on.
    final colW = ro.size.width / widget.columns;
    final colIdx = (local.dx / colW).floor().clamp(0, widget.columns - 1);
    final colDate = widget.columnDates[colIdx];
    return DateTime(colDate.year, colDate.month, colDate.day, snapped ~/ 60, snapped % 60);
  }

  /// Y-Position (in px auf der Leinwand) für eine Uhrzeit.
  static double _topFor(int hour, int minute) =>
      (((hour * 60 + minute) - kDayStart * 60) / 60) * kHourHeight;

  // ── Randscroll ─────────────────────────────────────────────────────────────

  /// Scrollt die Leinwand, wenn der Finger nahe an den oberen/unteren Rand kommt.
  /// Ohne das lässt sich ein Event auf einem Handy nur wenige Stunden weit ziehen —
  /// der Viewport zeigt ~500px von 1536px.
  ///
  /// [globalTopLeft] ist die künftige obere Kante des Blocks (daraus wird die Uhrzeit
  /// berechnet), [pointer] die tatsächliche Zeigerposition. Die Randzone muss am Zeiger
  /// hängen: bei einem drei Stunden langen Block liegt dessen Oberkante fast 200px über
  /// dem Finger, und der Randscroll sprang dann an, obwohl der Finger mitten im Bild war —
  /// die Leinwand wanderte unter dem Block weg und der Drop landete zu früh.
  void _updateEdgeScroll(Offset globalTopLeft, Offset? pointer) {
    _lastDragGlobal = globalTopLeft;
    final box = _viewportKey.currentContext?.findRenderObject() as RenderBox?;
    if (box == null) return;

    final dy = box.globalToLocal(pointer ?? globalTopLeft).dy;
    final h = box.size.height;

    double v = 0;
    if (dy < _kEdgeBand) {
      v = -_kMaxStep * (1 - (dy / _kEdgeBand)).clamp(0.0, 1.0);
    } else if (dy > h - _kEdgeBand) {
      v = _kMaxStep * (1 - ((h - dy) / _kEdgeBand)).clamp(0.0, 1.0);
    }
    _edgeScrollVelocity = v;

    if (v == 0) {
      _edgeScrollTimer?.cancel();
      _edgeScrollTimer = null;
      return;
    }
    _edgeScrollTimer ??= Timer.periodic(const Duration(milliseconds: 16), (_) {
      if (!_scroll.hasClients) return;
      final next = (_scroll.offset + _edgeScrollVelocity)
          .clamp(0.0, _scroll.position.maxScrollExtent);
      if (next == _scroll.offset) return;
      _scroll.jumpTo(next);
      // Der Finger steht still, aber die Leinwand bewegt sich — ohne Neuberechnung
      // würde der Geisterblock einfrieren.
      if (_lastDragGlobal != null) {
        final t = _globalToTime(_lastDragGlobal!);
        if (t != _hoverTime) setState(() => _hoverTime = t);
      }
    });
  }

  void _stopEdgeScroll() {
    _edgeScrollTimer?.cancel();
    _edgeScrollTimer = null;
    _edgeScrollVelocity = 0;
    _lastDragGlobal = null;
  }

  // ── Spurenaufteilung überlappender Events ──────────────────────────────────

  /// Weist überlappenden Events nebeneinanderliegende Spuren zu (Greedy über das
  /// Intervallgraph-Clustering). Vorher lagen alle Events auf voller Spaltenbreite
  /// übereinander, wodurch nur das zuletzt gezeichnete antippbar oder ziehbar war.
  List<_Placed> _assignLanes(List<CalendarEvent> dayEvents) {
    final evs = [...dayEvents]..sort((a, b) {
      final c = a.startTime.compareTo(b.startTime);
      return c != 0 ? c : b.durationInMinutes.compareTo(a.durationInMinutes);
    });

    final out = <_Placed>[];
    var clusterStart = 0;
    var laneEnds = <DateTime>[];
    DateTime? clusterEnd;

    void flush() {
      for (var i = clusterStart; i < out.length; i++) {
        out[i] = _Placed(out[i].event, out[i].lane, laneEnds.length);
      }
      clusterStart = out.length;
      laneEnds = <DateTime>[];
      clusterEnd = null;
    }

    for (final e in evs) {
      // Neues Cluster, sobald ein Event nach dem bisherigen Cluster-Ende beginnt.
      if (clusterEnd != null && !e.startTime.isBefore(clusterEnd!)) flush();

      var lane = laneEnds.indexWhere((end) => !e.startTime.isBefore(end));
      if (lane == -1) {
        laneEnds.add(e.endTime);
        lane = laneEnds.length - 1;
      } else {
        laneEnds[lane] = e.endTime;
      }

      out.add(_Placed(e, lane, 0)); // laneCount wird im flush() nachgetragen
      clusterEnd = (clusterEnd == null || e.endTime.isAfter(clusterEnd!)) ? e.endTime : clusterEnd;
    }
    flush();
    return out;
  }

  // ── Build ──────────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final gridLineColor = Theme.of(context).colorScheme.outlineVariant.withValues(alpha: 0.3);
    final timeTextColor = isDark ? Colors.grey.shade600 : Colors.grey.shade400;
    final totalHeight = kHourHeight * (kDayEnd - kDayStart);

    return SingleChildScrollView(
      key: _viewportKey,
      controller: _scroll,
      child: SizedBox(
        height: totalHeight,
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Time gutter
            SizedBox(
              width: kTimeGutterWidth,
              height: totalHeight,
              child: Stack(
                children: [
                  for (int h = kDayStart; h < kDayEnd; h++)
                    Positioned(
                      top: (h - kDayStart) * kHourHeight - 7,
                      left: 0,
                      right: 0,
                      child: Padding(
                        padding: const EdgeInsets.only(right: 8),
                        child: Text(
                          h == 0 ? '' : DateFormat('h a').format(DateTime(2000, 1, 1, h)),
                          textAlign: TextAlign.right,
                          style: TextStyle(fontSize: 10, color: timeTextColor, fontWeight: FontWeight.w500),
                        ),
                      ),
                    ),
                ],
              ),
            ),
            // Column(s) — wrapped in DragTarget
            Expanded(
              child: DragTarget<CalendarEvent>(
                onWillAcceptWithDetails: (_) => true,
                onMove: (details) {
                  final t = _globalToTime(details.offset);
                  if (t != _hoverTime) setState(() => _hoverTime = t);
                  _updateEdgeScroll(details.offset, _dragPointer);
                },
                onLeave: (_) {
                  _stopEdgeScroll();
                  setState(() => _hoverTime = null);
                },
                onAcceptWithDetails: (details) async {
                  _stopEdgeScroll();
                  final event = details.data;
                  final newStart = _globalToTime(details.offset);
                  setState(() {
                    _draggingEvent = null;
                    _hoverTime = null;
                  });
                  if (newStart == null) return;
                  final duration = event.endTime.difference(event.startTime);
                  final newEnd = newStart.add(duration);
                  final updated = event.copyWith(startTime: newStart, endTime: newEnd);
                  await context.read<CalendarProvider>().updateEvent(updated);
                },
                builder: (ctx, candidateData, rejectedData) {
                  final isOver = candidateData.isNotEmpty;
                  return Stack(
                    key: _columnKey,
                    children: [
                      // Hour grid lines
                      for (int h = kDayStart; h <= kDayEnd; h++)
                        Positioned(
                          top: (h - kDayStart) * kHourHeight,
                          left: 0,
                          right: 0,
                          child: Divider(
                            height: 1,
                            thickness: 1,
                            color: h % 2 == 0 ? Theme.of(context).colorScheme.outlineVariant.withValues(alpha: 0.3) : Theme.of(context).colorScheme.outlineVariant.withValues(alpha: 0.1),
                          ),
                        ),
                      // Column separators (week view)
                      if (widget.columns > 1)
                        LayoutBuilder(builder: (ctx2, constraints) {
                          final colW = constraints.maxWidth / widget.columns;
                          return Stack(
                            children: [
                              for (int c = 1; c < widget.columns; c++)
                                Positioned(
                                  left: c * colW,
                                  top: 0,
                                  width: 1,
                                  height: totalHeight,
                                  child: Container(color: gridLineColor),
                                ),
                            ],
                          );
                        }),
                      // Today highlight (week view)
                      if (widget.columns > 1)
                        LayoutBuilder(builder: (ctx2, constraints) {
                          final now = DateTime.now();
                          for (int c = 0; c < widget.columnDates.length; c++) {
                            if (isSameDay(widget.columnDates[c], now)) {
                              final colW = constraints.maxWidth / widget.columns;
                              return Stack(
                                children: [
                                  Positioned(
                                    left: c * colW,
                                    top: 0,
                                    width: colW,
                                    height: totalHeight,
                                    child: Container(color: AppTheme.primaryColor.withValues(alpha: 0.03)),
                                  ),
                                ],
                              );
                            }
                          }
                          return const SizedBox.shrink();
                        }),
                      // Events
                      LayoutBuilder(builder: (ctx2, constraints) {
                        final colW = constraints.maxWidth / widget.columns;

                        // Events je Spalte gruppieren und pro Spalte Spuren vergeben.
                        final byColumn = <int, List<CalendarEvent>>{};
                        for (final event in widget.events) {
                          final colIdx = widget.columns > 1
                              ? widget.columnDates.indexWhere((d) => isSameDay(d, event.startTime))
                              : 0;
                          if (colIdx < 0) continue;
                          byColumn.putIfAbsent(colIdx, () => []).add(event);
                        }

                        return Stack(
                          children: [
                            for (final entry in byColumn.entries)
                              for (final placed in _assignLanes(entry.value)) ...[
                                Builder(builder: (bctx) {
                                  final event = placed.event;
                                  final startMinutes =
                                      event.startTime.hour * 60 + event.startTime.minute;
                                  final durationMin = event.durationInMinutes.clamp(15, 1440);
                                  final top = _topFor(event.startTime.hour, event.startTime.minute);
                                  final height = (durationMin / 60) * kHourHeight;
                                  final isDragging = _draggingEvent?.id == event.id;

                                  // Ab 3 Spuren wird eine echte Aufteilung in der Wochenansicht
                                  // zu schmal zum Antippen — dann versetzte Überlappung wie im
                                  // Google Kalender. Die Sortierung nach Startzeit sorgt dafür,
                                  // dass später beginnende Events oben liegen und treffbar sind.
                                  final laneW = (colW - 4) / placed.laneCount;
                                  final narrow = laneW < 28;
                                  final left = entry.key * colW +
                                      2 +
                                      (narrow ? placed.lane * 10.0 : placed.lane * laneW);
                                  final width = narrow
                                      ? (colW - 4 - (placed.laneCount - 1) * 10.0)
                                      : laneW - 1;

                                  if (startMinutes < kDayStart * 60) return const SizedBox.shrink();

                                  return Positioned(
                                    left: left,
                                    top: top,
                                    width: width.clamp(16.0, double.infinity),
                                    height: height.clamp(24.0, double.infinity),
                                    child: AnimatedOpacity(
                                      duration: const Duration(milliseconds: 150),
                                      opacity: isDragging ? 0.35 : 1.0,
                                      child: _EventBlock(
                                        event: event,
                                        now: _now,
                                        onDragStarted: () => setState(() => _draggingEvent = event),
                                        onDragUpdate: (d) => _dragPointer = d.globalPosition,
                                        // Auch der Randscroll muss hier enden: wird der Drag
                                        // abgebrochen, während der Zeiger noch über dem Grid
                                        // steht, feuert kein onLeave — der Timer würde die
                                        // Leinwand danach endlos weiterscrollen.
                                        onDragEnd: () {
                                          _stopEdgeScroll();
                                          _dragPointer = null;
                                          setState(() {
                                            _draggingEvent = null;
                                            _hoverTime = null;
                                          });
                                        },
                                      ),
                                    ),
                                  );
                                }),
                              ],
                            // Drag drop-zone indicator
                            if (isOver && _hoverTime != null)
                              _DragIndicator(
                                hoverTime: _hoverTime!,
                                draggingEvent: candidateData.first,
                                colW: colW,
                                columns: widget.columns,
                                columnDates: widget.columnDates,
                              ),
                            // Current time line
                            _CurrentTimeLine(colW: constraints.maxWidth),
                          ],
                        );
                      }),
                    ],
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ─── Current Time Line ────────────────────────────────────────────────────────

class _CurrentTimeLine extends StatefulWidget {
  final double colW;
  const _CurrentTimeLine({required this.colW});

  @override
  State<_CurrentTimeLine> createState() => _CurrentTimeLineState();
}

class _CurrentTimeLineState extends State<_CurrentTimeLine> {
  late Timer _timer;
  DateTime _now = DateTime.now();

  @override
  void initState() {
    super.initState();
    _timer = Timer.periodic(const Duration(minutes: 1), (_) => setState(() => _now = DateTime.now()));
  }

  @override
  void dispose() {
    _timer.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final minutes = _now.hour * 60 + _now.minute;
    // kDayStart abziehen: heute 0, aber sobald jemand die Nachtstunden ausblendet,
    // säße die Linie sonst um kDayStart Stunden zu tief.
    final top = ((minutes - kDayStart * 60) / 60) * kHourHeight;
    return Positioned(
      top: top - 6,
      left: 0,
      right: 0,
      child: Row(
        children: [
          Container(
            width: 8,
            height: 8,
            margin: const EdgeInsets.only(left: 2, right: 4),
            decoration: const BoxDecoration(color: Colors.white, shape: BoxShape.circle, boxShadow: [BoxShadow(color: Colors.white, blurRadius: 4)]),
          ),
          Expanded(child: Container(height: 1, color: Colors.white.withValues(alpha: 0.5))),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
            color: Colors.white,
            child: const Text('NOW', style: TextStyle(color: Colors.black, fontSize: 8, fontWeight: FontWeight.w900, letterSpacing: -0.5)),
          ),
        ],
      ),
    );
  }
}

// ─── Drag Indicator ──────────────────────────────────────────────────────────

class _DragIndicator extends StatelessWidget {
  final DateTime hoverTime;
  final CalendarEvent? draggingEvent;
  final double colW;
  final int columns;
  final List<DateTime> columnDates;

  const _DragIndicator({
    required this.hoverTime,
    required this.draggingEvent,
    required this.colW,
    required this.columns,
    required this.columnDates,
  });

  @override
  Widget build(BuildContext context) {
    final colIdx = columnDates.indexWhere((d) => isSameDay(d, hoverTime));
    final left = colIdx < 0 ? 0.0 : colIdx * colW;
    final w = colIdx < 0 ? colW : colW;
    final top =
        (((hoverTime.hour * 60 + hoverTime.minute) - kDayStart * 60) / 60) * kHourHeight;
    final durMin = draggingEvent?.durationInMinutes ?? 60;
    final height = (durMin / 60) * kHourHeight;
    final color = draggingEvent != null
        ? (draggingEvent!.color != null ? draggingEvent!.colorObject : _typeColor(draggingEvent!.eventType))
        : AppTheme.primaryColor;

    return Stack(
      children: [
        // Ghost block
        Positioned(
          left: left + 2,
          top: top,
          width: w - 4,
          height: height.clamp(24.0, double.infinity),
          child: Container(
            decoration: BoxDecoration(
              color: color.withValues(alpha: 0.25),
              borderRadius: BorderRadius.zero,
              border: Border.all(color: color, width: 1.5),
            ),
          ),
        ),
        // Time pill label
        Positioned(
          left: left + 6,
          top: top - 20,
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
            decoration: BoxDecoration(
              color: color,
              borderRadius: BorderRadius.zero,
              boxShadow: [BoxShadow(color: color.withValues(alpha: 0.4), blurRadius: 8)],
            ),
            child: Text(
              DateFormat('h:mm a').format(hoverTime),
              style: const TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.w700),
            ),
          ),
        ),
      ],
    );
  }
}

// ─── Event Block ──────────────────────────────────────────────────────────────

/// [Flexible] nur bei begrenzter Höhe — mit unbegrenzten Constraints (etwa im
/// Drag-Feedback) wirft ein Flex-Kind sonst eine Assertion.
Widget _maybeFlexible(bool bounded, Widget child) =>
    bounded ? Flexible(child: child) : child;

class _EventBlock extends StatelessWidget {
  final CalendarEvent event;
  /// Von aussen durchgereicht, damit alle Bloecke dieselbe Uhr benutzen und ein einziger Timer
  /// im Grid die Faelligkeits-Einstufung aktuell haelt (siehe _TimelineGridState._urgencyTimer).
  final DateTime now;
  final VoidCallback? onDragStarted;
  final VoidCallback? onDragEnd;
  final ValueChanged<DragUpdateDetails>? onDragUpdate;

  const _EventBlock({
    required this.event,
    required this.now,
    this.onDragStarted,
    this.onDragEnd,
    this.onDragUpdate,
  });

  Widget _buildCard(BuildContext context, {double opacity = 1.0}) {
    final color = event.color != null ? event.colorObject : _typeColor(event.eventType);
    final isDark = Theme.of(context).brightness == Brightness.dark;

    final accent = _urgentAccent(event, now);
    final urgent = accent != null;

    // Auf der hervorgehobenen Karte traegt der Akzent die ganze Flaeche statt nur einer Ahnung
    // davon — genau das laesst sie aus einem vollen Tag herausspringen.
    final bg = accent ??
        (isDark ? color.withValues(alpha: 0.20) : color.withValues(alpha: 0.15));
    // Alles, was sonst in der Typfarbe gezeichnet wird. Auf dem vollflaechigen Akzent Schwarz
    // oder Weiss, je nachdem was darauf lesbar ist.
    final fg = urgent
        ? (accent.computeLuminance() > 0.5 ? Colors.black : Colors.white)
        : color;

    // Layout richtet sich nach dem tatsächlich verfügbaren Platz statt nach der
    // Dauer allein: liegen zwei Termine parallel, teilen sie sich die Spalte und
    // die Karte wird schmal — Typ-Label und Titel müssen dann einzeilig bleiben,
    // sonst laufen sie über den Kartenrand hinaus.
    return Opacity(
      opacity: opacity,
      child: LayoutBuilder(
        builder: (context, c) {
          const padH = 6.0 + 4.0 + 4.0; // linker Rand + horizontales Padding
          const padV = 8.0;
          final availW = c.maxWidth.isFinite ? c.maxWidth - padH : double.infinity;
          final bounded = c.maxHeight.isFinite;
          final availH = bounded ? c.maxHeight - padV : double.infinity;

          // Die Zeilenhöhen müssen mit der System-Schriftgröße mitwachsen, sonst
          // rechnet der Platzbedarf unten zu klein und der Text läuft bei
          // "große Schrift" aus der Karte heraus.
          final scaler = MediaQuery.textScalerOf(context);
          final narrow = availW < 70;
          final titleSize = narrow ? 10.0 : 12.0;
          const titleHeightFactor = 1.15;
          final titleLineH = scaler.scale(titleSize) * titleHeightFactor;
          // 8px Typ-Label (Zeilenhöhe 1.2) bzw. das 10px Schloss-Icon, + 1px Abstand
          final typeRowH = math.max(scaler.scale(8) * 1.2, 10.0) + 1;

          final showLock = event.isLocked && availW >= 34;
          final showType = !narrow && availH >= typeRowH + titleLineH;
          final titleBudget = availH - (showType ? typeRowH : 0);
          final titleLines =
              titleBudget.isFinite ? (titleBudget / titleLineH).floor().clamp(1, 2) : 2;

          // Erledigt: zurueckgenommen, aber sichtbar — der Block bleibt als Protokoll
          // stehen und soll den offenen Rest des Tages nicht ueberstrahlen.
          return Opacity(
            opacity: event.isCompleted ? 0.55 : 1.0,
            child: Container(
            decoration: BoxDecoration(
              color: bg,
              borderRadius: BorderRadius.zero,
              // Auf der vollflaechigen Karte muss der Balken gegen den Akzent anlaufen, nicht
              // mit ihm verschmelzen — deshalb die Schriftfarbe und ein Stueck breiter.
              border: Border(
                  left: BorderSide(color: fg, width: urgent ? 6 : 4)),
            ),
            padding: const EdgeInsets.fromLTRB(6, 4, 4, 4),
            // ClipRect fängt die letzten Pixel ab, wenn die Zeilenhöhe der
            // konkreten Schrift minimal über der Schätzung oben liegt.
            child: ClipRect(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  // Feste Höhe: so kann die Zeile die Spalte nicht sprengen, egal
                  // wie das Typ-Label bei der eingestellten Schriftgröße ausfällt.
                  if (showType) ...[
                    SizedBox(
                      height: typeRowH - 1,
                      child: Row(
                        children: [
                          Expanded(
                            child: Text(
                              // Bei Dringlichkeit zaehlt die Faelligkeit, nicht der Typ — dass
                              // es eine Aufgabe ist, sagt schon der Titel.
                              urgent
                                  ? deadlineLabel(event.relatedTaskDeadline!, now)
                                  : event.eventType.toUpperCase(),
                              maxLines: 1,
                              softWrap: false,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(
                                  fontSize: 8,
                                  height: 1.2,
                                  fontWeight: FontWeight.w800,
                                  letterSpacing: 1.2,
                                  color: fg.withValues(alpha: urgent ? 0.95 : 0.8)),
                            ),
                          ),
                          // Das Ausrufezeichen steht vor Haken und Schloss: es ist der Grund,
                          // warum die Karte ueberhaupt heraussticht.
                          if (urgent)
                            Icon(Icons.priority_high_rounded, size: 10, color: fg)
                          else if (event.isCompleted)
                            Icon(Icons.check_circle_rounded, size: 10,
                                color: fg.withValues(alpha: 0.7))
                          else if (showLock)
                            Icon(Icons.lock_rounded, size: 10, color: fg.withValues(alpha: 0.7)),
                        ],
                      ),
                    ),
                    const SizedBox(height: 1),
                  ],
                  // Der Titel bekommt den Rest als Obergrenze (Flexible), damit die
                  // Spalte auch dann nicht überläuft, wenn die Schätzung oben um
                  // ein paar Pixel danebenliegt.
                  _maybeFlexible(
                    bounded,
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            event.title,
                            maxLines: titleLines,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                                fontSize: titleSize,
                                fontWeight: FontWeight.w800,
                                color: fg,
                                decoration: event.isCompleted
                                    ? TextDecoration.lineThrough
                                    : null,
                                height: titleHeightFactor),
                          ),
                        ),
                        // Zu schmal oder zu flach fuer die Typ-Zeile: dann muss das Zeichen
                        // hierher, sonst faellt die Dringlichkeit genau bei den kleinen Bloecken
                        // weg — und die sind es, die man leicht uebersieht.
                        if (urgent && !showType)
                          Icon(Icons.priority_high_rounded, size: 10, color: fg)
                        else if (showLock && !showType)
                          Icon(Icons.lock_rounded, size: 10, color: fg.withValues(alpha: 0.7)),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ));
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final color = event.color != null ? event.colorObject : _typeColor(event.eventType);
    final card = GestureDetector(
      onTap: () => showModalBottomSheet(
        context: context,
        backgroundColor: Colors.transparent,
        isScrollControlled: true,
        builder: (_) => _EventDetailSheet(event: event),
      ),
      child: _buildCard(context),
    );

    // Fixed (pinned) events cannot be dragged — matches the backend, which
    // never lets the scheduler move isFixed events either. Vorlesungen ebenso: sie stammen
    // aus dem Stundenplan, und das Backend weist ein Verschieben mit 400 ab.
    if (event.isLocked) return card;

    // PointerAwareDraggable statt LongPressDraggable: mit der Maus wäre der Drag sonst
    // gar nicht auslösbar — siehe die Erklärung in pointer_aware_draggable.dart.
    return PointerAwareDraggable<CalendarEvent>(
      data: event,
      touchDelay: const Duration(milliseconds: 350),
      onDragStarted: onDragStarted,
      onDragUpdate: onDragUpdate,
      onDraggableCanceled: (v, _) => onDragEnd?.call(),
      onDragCompleted: onDragEnd,
      hapticFeedbackOnStart: true,
      // The widget shown under the finger while dragging
      feedback: Material(
        color: Colors.transparent,
        child: SizedBox(
          width: 180,
          child: Container(
            decoration: BoxDecoration(
              color: color.withValues(alpha: 0.9),
              borderRadius: BorderRadius.zero,
              boxShadow: [BoxShadow(color: color.withValues(alpha: 0.45), blurRadius: 16, offset: const Offset(0, 6))],
            ),
            padding: const EdgeInsets.fromLTRB(10, 8, 8, 8),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(event.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: Colors.white)),
                Text(DateFormat('h:mm a').format(event.startTime),
                    style: const TextStyle(fontSize: 11, color: Colors.white70)),
              ],
            ),
          ),
        ),
      ),
      // What remains in place (ghosted)
      childWhenDragging: _buildCard(context, opacity: 0.3),
      child: card,
    );
  }
}


// ─── Month View ───────────────────────────────────────────────────────────────

class _MonthView extends StatelessWidget {
  final CalendarProvider cal;
  final DateTime selected;
  final ValueChanged<DateTime> onDayTap;

  const _MonthView({required this.cal, required this.selected, required this.onDayTap});

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final firstDay = DateTime(selected.year, selected.month, 1);
    final daysInMonth = DateTime(selected.year, selected.month + 1, 0).day;
    final startOffset = (firstDay.weekday - 1) % 7;
    final totalCells = startOffset + daysInMonth;
    final rows = (totalCells / 7).ceil();
    const dayHeaders = ['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su'];

    final divider =
        Divider(height: 1, color: isDark ? const Color(0xFF2A2A38) : const Color(0xFFE8EAF0));

    // Das Raster scrollt bewusst nicht: es bekommt die verbleibende Höhe und
    // teilt sie durch die Zeilenzahl auf, damit der komplette Monat auf jedem
    // Display in einen Screen passt.
    return LayoutBuilder(
      builder: (context, constraints) {
        final h = constraints.maxHeight;
        // Feste Höhe für die Wochentagsleiste, damit die Aufteilung unten nicht
        // von der System-Schriftgröße abhängt (die Labels skalieren via FittedBox).
        const labelsH = 24.0;
        final chrome = labelsH + 1;
        // Das Raster braucht mindestens so viel, dass jede Zeile noch sichtbar ist.
        final gridMin = rows * 18.0;

        // Die Vorschau darf dem Raster höchstens ein Drittel wegnehmen — und nur
        // das, was nach dem Mindestraster übrig bleibt. Passt danach zu wenig für
        // eine sinnvolle Liste, entfällt sie ganz.
        var previewH = cal.selectedDayEvents.isEmpty ? 0.0 : (h * 0.32).clamp(0.0, 180.0);
        if (previewH > 0) {
          final maxPreview = h - chrome - 1 - gridMin;
          previewH = math.min(previewH, maxPreview);
          if (previewH < 60) previewH = 0;
        }
        final hasPreview = previewH > 0;

        return Column(
          children: [
            // Day labels
            SizedBox(
              height: labelsH,
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                child: Row(
                  children: dayHeaders.map((d) => Expanded(
                    child: Center(
                      child: FittedBox(
                        fit: BoxFit.scaleDown,
                        child: Text(d,
                            maxLines: 1,
                            style: const TextStyle(
                                fontSize: 11, fontWeight: FontWeight.w700, color: Colors.grey, letterSpacing: 0.5)),
                      ),
                    ),
                  )).toList(),
                ),
              ),
            ),
            divider,
            Expanded(
              child: Padding(
                padding: const EdgeInsets.all(6),
                child: LayoutBuilder(
                  builder: (context, grid) {
                    const spacing = 3.0;
                    final cellH =
                        ((grid.maxHeight - (rows - 1) * spacing) / rows).clamp(0.0, double.infinity);
                    final cellW = (grid.maxWidth - 6 * spacing) / 7;

                    return Column(
                      children: [
                        for (var r = 0; r < rows; r++) ...[
                          if (r > 0) const SizedBox(height: spacing),
                          SizedBox(
                            height: cellH,
                            child: Row(
                              children: [
                                for (var c = 0; c < 7; c++) ...[
                                  if (c > 0) const SizedBox(width: spacing),
                                  Expanded(
                                    child: _buildCell(
                                      r * 7 + c,
                                      startOffset,
                                      daysInMonth,
                                      cellH,
                                      cellW,
                                    ),
                                  ),
                                ],
                              ],
                            ),
                          ),
                        ],
                      ],
                    );
                  },
                ),
              ),
            ),
            // Selected day events preview
            if (hasPreview) ...[
              divider,
              SizedBox(
                height: previewH,
                child: ListView.builder(
                  padding: const EdgeInsets.all(12),
                  itemCount: cal.selectedDayEvents.length,
                  itemBuilder: (_, i) => _EventListTile(event: cal.selectedDayEvents[i]),
                ),
              ),
            ],
          ],
        );
      },
    );
  }

  Widget _buildCell(int idx, int startOffset, int daysInMonth, double cellH, double cellW) {
    final dayNum = idx - startOffset + 1;
    if (dayNum < 1 || dayNum > daysInMonth) return const SizedBox.shrink();
    final day = DateTime(selected.year, selected.month, dayNum);
    final isSel = isSameDay(day, selected);
    final isToday = isSameDay(day, DateTime.now());
    final evts = cal.getEventsForDay(day);

    // Schrift- und Punktgrößen richten sich nach der tatsächlichen Zellgröße —
    // auf einem kleinen Handy mit 6 Wochenzeilen bleibt sonst nichts übrig.
    final numSize = cellH < 22 ? 9.0 : (cellH < 30 ? 10.0 : (cellH < 42 ? 11.5 : 13.0));
    final dotSize = cellH < 34 ? 4.0 : 5.0;
    final showDots = evts.isNotEmpty && cellH >= numSize * 1.4 + dotSize + 6;
    final maxDots = ((cellW - 6 + 2) / (dotSize + 2)).floor().clamp(1, 4);

    // Dringliche Aufgaben zuerst: in eine Zelle passen nur vier Punkte, und ein "heute faellig"
    // darf nicht ausgerechnet der sein, der wegen der Uhrzeit hinten runterfaellt.
    final now = DateTime.now();
    final dotted = [...evts]..sort((a, b) =>
        (_isUrgent(b, now) ? 1 : 0).compareTo(_isUrgent(a, now) ? 1 : 0));

    return GestureDetector(
      onTap: () => onDayTap(day),
      child: Container(
        decoration: BoxDecoration(
          color: isSel
              ? AppTheme.primaryColor
              : isToday
                  ? AppTheme.primaryColor.withValues(alpha: 0.1)
                  : Colors.transparent,
          borderRadius: BorderRadius.zero,
        ),
        padding: const EdgeInsets.symmetric(horizontal: 2, vertical: 3),
        // FittedBox: bei großer System-Schriftgröße wächst die Tageszahl über die
        // berechnete Zellhöhe hinaus — dann wird der Inhalt herunterskaliert
        // statt überzulaufen.
        child: FittedBox(
          fit: BoxFit.scaleDown,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                '$dayNum',
                maxLines: 1,
                style: TextStyle(
                  fontSize: numSize,
                  height: 1.2,
                  fontWeight: FontWeight.w700,
                  color: isSel ? Colors.white : isToday ? const Color(0xFF918AFA) : null,
                ),
              ),
              if (showDots) ...[
                const SizedBox(height: 2),
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: dotted
                      .take(maxDots)
                      .map((e) => Container(
                            width: dotSize,
                            height: dotSize,
                            margin: const EdgeInsets.symmetric(horizontal: 1),
                            decoration: BoxDecoration(
                              color: isSel
                                  ? Colors.white.withValues(alpha: 0.8)
                                  : _urgentAccent(e, now) ??
                                      (e.color != null ? e.colorObject : _typeColor(e.eventType)),
                              shape: BoxShape.rectangle,
                            ),
                          ))
                      .toList(),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

// ─── Event List Tile (Month preview) ─────────────────────────────────────────

class _EventListTile extends StatelessWidget {
  final CalendarEvent event;
  const _EventListTile({required this.event});

  @override
  Widget build(BuildContext context) {
    final color = event.color != null ? event.colorObject : _typeColor(event.eventType);
    return GestureDetector(
      onTap: () => showModalBottomSheet(
        context: context,
        backgroundColor: Colors.transparent,
        isScrollControlled: true,
        builder: (_) => _EventDetailSheet(event: event),
      ),
      child: Container(
        margin: const EdgeInsets.only(bottom: 8),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        decoration: BoxDecoration(
          color: color.withValues(alpha: 0.1),
          borderRadius: BorderRadius.zero,
          border: Border(left: BorderSide(color: color, width: 3)),
        ),
        child: Row(
          children: [
            Icon(_typeIcon(event.eventType), size: 14, color: color),
            const SizedBox(width: 8),
            Expanded(
              child: Text(event.title,
                  style: TextStyle(fontWeight: FontWeight.w600, fontSize: 13, color: color)),
            ),
            Text(
              DateFormat('h:mm a').format(event.startTime),
              style: TextStyle(fontSize: 11, color: color.withValues(alpha: 0.75)),
            ),
          ],
        ),
      ),
    );
  }
}

// ─── Event Detail Sheet ───────────────────────────────────────────────────────

class _EventDetailSheet extends StatelessWidget {
  final CalendarEvent event;
  const _EventDetailSheet({required this.event});

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final color = event.color != null ? event.colorObject : _typeColor(event.eventType);
    final bg = isDark ? const Color(0xFF1A1A24) : Colors.white;

    return Container(
      margin: const EdgeInsets.all(12),
      decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.zero),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // Drag handle
          Center(
            child: Container(
              margin: const EdgeInsets.only(top: 12, bottom: 8),
              width: 40,
              height: 4,
              decoration: BoxDecoration(color: Colors.grey.withValues(alpha: 0.3), borderRadius: BorderRadius.circular(2)),
            ),
          ),
          // Header stripe
          Container(
            margin: const EdgeInsets.fromLTRB(16, 0, 16, 0),
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            decoration: BoxDecoration(
              color: color.withValues(alpha: 0.1),
              borderRadius: BorderRadius.zero,
              border: Border(left: BorderSide(color: color, width: 4)),
            ),
            child: Row(
              children: [
                Icon(_typeIcon(event.eventType), color: color, size: 20),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(event.title,
                      style: TextStyle(fontSize: 17, fontWeight: FontWeight.w700, color: color)),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: color.withValues(alpha: 0.15),
                    borderRadius: BorderRadius.zero,
                  ),
                  child: Text(event.eventType,
                      style: TextStyle(fontSize: 10, fontWeight: FontWeight.w700, color: color, letterSpacing: 0.5)),
                ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              children: [
                _SheetRow(
                  icon: Icons.access_time_rounded,
                  text: '${DateFormat('EEE, MMM d · h:mm a').format(event.startTime)} → ${DateFormat('h:mm a').format(event.endTime)}',
                  sub: '${event.durationInMinutes} minutes',
                ),
                // Faelligkeit der zugehoerigen Aufgabe. Die Karte im Raster zeigt nur "HEUTE
                // FÄLLIG"; wer den Block antippt, will das genaue Datum sehen.
                if (event.isTask && event.relatedTaskDeadline != null)
                  _SheetRow(
                    icon: Icons.flag_rounded,
                    text: 'Fällig: ${DateFormat('EEE, d. MMM · HH:mm').format(event.relatedTaskDeadline!)}',
                    sub: deadlineLabel(event.relatedTaskDeadline!, DateTime.now()),
                    color: _urgentAccent(event, DateTime.now()),
                  ),
                if (event.location != null) _SheetRow(icon: Icons.location_on_rounded, text: event.location!),
                if (event.description != null) _SheetRow(icon: Icons.notes_rounded, text: event.description!),
                // Umformuliert, seit das Pinnen über den Button unten reversibel ist.
                if (event.isFixed)
                  _SheetRow(icon: Icons.lock_rounded, text: 'Pinned — the scheduler will not move this'),
                if (event.isClass)
                  const _SheetRow(
                    icon: Icons.school_rounded,
                    text: 'Vorlesung — wird im Stundenplan verwaltet',
                    sub: 'Sie wird bei jeder Neuplanung neu erzeugt; Änderungen hier wären wieder weg.',
                  ),
                if (event.isCompleted)
                  const _SheetRow(
                    icon: Icons.check_circle_rounded,
                    text: 'Erledigt',
                    sub: 'Die Zeit ist gutgeschrieben; der Block bleibt als Protokoll stehen.',
                  ),
                const SizedBox(height: 12),
                // Nur Aufgabenblöcke: Gewohnheiten und Workouts haben ihre eigenen
                // Abschlusswege, das Backend weist alles andere mit 400 ab.
                if (event.id != null && event.isTask) ...[
                  SizedBox(
                    width: double.infinity,
                    child: FilledButton.icon(
                      icon: Icon(
                        event.isCompleted ? Icons.undo_rounded : Icons.check_rounded,
                        size: 16,
                      ),
                      label: Text(event.isCompleted ? 'Doch nicht erledigt' : 'Erledigt'),
                      style: FilledButton.styleFrom(
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.zero),
                      ),
                      onPressed: () async {
                        final provider = context.read<CalendarProvider>();
                        final messenger = ScaffoldMessenger.of(context);
                        Navigator.pop(context);
                        if (!await provider.setCompleted(event.id!, !event.isCompleted)) {
                          messenger.showSnackBar(const SnackBar(
                              content: Text('Konnte nicht gespeichert werden.')));
                        }
                      },
                    ),
                  ),
                  const SizedBox(height: 10),
                ],
                // Überspringen statt Löschen — nur für Gewohnheiten, Projektzeit und Trainings.
                // Bei denen war Löschen wirkungslos: die Woche stand danach unter ihrem Pensum
                // und der Scheduler legte binnen Sekunden Ersatz an.
                // Nur eine Richtung: übersprungene Blöcke zeichnet der Kalender nicht mehr,
                // dieses Sheet bekommt also nie einen zu sehen. Der Weg zurück führt über das
                // Rückgängig im Hinweis unten.
                if (event.id != null && event.isSkippable) ...[
                  SizedBox(
                    width: double.infinity,
                    child: OutlinedButton.icon(
                      icon: const Icon(Icons.skip_next_rounded, size: 16),
                      label: const Text('Diesmal überspringen'),
                      style: OutlinedButton.styleFrom(
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.zero),
                      ),
                      onPressed: () async {
                        final provider = context.read<CalendarProvider>();
                        final messenger = ScaffoldMessenger.of(context);
                        final id = event.id!;
                        Navigator.pop(context);

                        if (!await provider.setSkipped(id, true)) {
                          messenger.showSnackBar(const SnackBar(
                              content: Text('Konnte nicht gespeichert werden.')));
                          return;
                        }

                        // Der Block verschwindet aus dem Kalender, sobald er übersprungen ist —
                        // die Zeit ist ja wieder frei. Damit ist er auch nicht mehr antippbar,
                        // deshalb führt der einzige Weg zurück über dieses Rückgängig.
                        messenger.showSnackBar(SnackBar(
                          content: const Text('Übersprungen — die Zeit ist wieder frei.'),
                          duration: const Duration(seconds: 6),
                          action: SnackBarAction(
                            label: 'Rückgängig',
                            onPressed: () => provider.setSkipped(id, false),
                          ),
                        ));
                      },
                    ),
                  ),
                  const SizedBox(height: 10),
                ],
                // Pinnen, Bearbeiten und Löschen fehlen bei Vorlesungen bewusst: das Backend
                // weist sie mit 400 ab, ein Knopf wäre also nur ein Weg in eine Fehlermeldung.
                if (event.id != null && !event.isClass) ...[
                  SizedBox(
                    width: double.infinity,
                    child: OutlinedButton.icon(
                      icon: Icon(
                        event.isFixed ? Icons.lock_open_rounded : Icons.push_pin_rounded,
                        size: 16,
                      ),
                      label: Text(event.isFixed ? 'Unpin — let AI reschedule' : 'Pin to this time'),
                      style: OutlinedButton.styleFrom(
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.zero),
                      ),
                      onPressed: () async {
                        final provider = context.read<CalendarProvider>();
                        Navigator.pop(context);
                        await provider.setPinned(event.id!, !event.isFixed);
                      },
                    ),
                  ),
                  const SizedBox(height: 10),
                ],
                if (!event.isClass)
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton.icon(
                        icon: const Icon(Icons.edit_rounded, size: 16),
                        label: const Text('Edit'),
                        onPressed: () {
                          Navigator.pop(context);
                          if (!context.mounted) return;
                          showModalBottomSheet(
                            context: context,
                            backgroundColor: Colors.transparent,
                            isScrollControlled: true,
                            builder: (_) => CreateEventSheet(
                              selectedDay: event.startTime,
                              existingEvent: event,
                            ),
                          );
                        },
                        style: OutlinedButton.styleFrom(
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.zero),
                        ),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: FilledButton.icon(
                        icon: const Icon(Icons.delete_rounded, size: 16),
                        label: const Text('Delete'),
                        style: FilledButton.styleFrom(
                          backgroundColor: const Color(0xFFEF4444),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.zero),
                        ),
                        onPressed: () async {
                          if (event.id != null) {
                            await context.read<CalendarProvider>().deleteEvent(event.id!);
                          }
                          if (context.mounted) Navigator.pop(context);
                        },
                      ),
                    ),
                  ],
                ),
                SizedBox(height: MediaQuery.of(context).viewInsets.bottom),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _SheetRow extends StatelessWidget {
  final IconData icon;
  final String text;
  final String? sub;
  /// Hebt Symbol und Text hervor; ohne Angabe bleibt die Zeile im gewohnten Grau.
  final Color? color;
  const _SheetRow({required this.icon, required this.text, this.sub, this.color});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 14),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: 16, color: color ?? Colors.grey),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(text,
                    style: TextStyle(
                        fontSize: 13,
                        fontWeight: color != null ? FontWeight.w700 : FontWeight.w500,
                        color: color)),
                if (sub != null) Text(sub!, style: const TextStyle(fontSize: 11, color: Colors.grey)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
