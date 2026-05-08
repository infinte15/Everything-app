import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:google_fonts/google_fonts.dart';
import '../../providers/study_provider.dart';
import '../../models/study_note.dart';
import '../../config/app_theme.dart';

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
                          childAspectRatio: 0.85,
                        ),
                        itemCount: notes.length,
                        itemBuilder: (context, index) => _NoteCard(note: notes[index]),
                      ),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => _showAddNoteDialog(context),
        backgroundColor: const Color(0xFFC2C1FF),
        child: const Icon(Icons.add, color: Colors.black),
      ),
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

  void _showAddNoteDialog(BuildContext context) {
    final titleController = TextEditingController();
    final contentController = TextEditingController();

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: const Color(0xFF131313),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (context) => Padding(
        padding: EdgeInsets.only(
          bottom: MediaQuery.of(context).viewInsets.bottom + 24,
          left: 24,
          right: 24,
          top: 24,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              'Neue Notiz',
              style: GoogleFonts.manrope(
                fontSize: 20,
                fontWeight: FontWeight.w800,
                color: Colors.white,
              ),
            ),
            const SizedBox(height: 20),
            TextField(
              controller: titleController,
              style: const TextStyle(color: Colors.white),
              decoration: const InputDecoration(
                labelText: 'Titel',
                labelStyle: TextStyle(color: Colors.grey),
                enabledBorder: UnderlineInputBorder(borderSide: BorderSide(color: Colors.grey)),
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: contentController,
              maxLines: 5,
              style: const TextStyle(color: Colors.white),
              decoration: const InputDecoration(
                hintText: 'Inhalt schreiben...',
                hintStyle: TextStyle(color: Colors.grey),
                border: InputBorder.none,
              ),
            ),
            const SizedBox(height: 24),
            ElevatedButton(
              onPressed: () {
                if (titleController.text.isNotEmpty) {
                  context.read<StudyProvider>().addNote(
                    title: titleController.text,
                    content: contentController.text,
                  );
                  Navigator.pop(context);
                }
              },
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFFC2C1FF),
                foregroundColor: Colors.black,
                padding: const EdgeInsets.symmetric(vertical: 16),
              ),
              child: const Text('Speichern', style: TextStyle(fontWeight: FontWeight.bold)),
            ),
          ],
        ),
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
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.white.withValues(alpha: 0.1)),
      ),
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            note.title,
            style: const TextStyle(
              color: Colors.white,
              fontWeight: FontWeight.bold,
              fontSize: 16,
            ),
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
          const SizedBox(height: 8),
          Expanded(
            child: Text(
              note.content,
              style: TextStyle(
                color: Colors.white.withValues(alpha: 0.7),
                fontSize: 13,
              ),
              maxLines: 5,
              overflow: TextOverflow.ellipsis,
            ),
          ),
          const SizedBox(height: 8),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Heute',
                style: TextStyle(color: Colors.white.withValues(alpha: 0.4), fontSize: 11),
              ),
              if (note.isFavorite)
                const Icon(Icons.star, color: Colors.amber, size: 14),
            ],
          ),
        ],
      ),
    );
  }
}
