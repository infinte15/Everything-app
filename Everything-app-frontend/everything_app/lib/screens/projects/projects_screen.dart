import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:intl/intl.dart';
import '../../providers/project_provider.dart';
import '../../models/project.dart';

// ─── Column definitions ───────────────────────────────────────────────────────
const _columns = [
  _ColDef('PLANNING',   'Planung',        Color(0xFF60A5FA), ''),
  _ColDef('ACTIVE',     'Aktiv',          Color(0xFF34D399), ''),
  _ColDef('ON_HOLD',    'Pausiert',       Color(0xFFFBBF24), ''),
  _ColDef('COMPLETED',  'Abgeschlossen',  Color(0xFF5856D6), ''),
];

class _ColDef {
  final String status;
  final String label;
  final Color color;
  final String hint;
  const _ColDef(this.status, this.label, this.color, this.hint);
}

// ─── Screen ───────────────────────────────────────────────────────────────────
class ProjectsScreen extends StatefulWidget {
  const ProjectsScreen({super.key});
  @override
  State<ProjectsScreen> createState() => _ProjectsScreenState();
}

class _ProjectsScreenState extends State<ProjectsScreen> {
  final _searchCtrl = TextEditingController();
  String _query = '';
  String? _draggingProjectId;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) =>
        context.read<ProjectProvider>().loadProjects());
    _searchCtrl.addListener(() => setState(() => _query = _searchCtrl.text.toLowerCase()));
  }

  @override
  void dispose() {
    _searchCtrl.dispose();
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

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<ProjectProvider>();
    final projects = provider.projects
        .where((p) => _query.isEmpty || p.name.toLowerCase().contains(_query))
        .toList();

    return Scaffold(
      backgroundColor: const Color(0xFF0A0A0B),
      appBar: AppBar(
        backgroundColor: const Color(0xFF111113),
        elevation: 0,
        leading: const BackButton(color: Color(0xFFC2C1FF)),
        title: Text('Projekte', style: GoogleFonts.spaceGrotesk(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 20)),
        actions: [
          IconButton(
            icon: const Icon(Icons.add, color: Color(0xFFC2C1FF)),
            onPressed: () => _openCreate(),
          ),
        ],
      ),
      body: provider.isLoading
          ? const Center(child: CircularProgressIndicator(color: Color(0xFF5856D6)))
          : Column(
              children: [
                // Search bar
                Padding(
                  padding: const EdgeInsets.fromLTRB(12, 12, 12, 0),
                  child: TextField(
                    controller: _searchCtrl,
                    style: const TextStyle(color: Colors.white, fontSize: 14),
                    decoration: InputDecoration(
                      hintText: 'Filter by keyword…',
                      hintStyle: const TextStyle(color: Color(0xFF555555)),
                      prefixIcon: const Icon(Icons.search, color: Color(0xFF555555), size: 18),
                      filled: true,
                      fillColor: const Color(0xFF1A1A1C),
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(8), borderSide: const BorderSide(color: Color(0xFF2A2A2D))),
                      enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(8), borderSide: const BorderSide(color: Color(0xFF2A2A2D))),
                      focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(8), borderSide: const BorderSide(color: Color(0xFF5856D6))),
                      contentPadding: const EdgeInsets.symmetric(vertical: 10),
                      isDense: true,
                    ),
                  ),
                ),
                const SizedBox(height: 12),
                // Kanban board
                Expanded(
                  child: ListView(
                    scrollDirection: Axis.horizontal,
                    padding: const EdgeInsets.symmetric(horizontal: 12),
                    children: _columns.map((col) {
                      final colProjects = projects.where((p) => p.status == col.status).toList();
                      return _KanbanColumn(
                        colDef: col,
                        projects: colProjects,
                        isDragging: _draggingProjectId != null,
                        onDrop: (project) {
                          final updated = project.copyWith(status: col.status);
                          context.read<ProjectProvider>().updateProject(updated);
                          setState(() => _draggingProjectId = null);
                        },
                        onDragStart: (id) => setState(() => _draggingProjectId = id),
                        onDragEnd: () => setState(() => _draggingProjectId = null),
                        onAdd: () => _openCreate(),
                        onEdit: (p) => _openCreate(editing: p),
                        onDelete: (p) => _confirmDelete(p),
                        onTap: (p) => Navigator.push(context, MaterialPageRoute(builder: (_) => _ProjectDetailScreen(project: p))),
                      );
                    }).toList(),
                  ),
                ),
              ],
            ),
    );
  }

  Future<void> _confirmDelete(Project p) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        backgroundColor: const Color(0xFF1A1A1C),
        title: Text('Löschen?', style: GoogleFonts.manrope(color: Colors.white)),
        content: Text('„${p.name}" wirklich löschen?', style: const TextStyle(color: Colors.grey)),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Abbrechen')),
          FilledButton(style: FilledButton.styleFrom(backgroundColor: Colors.red), onPressed: () => Navigator.pop(context, true), child: const Text('Löschen')),
        ],
      ),
    );
    if (ok == true && p.id != null && mounted) context.read<ProjectProvider>().deleteProject(p.id!);
  }
}

