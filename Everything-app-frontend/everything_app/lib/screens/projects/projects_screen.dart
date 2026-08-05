import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';
import 'package:intl/intl.dart';
import '../../config/app_theme.dart';
import '../../providers/project_provider.dart';
import '../../models/project.dart';
import '../../widgets/kinetic_card.dart';
import '../../widgets/pointer_aware_draggable.dart';

/// Akzent des Projekte-Space — identisch zur Kachel im Spaces-Grid und zur Blockfarbe,
/// die der Scheduler den Projektzeiten im Kalender gibt.
const Color _kProjectAccent = AppTheme.projectsColor;

// ─── Spalten ──────────────────────────────────────────────────────────────────
//
// [accepts] statt eines einzelnen Status, weil das Backend mehr Status kennt, als das Board
// Spalten hat: ACTIVE vergibt updateProjectStatistics automatisch, IN_PROGRESS existiert im
// Enum, CANCELLED entsteht manuell. Ohne die Sammelspalten verschwanden solche Projekte
// lautlos vom Board. Invariante: jeder Status landet in genau einer Spalte.
const _columns = [
  _ColDef('PLANNING', ['PLANNING'], 'Geplant', Color(0xFF60A5FA),
      'Noch nicht begonnen — hierher gehören frische Ideen'),
  _ColDef('ACTIVE', ['ACTIVE', 'IN_PROGRESS'], 'Aktiv', _kProjectAccent,
      'Wird gerade bearbeitet und bekommt Projektzeit im Kalender'),
  _ColDef('ON_HOLD', ['ON_HOLD'], 'Pausiert', Color(0xFFFBBF24),
      'Ruht — der Scheduler plant hierfür keine Zeit ein'),
  _ColDef('COMPLETED', ['COMPLETED', 'CANCELLED'], 'Fertig', Color(0xFF34D399),
      'Abgeschlossen oder abgebrochen'),
];

class _ColDef {
  /// Status, den ein Drop in diese Spalte setzt.
  final String status;
  /// Alle Status, die in dieser Spalte angezeigt werden.
  final List<String> accepts;
  final String label;
  final Color color;
  final String hint;
  const _ColDef(this.status, this.accepts, this.label, this.color, this.hint);
}

// ─── Screen ───────────────────────────────────────────────────────────────────
class ProjectsScreen extends StatefulWidget {
  const ProjectsScreen({super.key});
  @override
  State<ProjectsScreen> createState() => _ProjectsScreenState();
}

