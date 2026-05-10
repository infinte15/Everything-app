import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:google_fonts/google_fonts.dart';
import '../../providers/study_provider.dart';
import '../../models/study_note.dart';
import '../../config/app_theme.dart';
import '../../widgets/create_note_sheet.dart';

class NotesScreen extends StatefulWidget {
  const NotesScreen({super.key});

  @override
  State<NotesScreen> createState() => _NotesScreenState();
}

class _NotesScreenState extends State<NotesScreen> {
  final _searchController = TextEditingController();
  String _searchQuery = '';

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<StudyProvider>().loadData();
    });
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final studyProvider = context.watch<StudyProvider>();
    final notes = studyProvider.notes
        .where((n) => n.title.toLowerCase().contains(_searchQuery.toLowerCase()) || 
                      n.content.toLowerCase().contains(_searchQuery.toLowerCase()))
        .toList();

    return Scaffold(
      backgroundColor: const Color(0xFF0E0E0E),
      appBar: AppBar(
        backgroundColor: const Color(0xFF131313),
        elevation: 0,
        leading: const BackButton(color: Color(0xFFC2C1FF)),
        title: Text(
          'Notizen',
          style: GoogleFonts.manrope(
            color: const Color(0xFFC2C1FF),
            fontWeight: FontWeight.w800,
          ),
        ),
      ),
      body: Column(
        children: [
          // Search Bar
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: TextField(
              controller: _searchController,
              onChanged: (v) => setState(() => _searchQuery = v),
              style: const TextStyle(color: Colors.white),
              decoration: InputDecoration(
                hintText: 'Notizen durchsuchen...',
                hintStyle: const TextStyle(color: Colors.grey),
                prefixIcon: const Icon(Icons.search, color: Colors.grey),
                filled: true,
                fillColor: const Color(0xFF252626),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(8),
                  borderSide: BorderSide.none,
                ),
              ),
            ),
          ),
          
          // Notes Grid
          Expanded(
            child: studyProvider.isLoading
                ? const Center(child: CircularProgressIndicator())
                : notes.isEmpty
                    ? _buildEmptyState()
                    : GridView.builder(
                        padding: const EdgeInsets.all(16),
                        gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                          crossAxisCount: 2,
                          mainAxisSpacing: 12,
                          crossAxisSpacing: 12,
                          childAspectRatio: 1.8,
                        ),
                        itemCount: notes.length,
                        itemBuilder: (context, index) => _NoteCard(note: notes[index]),
                      ),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => _showCreateNote(context),
        backgroundColor: const Color(0xFFC2C1FF),
        child: const Icon(Icons.add, color: Colors.black),
      ),
    );
  }

  void _showCreateNote(BuildContext context) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => const CreateNoteSheet(),
    );
  }

  Widget _buildEmptyState() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.note_alt_outlined, size: 64, color: Colors.grey),
          const SizedBox(height: 16),
          Text(
            _searchQuery.isEmpty ? 'Noch keine Notizen' : 'Keine Ergebnisse gefunden',
            style: const TextStyle(color: Colors.grey, fontSize: 16),
          ),
        ],
      ),
    );
  }


}

class _NoteCard extends StatelessWidget {
  final StudyNote note;
  const _NoteCard({required this.note});

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: const Color(0xFF252626),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: Colors.white.withValues(alpha: 0.05)),
      ),
      padding: const EdgeInsets.all(6),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.end,
            children: [
              SizedBox(
                height: 16,
                width: 16,
                child: Checkbox(
                  value: false,
                  onChanged: (v) {
                    if (v == true) _confirmDelete(context, note);
                  },
                  activeColor: const Color(0xFFC2C1FF),
                  checkColor: Colors.black,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(2)),
                  side: const BorderSide(color: Colors.grey, width: 0.8),
                ),
              ),
            ],
          ),
          Text(
            note.title,
            style: const TextStyle(
              color: Colors.white,
              fontWeight: FontWeight.bold,
              fontSize: 11,
            ),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          const SizedBox(height: 1),
          Expanded(
            child: Text(
              note.content,
              style: TextStyle(
                color: Colors.white.withValues(alpha: 0.6),
                fontSize: 9,
              ),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
          const SizedBox(height: 2),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
                decoration: BoxDecoration(
                  color: const Color(0xFFC2C1FF).withOpacity(0.08),
                  borderRadius: BorderRadius.circular(2),
                ),
                child: Text(
                  note.category?.toUpperCase() ?? 'PERSONAL',
                  style: GoogleFonts.inter(color: const Color(0xFFC2C1FF), fontSize: 7, fontWeight: FontWeight.bold),
                ),
              ),
              if (note.isFavorite)
                const Icon(Icons.star, color: Colors.amber, size: 8),
            ],
          ),
        ],
      ),
    );
  }

  void _confirmDelete(BuildContext context, StudyNote note) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF1E1E1E),
        title: const Text('Notiz löschen?', style: TextStyle(color: Colors.white)),
        content: Text('Möchtest du "${note.title}" wirklich löschen?', style: const TextStyle(color: Colors.grey)),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Abbrechen', style: TextStyle(color: Colors.grey)),
          ),
          TextButton(
            onPressed: () {
              context.read<StudyProvider>().deleteNote(note.id!);
              Navigator.pop(ctx);
            },
            child: const Text('Löschen', style: TextStyle(color: Colors.redAccent)),
          ),
        ],
      ),
    );
  }
}