// ─── Kanban Column ────────────────────────────────────────────────────────────
class _KanbanColumn extends StatefulWidget {
  final _ColDef colDef;
  final List<Project> projects;
  final bool isDragging;
  final ValueChanged<Project> onDrop;
  final ValueChanged<String> onDragStart;
  final VoidCallback onDragEnd;
  final VoidCallback onAdd;
  final ValueChanged<Project> onEdit;
  final ValueChanged<Project> onDelete;
  final ValueChanged<Project> onTap;

  const _KanbanColumn({
    required this.colDef, required this.projects, required this.isDragging,
    required this.onDrop, required this.onDragStart, required this.onDragEnd,
    required this.onAdd, required this.onEdit, required this.onDelete, required this.onTap,
  });

  @override
  State<_KanbanColumn> createState() => _KanbanColumnState();
}

class _KanbanColumnState extends State<_KanbanColumn> {
  bool _isHovered = false;

  @override
  Widget build(BuildContext context) {
    final col = widget.colDef;
    final count = widget.projects.length;

    return DragTarget<Project>(
      onWillAcceptWithDetails: (d) {
        setState(() => _isHovered = true);
        return d.data.status != col.status;
      },
      onLeave: (_) => setState(() => _isHovered = false),
      onAcceptWithDetails: (d) {
        setState(() => _isHovered = false);
        widget.onDrop(d.data);
      },
      builder: (ctx, candidates, _) {
        return AnimatedContainer(
          duration: const Duration(milliseconds: 150),
          width: 240,
          margin: const EdgeInsets.only(right: 12, bottom: 12),
          decoration: BoxDecoration(
            color: _isHovered ? col.color.withOpacity(0.07) : const Color(0xFF111113),
            borderRadius: BorderRadius.circular(10),
            border: Border.all(color: _isHovered ? col.color.withOpacity(0.5) : const Color(0xFF222225), width: 1.5),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Column header
              Padding(
                padding: const EdgeInsets.fromLTRB(12, 12, 8, 8),
                child: Row(
                  children: [
                    Container(width: 10, height: 10, decoration: BoxDecoration(shape: BoxShape.circle, color: col.color, border: Border.all(color: Colors.transparent, width: 2))),
                    const SizedBox(width: 8),
                    Text(col.label, style: GoogleFonts.manrope(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 13)),
                    const SizedBox(width: 6),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
                      decoration: BoxDecoration(color: const Color(0xFF222225), borderRadius: BorderRadius.circular(10)),
                      child: Text('$count', style: const TextStyle(color: Colors.grey, fontSize: 11)),
                    ),
                    const Spacer(),
                    GestureDetector(
                      onTap: widget.onAdd,
                      child: const Icon(Icons.add, color: Colors.grey, size: 18),
                    ),
                  ],
                ),
              ),
              const Divider(color: Color(0xFF222225), height: 1),
              // Cards
              Expanded(
                child: ListView.builder(
                  padding: const EdgeInsets.all(8),
                  itemCount: widget.projects.length,
                  itemBuilder: (_, i) => _ProjectKanbanCard(
                    project: widget.projects[i],
                    accentColor: col.color,
                    onDragStart: widget.onDragStart,
                    onDragEnd: widget.onDragEnd,
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

// ─── Project Kanban Card ──────────────────────────────────────────────────────
class _ProjectKanbanCard extends StatelessWidget {
  final Project project;
  final Color accentColor;
  final ValueChanged<String> onDragStart;
  final VoidCallback onDragEnd;
  final ValueChanged<Project> onEdit;
  final ValueChanged<Project> onDelete;
  final ValueChanged<Project> onTap;

  const _ProjectKanbanCard({
    required this.project, required this.accentColor,
    required this.onDragStart, required this.onDragEnd,
    required this.onEdit, required this.onDelete, required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final pct = project.completionPercentage;
    final fmt = DateFormat('dd.MM.yy');

    return Draggable<Project>(
      data: project,
      onDragStarted: () => onDragStart(project.id.toString()),
      onDragEnd: (_) => onDragEnd(),
      feedback: Material(
        color: Colors.transparent,
        child: Opacity(opacity: 0.85, child: SizedBox(width: 224, child: _cardBody(pct, fmt, accentColor))),
      ),
      childWhenDragging: Opacity(opacity: 0.3, child: _cardBody(pct, fmt, accentColor)),
      child: GestureDetector(
        onTap: () => onTap(project),
        child: _cardBody(pct, fmt, accentColor),
      ),
    );
  }

  Widget _cardBody(int pct, DateFormat fmt, Color color) {
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: const Color(0xFF1A1A1C),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: const Color(0xFF2A2A2D)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(project.name, style: GoogleFonts.manrope(color: Colors.white, fontWeight: FontWeight.w600, fontSize: 13)),
              ),
              PopupMenuButton<String>(
                color: const Color(0xFF1E1E20),
                padding: EdgeInsets.zero,
                iconSize: 16,
                icon: const Icon(Icons.more_horiz, color: Colors.grey),
                onSelected: (v) {
                  if (v == 'edit') onEdit(project);
                  if (v == 'delete') onDelete(project);
                },
                itemBuilder: (_) => [
                  const PopupMenuItem(value: 'edit', child: Text('Bearbeiten', style: TextStyle(color: Colors.white, fontSize: 13))),
                  const PopupMenuItem(value: 'delete', child: Text('Löschen', style: TextStyle(color: Colors.red, fontSize: 13))),
                ],
              ),
            ],
          ),
          if (project.description != null && project.description!.isNotEmpty) ...[
            const SizedBox(height: 4),
            Text(project.description!, maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(color: Colors.grey, fontSize: 11)),
          ],
          const SizedBox(height: 10),
          ClipRRect(
            borderRadius: BorderRadius.circular(3),
            child: LinearProgressIndicator(
              value: pct / 100.0,
              backgroundColor: const Color(0xFF2A2A2D),
              valueColor: AlwaysStoppedAnimation<Color>(color),
              minHeight: 4,
            ),
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              const Icon(Icons.task_alt, size: 11, color: Colors.grey),
              const SizedBox(width: 3),
              Text('${project.tasksCompleted}/${project.tasksTotal}', style: const TextStyle(color: Colors.grey, fontSize: 10)),
              const SizedBox(width: 8),
              const Icon(Icons.repeat, size: 11, color: Colors.grey),
              const SizedBox(width: 3),
              Text('${project.weeklySessionCount}×/Wo · ${project.sessionDurationMinutes ~/ 60}h', style: const TextStyle(color: Colors.grey, fontSize: 10)),
              const Spacer(),
              if (project.targetEndDate != null)
                Row(children: [
                  Icon(Icons.event, size: 11, color: project.isOverdue ? Colors.red : Colors.grey),
                  const SizedBox(width: 3),
                  Text(fmt.format(project.targetEndDate!), style: TextStyle(color: project.isOverdue ? Colors.red : Colors.grey, fontSize: 10)),
                ]),
            ],
          ),
        ],
      ),
    );
  }
}

// ─── Detail Screen ────────────────────────────────────────────────────────────
class _ProjectDetailScreen extends StatelessWidget {
  final Project project;
  const _ProjectDetailScreen({required this.project});

  @override
  Widget build(BuildContext context) {
    final fmt = DateFormat('dd.MM.yyyy');
    final totalMins = project.weeklySessionCount * project.sessionDurationMinutes;

    return Scaffold(
      backgroundColor: const Color(0xFF0A0A0B),
      appBar: AppBar(
        backgroundColor: const Color(0xFF111113),
        elevation: 0,
        leading: const BackButton(color: Color(0xFFC2C1FF)),
        title: Text(project.name, style: GoogleFonts.spaceGrotesk(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 18)),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // Status + progress
          Row(children: [
            _Chip(project.statusLabel),
            const SizedBox(width: 8),
            _Chip('${project.completionPercentage}%'),
            const SizedBox(width: 8),
            _Chip('${project.tasksCompleted}/${project.tasksTotal} Tasks'),
          ]),
          const SizedBox(height: 16),
          ClipRRect(
            borderRadius: BorderRadius.circular(6),
            child: LinearProgressIndicator(
              value: project.completionPercentage / 100.0,
              backgroundColor: const Color(0xFF222225),
              valueColor: const AlwaysStoppedAnimation<Color>(Color(0xFF5856D6)),
              minHeight: 8,
            ),
          ),
          const SizedBox(height: 20),
          if (project.description != null) ...[
            Text(project.description!, style: const TextStyle(color: Colors.grey, fontSize: 14)),
            const SizedBox(height: 20),
          ],
          _InfoRow('Startdatum', project.startDate != null ? fmt.format(project.startDate!) : '—'),
          _InfoRow('Zieldatum', project.targetEndDate != null ? fmt.format(project.targetEndDate!) : '—'),
          _InfoRow('Sessions/Woche', '${project.weeklySessionCount}×'),
          _InfoRow('Session-Dauer', '${project.sessionDurationMinutes} Min'),
          _InfoRow('Wöchentlich', '${totalMins ~/ 60}h ${totalMins % 60}min'),
        ],
      ),
    );
  }
}

class _Chip extends StatelessWidget {
  final String label;
  const _Chip(this.label);
  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
    decoration: BoxDecoration(color: const Color(0xFF1A1A1C), borderRadius: BorderRadius.circular(8), border: Border.all(color: const Color(0xFF2A2A2D))),
    child: Text(label, style: const TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.bold)),
  );
}