class _ProjectsScreenState extends State<ProjectsScreen> {
  final PageController _pageCtrl = PageController();
  int _currentPage = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) =>
        context.read<ProjectProvider>().loadProjects());
  }

  @override
  void dispose() {
    _pageCtrl.dispose();
    super.dispose();
  }

  void _openCreate({Project? editing}) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => _CreateProjectSheet(editing: editing),
    );
  }

  void _openDetail(Project project) {
    if (project.id != null) context.push('/projects/${project.id}');
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final provider = context.watch<ProjectProvider>();

    return Scaffold(
      backgroundColor: theme.colorScheme.surface,
      appBar: AppBar(
        backgroundColor: theme.colorScheme.surface,
        elevation: 0,
        leading: const BackButton(color: _kProjectAccent),
        title: Text(
          'PROJEKTE',
          style: theme.textTheme.titleLarge?.copyWith(
            fontWeight: FontWeight.w900,
            letterSpacing: -1.0,
            color: _kProjectAccent,
          ),
        ),
      ),
      floatingActionButton: FloatingActionButton(
        backgroundColor: _kProjectAccent,
        shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
        onPressed: () => _openCreate(),
        child: const Icon(Icons.add, color: Colors.black),
      ),
      body: provider.isLoading
          ? const Center(child: CircularProgressIndicator(color: _kProjectAccent))
          : LayoutBuilder(
              builder: (context, constraints) {
                final isNarrow = constraints.maxWidth < 600;
                return Column(
                  children: [
                    const SizedBox(height: 16),
                    if (isNarrow) _buildColumnTabStrip(provider),
                    Expanded(
                      child: isNarrow
                          ? _buildPageView(provider)
                          : _buildWideRow(provider),
                    ),
                  ],
                );
              },
            ),
    );
  }

  Widget _buildColumnTabStrip(ProjectProvider provider) {
    final theme = Theme.of(context);
    return SizedBox(
      height: 40,
      child: ListView.builder(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 12),
        itemCount: _columns.length,
        itemBuilder: (_, i) {
          final col = _columns[i];
          final isSelected = _currentPage == i;
          final count = provider.byStatus(col.accepts).length;
          return GestureDetector(
            onTap: () {
              _pageCtrl.animateToPage(i,
                  duration: const Duration(milliseconds: 250), curve: Curves.easeInOut);
              setState(() => _currentPage = i);
            },
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 150),
              margin: const EdgeInsets.only(right: 8),
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
              decoration: BoxDecoration(
                color: isSelected ? col.color.withValues(alpha: 0.15) : Colors.transparent,
                border: Border.all(
                  color: isSelected ? col.color : theme.colorScheme.outlineVariant,
                  width: 1.5,
                ),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.radio_button_unchecked, color: col.color, size: 12),
                  const SizedBox(width: 6),
                  Text(
                    '${col.label} $count',
                    style: theme.textTheme.labelMedium?.copyWith(
                      color: isSelected
                          ? theme.colorScheme.onSurface
                          : theme.colorScheme.onSurfaceVariant,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _buildPageView(ProjectProvider provider) {
    return PageView.builder(
      controller: _pageCtrl,
      onPageChanged: (i) => setState(() => _currentPage = i),
      itemCount: _columns.length,
      itemBuilder: (_, i) => Padding(
        padding: const EdgeInsets.fromLTRB(12, 8, 12, 12),
        child: _column(_columns[i], provider),
      ),
    );
  }

  Widget _buildWideRow(ProjectProvider provider) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: _columns.map((col) => Expanded(child: _column(col, provider))).toList(),
      ),
    );
  }

  Widget _column(_ColDef col, ProjectProvider provider) {
    return _KanbanColumn(
      colDef: col,
      projects: provider.byStatus(col.accepts),
      onDrop: (p) => provider.updateProject(p.copyWith(status: col.status)),
      onEdit: (p) => _openCreate(editing: p),
      onDelete: _confirmDelete,
      onTap: _openDetail,
    );
  }

  Future<void> _confirmDelete(Project p) async {
    final theme = Theme.of(context);
    final ok = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        backgroundColor: theme.colorScheme.surfaceContainerHigh,
        shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
        title: const Text('Löschen?'),
        content: Text(
          '„${p.name}" wirklich löschen? Zugeordnete Aufgaben bleiben erhalten und verlieren '
          'nur ihre Projektzuordnung.',
          style: theme.textTheme.bodySmall,
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Abbrechen')),
          FilledButton(
            style: FilledButton.styleFrom(
              backgroundColor: theme.colorScheme.error,
              shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
            ),
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Löschen'),
          ),
        ],
      ),
    );
    if (ok == true && p.id != null && mounted) {
      context.read<ProjectProvider>().deleteProject(p.id!);
    }
  }
}

// ─── Kanban-Spalte ────────────────────────────────────────────────────────────
class _KanbanColumn extends StatefulWidget {
  final _ColDef colDef;
  final List<Project> projects;
  final ValueChanged<Project> onDrop;
  final ValueChanged<Project> onEdit;
  final ValueChanged<Project> onDelete;
  final ValueChanged<Project> onTap;

  const _KanbanColumn({
    required this.colDef,
    required this.projects,
    required this.onDrop,
    required this.onEdit,
    required this.onDelete,
    required this.onTap,
  });

