import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../../config/app_theme.dart';

class StudyScreen extends StatefulWidget {
  const StudyScreen({super.key});

  @override
  State<StudyScreen> createState() => _StudyScreenState();
}

class _StudyScreenState extends State<StudyScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 4, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Studium'),
        backgroundColor: AppTheme.studyColor,
        foregroundColor: Colors.white,
        bottom: TabBar(
          controller: _tabController,
          labelColor: Colors.white,
          unselectedLabelColor: Colors.white70,
          indicatorColor: Colors.white,
          tabs: const [
            Tab(icon: Icon(Icons.calendar_today), text: 'Stundenplan'),
            Tab(icon: Icon(Icons.notes), text: 'Notizen'),
            Tab(icon: Icon(Icons.style), text: 'Karteikarten'),
            Tab(icon: Icon(Icons.grade), text: 'Noten'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabController,
        children: const [
          _TimetableTab(),
          _NotesTab(),
          _FlashcardsTab(),
          _GradesTab(),
        ],
      ),
    );
  }
}

// ─── Timetable Tab ─────────────────────────────────────────────────────────────

class _TimetableTab extends StatelessWidget {
  const _TimetableTab();

  final List<_TimetableEntry> _entries = const [
    _TimetableEntry(day: 'Mo', time: '08:00', subject: 'Mathematik',
        room: 'H1.01', color: Color(0xFF3B82F6)),
    _TimetableEntry(day: 'Mo', time: '10:00', subject: 'Programmierung',
        room: 'PC-Pool', color: Color(0xFF10B981)),
    _TimetableEntry(day: 'Di', time: '09:00', subject: 'Physik',
        room: 'H2.12', color: Color(0xFFF59E0B)),
    _TimetableEntry(day: 'Mi', time: '14:00', subject: 'Datenbanken',
        room: 'H1.03', color: Color(0xFF8B5CF6)),
    _TimetableEntry(day: 'Do', time: '08:00', subject: 'Algorithmen',
        room: 'H2.01', color: Color(0xFFEF4444)),
    _TimetableEntry(day: 'Fr', time: '11:00', subject: 'Seminar',
        room: 'S0.01', color: Color(0xFFEC4899)),
  ];

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final days = ['Mo', 'Di', 'Mi', 'Do', 'Fr'];

    return ListView(
      padding: const EdgeInsets.all(16),
      children: days.map((day) {
        final dayEntries = _entries.where((e) => e.day == day).toList();
        if (dayEntries.isEmpty) return const SizedBox.shrink();
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 8),
              child: Text(
                _fullDayName(day),
                style: theme.textTheme.titleMedium
                    ?.copyWith(fontWeight: FontWeight.bold),
              ),
            ),
            ...dayEntries.map((e) => _TimetableCard(entry: e)),
            const SizedBox(height: 8),
          ],
        );
      }).toList(),
    );
  }

  String _fullDayName(String day) {
    const names = {
      'Mo': 'Montag', 'Di': 'Dienstag', 'Mi': 'Mittwoch',
      'Do': 'Donnerstag', 'Fr': 'Freitag',
    };
    return names[day] ?? day;
  }
}

class _TimetableEntry {
  final String day;
  final String time;
  final String subject;
  final String room;
  final Color color;
  const _TimetableEntry(
      {required this.day,
      required this.time,
      required this.subject,
      required this.room,
      required this.color});
}

class _TimetableCard extends StatelessWidget {
  final _TimetableEntry entry;
  const _TimetableCard({required this.entry});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: entry.color.withOpacity(0.1),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: entry.color.withOpacity(0.3)),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            decoration: BoxDecoration(
              color: entry.color,
              borderRadius: BorderRadius.circular(8),
            ),
            child: Text(entry.time,
                style: const TextStyle(
                    color: Colors.white, fontWeight: FontWeight.bold)),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(entry.subject,
                    style: const TextStyle(fontWeight: FontWeight.bold)),
                Text('Raum: ${entry.room}',
                    style: const TextStyle(color: Colors.grey, fontSize: 13)),
              ],
            ),
          ),
          Icon(Icons.chevron_right, color: entry.color),
        ],
      ),
    );
  }
}