class _InfoRow extends StatelessWidget {
  final String label;
  final String value;
  const _InfoRow(this.label, this.value);
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 6),
    child: Row(children: [
      Text(label, style: const TextStyle(color: Colors.grey, fontSize: 13)),
      const Spacer(),
      Text(value, style: const TextStyle(color: Colors.white, fontSize: 13, fontWeight: FontWeight.w600)),
    ]),
  );
}

// ─── Create / Edit Sheet ──────────────────────────────────────────────────────
class _CreateProjectSheet extends StatefulWidget {
  final Project? editing;
  const _CreateProjectSheet({this.editing});
  @override
  State<_CreateProjectSheet> createState() => _CreateProjectSheetState();
}

class _CreateProjectSheetState extends State<_CreateProjectSheet> {
  final _nameCtrl = TextEditingController();
  final _descCtrl = TextEditingController();
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
  void dispose() { _nameCtrl.dispose(); _descCtrl.dispose(); super.dispose(); }

  Future<void> _pickDate(bool isStart) async {
    final picked = await showDatePicker(
      context: context,
      initialDate: (isStart ? _startDate : _targetEnd) ?? DateTime.now(),
      firstDate: DateTime(2020), lastDate: DateTime(2035),
    );
    if (picked != null) setState(() { if (isStart) _startDate = picked; else _targetEnd = picked; });
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
    final ok = widget.editing != null ? await provider.updateProject(project) : await provider.addProject(project);
    if (ok && mounted) Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    final fmt = DateFormat('dd.MM.yyyy');

    return Container(
      decoration: const BoxDecoration(color: Color(0xFF111113), borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom + 24, left: 20, right: 20, top: 16),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
              Text(widget.editing != null ? 'Bearbeiten' : 'Neues Projekt', style: GoogleFonts.spaceGrotesk(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 18)),
              IconButton(icon: const Icon(Icons.close, color: Colors.grey), onPressed: () => Navigator.pop(context)),
            ]),
            const SizedBox(height: 14),
            _label('Name'),
            const SizedBox(height: 6),
            TextField(controller: _nameCtrl, autofocus: true, style: const TextStyle(color: Colors.white), decoration: _deco('Projektname…')),
            const SizedBox(height: 12),
            _label('Beschreibung'),
            const SizedBox(height: 6),
            TextField(controller: _descCtrl, maxLines: 2, style: const TextStyle(color: Colors.white), decoration: _deco('Optional…')),
            const SizedBox(height: 12),
            _label('Status'),
            const SizedBox(height: 8),
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: [for (final col in _columns) Padding(
                  padding: const EdgeInsets.only(right: 8),
                  child: ChoiceChip(
                    label: Text(col.label),
                    selected: _status == col.status,
                    selectedColor: col.color.withOpacity(0.3),
                    backgroundColor: const Color(0xFF1A1A1C),
                    side: BorderSide(color: _status == col.status ? col.color : const Color(0xFF333336)),
                    labelStyle: TextStyle(color: _status == col.status ? Colors.white : Colors.grey, fontWeight: FontWeight.w600, fontSize: 12),
                    onSelected: (_) => setState(() => _status = col.status),
                  ),
                )],
              ),
            ),
            const SizedBox(height: 12),
            Row(children: [
              Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                _label('Startdatum'), const SizedBox(height: 6),
                GestureDetector(onTap: () => _pickDate(true), child: _readonlyBox(_startDate != null ? fmt.format(_startDate!) : 'Wählen…')),
              ])),
              const SizedBox(width: 12),
              Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                _label('Zieldatum'), const SizedBox(height: 6),
                GestureDetector(onTap: () => _pickDate(false), child: _readonlyBox(_targetEnd != null ? fmt.format(_targetEnd!) : 'Wählen…')),
              ])),
            ]),
            const SizedBox(height: 16),
            _label('Wöchentliche Arbeit'),
            const SizedBox(height: 10),
            Row(children: [
              Expanded(child: Column(children: [
                Text('$_sessions× / Woche', style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 14)),
                Slider(value: _sessions.toDouble(), min: 1, max: 7, divisions: 6, activeColor: const Color(0xFF5856D6), label: '$_sessions Sessions', onChanged: (v) => setState(() => _sessions = v.round())),
              ])),
              const SizedBox(width: 12),
              Expanded(child: Column(children: [
                Text('${_sessionMins} Min / Session', style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 14)),
                Slider(value: _sessionMins.toDouble(), min: 15, max: 240, divisions: 15, activeColor: const Color(0xFF5856D6), label: '$_sessionMins Min', onChanged: (v) => setState(() => _sessionMins = v.round())),
              ])),
            ]),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: BoxDecoration(color: const Color(0xFF5856D6).withOpacity(0.1), borderRadius: BorderRadius.circular(8)),
              child: Row(children: [
                const Icon(Icons.timer_outlined, color: Color(0xFFC2C1FF), size: 16),
                const SizedBox(width: 8),
                Text(
                  '${(_sessions * _sessionMins / 60).toStringAsFixed(1)} Stunden / Woche',
                  style: const TextStyle(color: Color(0xFFC2C1FF), fontWeight: FontWeight.bold, fontSize: 13),
                ),
              ]),
            ),
            const SizedBox(height: 20),
            SizedBox(
              width: double.infinity, height: 48,
              child: FilledButton(
                onPressed: _save,
                style: FilledButton.styleFrom(backgroundColor: const Color(0xFF5856D6), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12))),
                child: Text(widget.editing != null ? 'Speichern' : 'Erstellen', style: GoogleFonts.manrope(fontWeight: FontWeight.bold, fontSize: 15)),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _label(String t) => Text(t, style: const TextStyle(color: Colors.grey, fontSize: 12, fontWeight: FontWeight.w600));

  InputDecoration _deco(String hint) => InputDecoration(
    hintText: hint, hintStyle: const TextStyle(color: Color(0xFF444446)),
    filled: true, fillColor: const Color(0xFF1A1A1C),
    border: OutlineInputBorder(borderRadius: BorderRadius.circular(8), borderSide: const BorderSide(color: Color(0xFF333336))),
    enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(8), borderSide: const BorderSide(color: Color(0xFF333336))),
    focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(8), borderSide: const BorderSide(color: Color(0xFF5856D6), width: 2)),
    contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
  );

  Widget _readonlyBox(String t) => Container(
    width: double.infinity, padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 11),
    decoration: BoxDecoration(color: const Color(0xFF1A1A1C), border: Border.all(color: const Color(0xFF333336)), borderRadius: BorderRadius.circular(8)),
    child: Text(t, style: const TextStyle(color: Colors.white, fontSize: 13)),
  );
}