  @override
  State<_KanbanColumn> createState() => _KanbanColumnState();
}

class _KanbanColumnState extends State<_KanbanColumn> {
  bool _isHovered = false;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final col = widget.colDef;
    final count = widget.projects.length;

    return DragTarget<Project>(
      onWillAcceptWithDetails: (d) {
        setState(() => _isHovered = true);
        return !col.accepts.contains(d.data.status);
      },
      onLeave: (_) => setState(() => _isHovered = false),
      onAcceptWithDetails: (d) {
        setState(() => _isHovered = false);
        widget.onDrop(d.data);
      },
      builder: (ctx, candidates, _) {
        return AnimatedContainer(
          duration: const Duration(milliseconds: 150),
          width: double.infinity,
          margin: const EdgeInsets.only(right: 8, bottom: 8),
          decoration: BoxDecoration(
            color: _isHovered
                ? col.color.withValues(alpha: 0.07)
                : theme.colorScheme.surfaceContainerLow,
            border: Border.all(
              color: _isHovered ? col.color.withValues(alpha: 0.5) : Colors.transparent,
              width: 1.5,
            ),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 16, 12, 12),
                child: Row(
                  children: [
                    Icon(Icons.radio_button_unchecked, color: col.color, size: 14),
                    const SizedBox(width: 8),
                    Text(
                      col.label.toUpperCase(),
                      style: theme.textTheme.labelMedium?.copyWith(
                        fontWeight: FontWeight.w900,
                        letterSpacing: 1.2,
                      ),
                    ),
                    const SizedBox(width: 8),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                      color: theme.colorScheme.surfaceContainerHighest,
                      child: Text('$count', style: theme.textTheme.labelSmall),
                    ),
                  ],
                ),
              ),
              Expanded(
                child: count == 0
                    ? Padding(
                        padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
                        child: Text(
                          col.hint,
                          style: theme.textTheme.bodySmall
                              ?.copyWith(color: theme.colorScheme.outlineVariant),
                        ),
                      )
                    : ListView.builder(
                        padding: const EdgeInsets.all(8),
                        itemCount: widget.projects.length,
                        itemBuilder: (_, i) => _ProjectKanbanCard(
                          project: widget.projects[i],
                          accentColor: col.color,
                          onEdit: widget.onEdit,
                          onDelete: widget.onDelete,
                          onTap: widget.onTap,
                        ),
                      ),
              ),
            ],
          ),
        );
      },
    );
  }
}

// ─── Projektkarte ─────────────────────────────────────────────────────────────
class _ProjectKanbanCard extends StatelessWidget {
  final Project project;
  final Color accentColor;
  final ValueChanged<Project> onEdit;
  final ValueChanged<Project> onDelete;
  final ValueChanged<Project> onTap;