// ─── Notes Tab ─────────────────────────────────────────────────────────────────

class _NotesTab extends StatefulWidget {
  const _NotesTab();

  @override
  State<_NotesTab> createState() => _NotesTabState();
}

class _NotesTabState extends State<_NotesTab> {
  final List<Map<String, dynamic>> _notes = [
    {
      'title': 'Vorlesung Analysis - Kapitel 3',
      'preview': 'Integralrechnung, Hauptsatz der Differential...',
      'date': DateTime.now().subtract(const Duration(days: 1)),
      'subject': 'Mathematik',
    },
    {
      'title': 'Java OOP Grundlagen',
      'preview': 'Klassen, Objekte, Vererbung, Polymorphismus...',
      'date': DateTime.now().subtract(const Duration(days: 3)),
      'subject': 'Programmierung',
    },
  ];

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Stack(
      children: [
        _notes.isEmpty
            ? const Center(
                child: Text('Noch keine Notizen vorhanden',
                    style: TextStyle(color: Colors.grey)))
            : ListView.builder(
                padding: const EdgeInsets.all(16),
                itemCount: _notes.length,
                itemBuilder: (_, i) {
                  final note = _notes[i];
                  return Card(
                    margin: const EdgeInsets.only(bottom: 8),
                    child: ListTile(
                      leading: CircleAvatar(
                        backgroundColor: AppTheme.studyColor.withOpacity(0.1),
                        child: Icon(Icons.notes, color: AppTheme.studyColor),
                      ),
                      title: Text(note['title'],
                          style: const TextStyle(fontWeight: FontWeight.w600)),
                      subtitle: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(note['preview'],
                              maxLines: 1, overflow: TextOverflow.ellipsis),
                          Text(
                            '${note['subject']} • vor ${DateTime.now().difference(note['date']).inDays} Tagen',
                            style: const TextStyle(
                                fontSize: 11, color: Colors.grey),
                          ),
                        ],
                      ),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: () => _openNote(context, note),
                    ),
                  );
                },
              ),
        Positioned(
          bottom: 16,
          right: 16,
          child: FloatingActionButton(
            backgroundColor: AppTheme.studyColor,
            onPressed: () => _createNote(context),
            child: const Icon(Icons.add, color: Colors.white),
          ),
        ),
      ],
    );
  }

  void _openNote(BuildContext context, Map<String, dynamic> note) {
    Navigator.push(context, MaterialPageRoute(
      builder: (_) => _NoteEditorScreen(note: note),
    ));
  }

  void _createNote(BuildContext context) {
    Navigator.push(context, MaterialPageRoute(
      builder: (_) => _NoteEditorScreen(note: null),
    ));
  }
}

class _NoteEditorScreen extends StatelessWidget {
  final Map<String, dynamic>? note;
  const _NoteEditorScreen({required this.note});