  const _ProjectKanbanCard({
    required this.project,
    required this.accentColor,
    required this.onEdit,
    required this.onDelete,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    // Mit einem nackten Draggable gewinnt auf dem Touchscreen die Karte den Gestenwettstreit
    // gegen die ListView — die Spalte ließ sich dann nicht mehr scrollen, ohne versehentlich
    // ein Projekt zu verschieben. PointerAwareDraggable verlangt am Finger einen Long-Press
    // und zieht mit der Maus weiterhin sofort.
    return PointerAwareDraggable<Project>(
      data: project,
      feedback: Material(
        color: Colors.transparent,
        child: Opacity(
          opacity: 0.85,
          child: SizedBox(width: 224, child: _cardBody(context)),
        ),
      ),
      childWhenDragging: Opacity(opacity: 0.3, child: _cardBody(context)),
      child: GestureDetector(
        onTap: () => onTap(project),
        child: _cardBody(context),
      ),
    );
  }

  Widget _cardBody(BuildContext context) {
    final theme = Theme.of(context);
    final fmt = DateFormat('dd.MM.yy');
    // Der Fortschritt kommt aus den verknüpften Aufgaben — ohne Aufgaben gibt es keinen.
    final hasTasks = project.tasksTotal > 0;
    final progress = hasTasks ? project.completionPercentage / 100 : 0.0;

    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      child: KineticCard(
        padding: const EdgeInsets.all(12),
        backgroundColor: theme.colorScheme.surfaceContainerHigh,
        borderColor: theme.colorScheme.outlineVariant.withValues(alpha: 0.4),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    project.name,
                    style: theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w700),
                  ),
                ),
                PopupMenuButton<String>(
                  padding: EdgeInsets.zero,
                  iconSize: 16,
                  icon: Icon(Icons.more_horiz, color: theme.colorScheme.onSurfaceVariant),
                  onSelected: (v) {
                    if (v == 'edit') onEdit(project);
                    if (v == 'delete') onDelete(project);
                  },
                  itemBuilder: (_) => [
                    const PopupMenuItem(value: 'edit', child: Text('Bearbeiten')),
                    PopupMenuItem(
                      value: 'delete',
                      child: Text('Löschen', style: TextStyle(color: theme.colorScheme.error)),
                    ),
                  ],
                ),
              ],
            ),
            if (project.status == 'CANCELLED')
              Padding(
                padding: const EdgeInsets.only(top: 2),
                child: Text(
                  'ABGEBROCHEN',
                  style: theme.textTheme.labelSmall?.copyWith(
                    fontSize: 9,
                    letterSpacing: 1.2,
                    fontWeight: FontWeight.w900,
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ),
            if (project.description != null && project.description!.isNotEmpty) ...[
              const SizedBox(height: 4),
              Text(
                project.description!,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.bodySmall
                    ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
              ),
            ],
            const SizedBox(height: 10),
            LinearProgressIndicator(
              value: progress,
              backgroundColor: theme.colorScheme.surfaceContainerLowest,
              valueColor: AlwaysStoppedAnimation<Color>(accentColor),
              minHeight: 3,
            ),
            const SizedBox(height: 4),
            Text(
              hasTasks
                  ? '${project.tasksCompleted}/${project.tasksTotal} Aufgaben · ${project.completionPercentage}%'
                  : 'Noch keine Aufgaben zugeordnet',
              style: theme.textTheme.labelSmall
                  ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                Icon(Icons.repeat, size: 11, color: theme.colorScheme.onSurfaceVariant),
                const SizedBox(width: 3),
                Text(
                  project.weeklySessionCount == 0
                      ? 'keine Projektzeit'
                      : '${project.weeklySessionCount}×/Wo · ${_hours(project.weeklyMinutes)}',
                  style: theme.textTheme.labelSmall
                      ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                ),
                const Spacer(),
                if (project.targetEndDate != null)
                  Row(children: [
                    Icon(Icons.event,
                        size: 11,
                        color: project.isOverdue
                            ? theme.colorScheme.error
                            : theme.colorScheme.onSurfaceVariant),
                    const SizedBox(width: 3),
                    Text(
                      fmt.format(project.targetEndDate!),
                      style: theme.textTheme.labelSmall?.copyWith(
                        color: project.isOverdue
                            ? theme.colorScheme.error
                            : theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ]),
              ],
            ),
          ],
        ),
      ),
    );
  }

  static String _hours(int minutes) {
    final h = minutes ~/ 60;
    final m = minutes % 60;
    if (h == 0) return '${m}min';
    return m == 0 ? '${h}h' : '${h}h$m';
  }
}

// ─── Anlegen / Bearbeiten ─────────────────────────────────────────────────────
class _CreateProjectSheet extends StatefulWidget {
  final Project? editing;
  const _CreateProjectSheet({this.editing});
  @override
  State<_CreateProjectSheet> createState() => _CreateProjectSheetState();
}

class _CreateProjectSheetState extends State<_CreateProjectSheet> {
  final _nameCtrl = TextEditingController();
  final _descCtrl = TextEditingController();
  // 'PLANNING' und nicht 'NEW': 'NEW' ist kein gültiger Status, solche Projekte landeten in
  // gar keiner Spalte.
  String _status = 'PLANNING';
  DateTime? _startDate;
  DateTime? _targetEnd;
  int _sessions = 3;
  int _sessionMins = 60;

  @override
  void initState() {
    super.initState();
    final e = widget.editing;
    if (e != null) {
      _nameCtrl.text = e.name;
      _descCtrl.text = e.description ?? '';
      _status = e.status;
      _startDate = e.startDate;
      _targetEnd = e.targetEndDate;
      _sessions = e.weeklySessionCount;
      _sessionMins = e.sessionDurationMinutes;
    }
  }

  @override
  void dispose() {
    _nameCtrl.dispose();
    _descCtrl.dispose();
    super.dispose();
  }

  Future<void> _pickDate(bool isStart) async {
    final picked = await showDatePicker(
      context: context,
      initialDate: (isStart ? _startDate : _targetEnd) ?? DateTime.now(),
      firstDate: DateTime(2020),
      lastDate: DateTime(2035),
    );
    if (picked != null) {
      setState(() {
        if (isStart) {
          _startDate = picked;
        } else {
          _targetEnd = picked;
        }
      });
    }
  }