  @override
  Widget build(BuildContext context) {
    final titleController = TextEditingController(text: note?['title'] ?? '');
    final contentController =
        TextEditingController(text: note?['preview'] ?? '');
    return Scaffold(
      appBar: AppBar(
        title: Text(note == null ? 'Neue Notiz' : 'Notiz bearbeiten'),
        backgroundColor: AppTheme.studyColor,
        foregroundColor: Colors.white,
        actions: [
          IconButton(
            icon: const Icon(Icons.save),
            onPressed: () => Navigator.pop(context),
          ),
        ],
      ),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            TextField(
              controller: titleController,
              style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold),
              decoration: const InputDecoration(
                hintText: 'Titel...',
                border: InputBorder.none,
              ),
            ),
            const Divider(),
            Expanded(
              child: TextField(
                controller: contentController,
                maxLines: null,
                expands: true,
                decoration: const InputDecoration(
                  hintText: 'Notiz schreiben...',
                  border: InputBorder.none,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ─── Flashcards Tab ────────────────────────────────────────────────────────────

class _FlashcardsTab extends StatefulWidget {
  const _FlashcardsTab();

  @override
  State<_FlashcardsTab> createState() => _FlashcardsTabState();
}

class _FlashcardsTabState extends State<_FlashcardsTab> {
  final List<Map<String, String>> _cards = [
    {'q': 'Was ist eine abstrakte Klasse?', 'a': 'Eine Klasse, die nicht instanziiert werden kann und als Vorlage für Unterklassen dient.'},
    {'q': 'Was ist Polymorphismus?', 'a': 'Die Fähigkeit eines Objekts, sich als ein Objekt eines anderen Typs zu verhalten.'},
    {'q': 'Was ist ein Interface?', 'a': 'Ein Vertrag, der definiert, welche Methoden eine Klasse implementieren muss.'},
  ];
  int _currentIndex = 0;
  bool _showAnswer = false;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    if (_cards.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.style, size: 64, color: Colors.grey),
            const SizedBox(height: 16),
            const Text('Noch keine Karteikarten',
                style: TextStyle(color: Colors.grey)),
            const SizedBox(height: 24),
            FilledButton.icon(
              icon: const Icon(Icons.add),
              label: const Text('Erste Karteikarte erstellen'),
              onPressed: () {},
            ),
          ],
        ),
      );
    }

    final card = _cards[_currentIndex];
    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        children: [
          Text(
            'Karte ${_currentIndex + 1} von ${_cards.length}',
            style: const TextStyle(color: Colors.grey),
          ),
          const SizedBox(height: 8),
          LinearProgressIndicator(
            value: (_currentIndex + 1) / _cards.length,
            backgroundColor: Colors.grey.shade200,
            valueColor: AlwaysStoppedAnimation<Color>(AppTheme.studyColor),
          ),
          const SizedBox(height: 32),
          Expanded(
            child: GestureDetector(
              onTap: () => setState(() => _showAnswer = !_showAnswer),
              child: AnimatedSwitcher(
                duration: const Duration(milliseconds: 300),
                child: Container(
                  key: ValueKey(_showAnswer),
                  width: double.infinity,
                  padding: const EdgeInsets.all(32),
                  decoration: BoxDecoration(
                    color: _showAnswer
                        ? AppTheme.studyColor.withOpacity(0.1)
                        : theme.colorScheme.surfaceVariant,
                    borderRadius: BorderRadius.circular(24),
                    border: Border.all(
                      color: _showAnswer
                          ? AppTheme.studyColor
                          : Colors.transparent,
                    ),
                  ),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text(
                        _showAnswer ? 'Antwort' : 'Frage',
                        style: TextStyle(
                          color: _showAnswer
                              ? AppTheme.studyColor
                              : Colors.grey,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      const SizedBox(height: 16),
                      Text(
                        _showAnswer ? card['a']! : card['q']!,
                        textAlign: TextAlign.center,
                        style: theme.textTheme.titleMedium,
                      ),
                      const SizedBox(height: 16),
                      Text(
                        _showAnswer ? '' : 'Tippen zum Umdrehen',
                        style: const TextStyle(
                            color: Colors.grey, fontSize: 12),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
          const SizedBox(height: 24),
          if (_showAnswer)
            Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    icon: const Icon(Icons.close, color: Colors.red),
                    label: const Text('Nochmal',
                        style: TextStyle(color: Colors.red)),
                    onPressed: () {
                      setState(() => _showAnswer = false);
                    },
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: FilledButton.icon(
                    icon: const Icon(Icons.check),
                    label: const Text('Gewusst'),
                    style:
                        FilledButton.styleFrom(backgroundColor: Colors.green),
                    onPressed: () {
                      setState(() {
                        _showAnswer = false;
                        if (_currentIndex < _cards.length - 1) {
                          _currentIndex++;
                        } else {
                          _currentIndex = 0;
                        }
                      });
                    },
                  ),
                ),
              ],
            ),
        ],
      ),
    );
  }
}

// ─── Grades Tab ────────────────────────────────────────────────────────────────

class _GradesTab extends StatefulWidget {
  const _GradesTab();

  @override
  State<_GradesTab> createState() => _GradesTabState();
}

class _GradesTabState extends State<_GradesTab> {
  final List<Map<String, dynamic>> _grades = [
    {'subject': 'Mathematik', 'grade': 2.0, 'credits': 6, 'type': 'Klausur'},
    {'subject': 'Programmierung', 'grade': 1.3, 'credits': 8, 'type': 'Projekt'},
    {'subject': 'Physik', 'grade': 2.7, 'credits': 4, 'type': 'Klausur'},
    {'subject': 'Datenbanken', 'grade': 1.7, 'credits': 5, 'type': 'Mündlich'},
  ];

  double get _average {
    if (_grades.isEmpty) return 0;
    double sum = 0;
    int totalCredits = 0;
    for (final g in _grades) {
      sum += (g['grade'] as double) * (g['credits'] as int);
      totalCredits += g['credits'] as int;
    }
    return sum / totalCredits;
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // Average Card
        Container(
          padding: const EdgeInsets.all(24),
          decoration: BoxDecoration(
            gradient: LinearGradient(
              colors: [AppTheme.studyColor, AppTheme.studyColor.withOpacity(0.7)],
            ),
            borderRadius: BorderRadius.circular(20),
          ),
          child: Column(
            children: [
              const Text('Gesamtdurchschnitt',
                  style: TextStyle(color: Colors.white70, fontSize: 14)),
              const SizedBox(height: 8),
              Text(
                _average.toStringAsFixed(2),
                style: const TextStyle(
                    color: Colors.white,
                    fontSize: 48,
                    fontWeight: FontWeight.bold),
              ),
              Text(
                '${_grades.fold<int>(0, (s, g) => s + (g['credits'] as int))} ECTS gesammelt',
                style: const TextStyle(color: Colors.white70),
              ),
            ],
          ),
        ),
        const SizedBox(height: 24),
        Text('Einzelnoten',
            style: theme.textTheme.titleMedium
                ?.copyWith(fontWeight: FontWeight.bold)),
        const SizedBox(height: 12),
        ..._grades.map((g) => _GradeCard(grade: g)),
        const SizedBox(height: 16),
        OutlinedButton.icon(
          icon: const Icon(Icons.add),
          label: const Text('Note hinzufügen'),
          onPressed: () {},
        ),
      ],
    );
  }
}

class _GradeCard extends StatelessWidget {
  final Map<String, dynamic> grade;
  const _GradeCard({required this.grade});

  Color _gradeColor(double g) {
    if (g <= 1.5) return Colors.green;
    if (g <= 2.5) return Colors.blue;
    if (g <= 3.5) return Colors.orange;
    return Colors.red;
  }

  @override
  Widget build(BuildContext context) {
    final g = grade['grade'] as double;
    final color = _gradeColor(g);
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: color.withOpacity(0.1),
          child: Text(
            g.toString(),
            style:
                TextStyle(color: color, fontWeight: FontWeight.bold),
          ),
        ),
        title: Text(grade['subject'],
            style: const TextStyle(fontWeight: FontWeight.w600)),
        subtitle: Text('${grade['type']} • ${grade['credits']} ECTS'),
        trailing: Container(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
          decoration: BoxDecoration(
            color: color.withOpacity(0.1),
            borderRadius: BorderRadius.circular(8),
          ),
          child: Text(
            g <= 1.5 ? 'Sehr gut' : g <= 2.5 ? 'Gut' : g <= 3.5 ? 'Befriedigend' : 'Ausreichend',
            style: TextStyle(color: color, fontSize: 11),
          ),
        ),
      ),
    );
  }
}