  Future<void> _save() async {
    if (_nameCtrl.text.trim().isEmpty) return;
    final project = Project(
      id: widget.editing?.id,
      name: _nameCtrl.text.trim(),
      description: _descCtrl.text.trim().isNotEmpty ? _descCtrl.text.trim() : null,
      status: _status,
      startDate: _startDate,
      targetEndDate: _targetEnd,
      weeklySessionCount: _sessions,
      sessionDurationMinutes: _sessionMins,
    );
    final provider = context.read<ProjectProvider>();
    final ok = widget.editing != null
        ? await provider.updateProject(project)
        : await provider.addProject(project);
    if (ok && mounted) Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final fmt = DateFormat('dd.MM.yyyy');

    return Container(
      color: theme.colorScheme.surfaceContainerLow,
      padding: EdgeInsets.only(
        bottom: MediaQuery.of(context).viewInsets.bottom + 24,
        left: 20,
        right: 20,
        top: 16,
      ),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
              Text(
                widget.editing != null ? 'BEARBEITEN' : 'NEUES PROJEKT',
                style: theme.textTheme.titleMedium?.copyWith(
                  fontWeight: FontWeight.w900,
                  letterSpacing: 0.5,
                ),
              ),
              IconButton(
                icon: Icon(Icons.close, color: theme.colorScheme.onSurfaceVariant),
                onPressed: () => Navigator.pop(context),
              ),
            ]),
            const SizedBox(height: 14),
            _label('Name'),
            const SizedBox(height: 6),
            TextField(controller: _nameCtrl, autofocus: true, decoration: _deco('Projektname…')),
            const SizedBox(height: 12),
            _label('Beschreibung'),
            const SizedBox(height: 6),
            TextField(controller: _descCtrl, maxLines: 2, decoration: _deco('Optional…')),
            const SizedBox(height: 12),
            _label('Status'),
            const SizedBox(height: 8),
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: [
                  for (final col in _columns)
                    Padding(
                      padding: const EdgeInsets.only(right: 8),
                      child: ChoiceChip(
                        label: Text(col.label),
                        selected: col.accepts.contains(_status),
                        selectedColor: col.color.withValues(alpha: 0.3),
                        shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
                        side: BorderSide(
                          color: col.accepts.contains(_status)
                              ? col.color
                              : theme.colorScheme.outlineVariant,
                        ),
                        onSelected: (_) => setState(() => _status = col.status),
                      ),
                    ),
                ],
              ),
            ),
            const SizedBox(height: 12),
            Row(children: [
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  _label('Startdatum'),
                  const SizedBox(height: 6),
                  GestureDetector(
                    onTap: () => _pickDate(true),
                    child: _readonlyBox(_startDate != null ? fmt.format(_startDate!) : 'Wählen…'),
                  ),
                ]),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  _label('Zieldatum'),
                  const SizedBox(height: 6),
                  GestureDetector(
                    onTap: () => _pickDate(false),
                    child: _readonlyBox(_targetEnd != null ? fmt.format(_targetEnd!) : 'Wählen…'),
                  ),
                ]),
              ),
            ]),
            const SizedBox(height: 16),
            _label('Projektzeit im Kalender'),
            const SizedBox(height: 10),
            Row(children: [
              Expanded(
                child: Column(children: [
                  Text('$_sessions× / Woche',
                      style: theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w700)),
                  Slider(
                    value: _sessions.toDouble(),
                    // Min 0: "keine automatische Projektzeit" muss einstellbar sein.
                    min: 0,
                    max: 14,
                    divisions: 14,
                    activeColor: _kProjectAccent,
                    label: '$_sessions Sessions',
                    onChanged: (v) => setState(() => _sessions = v.round()),
                  ),
                ]),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(children: [
                  Text('$_sessionMins Min / Session',
                      style: theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w700)),
                  Slider(
                    value: _sessionMins.toDouble(),
                    min: 15,
                    max: 240,
                    divisions: 15,
                    activeColor: _kProjectAccent,
                    label: '$_sessionMins Min',
                    onChanged: (v) => setState(() => _sessionMins = (v / 15).round() * 15),
                  ),
                ]),
              ),
            ]),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              color: _kProjectAccent.withValues(alpha: 0.1),
              child: Row(children: [
                const Icon(Icons.timer_outlined, color: _kProjectAccent, size: 16),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    _sessions == 0
                        ? 'Keine automatischen Blöcke — nur verknüpfte Aufgaben werden geplant'
                        : '${(_sessions * _sessionMins / 60).toStringAsFixed(1)} Stunden / Woche in freien Kalenderlücken',
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: _kProjectAccent,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
              ]),
            ),
            const SizedBox(height: 20),
            SizedBox(
              width: double.infinity,
              height: 48,
              child: FilledButton(
                onPressed: _save,
                style: FilledButton.styleFrom(
                  backgroundColor: _kProjectAccent,
                  foregroundColor: Colors.black,
                  shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
                ),
                child: Text(widget.editing != null ? 'Speichern' : 'Erstellen',
                    style: const TextStyle(fontWeight: FontWeight.w900)),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _label(String t) => Builder(
        builder: (context) => Text(
          t.toUpperCase(),
          style: Theme.of(context).textTheme.labelSmall?.copyWith(
                fontSize: 9,
                letterSpacing: 1.2,
                fontWeight: FontWeight.w900,
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              ),
        ),
      );

  InputDecoration _deco(String hint) {
    final theme = Theme.of(context);
    return InputDecoration(
      hintText: hint,
      filled: true,
      fillColor: theme.colorScheme.surfaceContainerHigh,
      border: OutlineInputBorder(
        borderRadius: BorderRadius.zero,
        borderSide: BorderSide(color: theme.colorScheme.outlineVariant),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.zero,
        borderSide: BorderSide(color: theme.colorScheme.outlineVariant),
      ),
      focusedBorder: const OutlineInputBorder(
        borderRadius: BorderRadius.zero,
        borderSide: BorderSide(color: _kProjectAccent, width: 2),
      ),
      contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
    );
  }

  Widget _readonlyBox(String t) {
    final theme = Theme.of(context);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 11),
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerHigh,
        border: Border.all(color: theme.colorScheme.outlineVariant),
      ),
      child: Text(t, style: theme.textTheme.bodySmall),
    );
  }
}
