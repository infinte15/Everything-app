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
  bool _isSearching = false; // Steuert, ob die Suchleiste in der AppBar aktiv ist
  
  // Filter-Status für Kategorien
  String _selectedCategoryFilter = 'All'; // 'All', 'Personal', 'Studium'

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

  // Filter-Menü als BottomSheet (1:1 passend zu deinem Clean/Dark Style)
  void _showFilterMenu() {
    showModalBottomSheet(
      context: context,
      backgroundColor: const Color(0xFF131313),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (BuildContext context) {
        return StatefulBuilder(
          builder: (BuildContext context, StateSetter setModalState) {
            return Padding(
              padding: const EdgeInsets.all(20.0),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        'Notizen filtern',
                        style: GoogleFonts.manrope(
                          fontSize: 18, 
                          fontWeight: FontWeight.bold, 
                          color: Colors.white
                        ),
                      ),
                      IconButton(
                        icon: const Icon(Icons.close, color: Colors.grey),
                        onPressed: () => Navigator.pop(context),
                      )
                    ],
                  ),
                  const Divider(color: Color(0xFF222222)),
                  const SizedBox(height: 8),
                  Text(
                    'Kategorie auswählen',
                    style: GoogleFonts.manrope(
                      fontSize: 12, 
                      fontWeight: FontWeight.w600, 
                      color: Colors.grey
                    ),
                  ),
                  const SizedBox(height: 12),
                  Row(
                    children: ['All', 'Personal', 'Studium'].map((cat) {
                      final isSelected = _selectedCategoryFilter == cat;
                      return Padding(
                        padding: const EdgeInsets.only(right: 8.0),
                        child: ChoiceChip(
                          backgroundColor: const Color(0xFF1E1E1E),
                          selectedColor: const Color(0xFF5856D6),
                          labelStyle: GoogleFonts.manrope(
                            color: isSelected ? Colors.white : Colors.grey,
                            fontSize: 13,
                            fontWeight: FontWeight.w600
                          ),
                          label: Text(cat == 'All' ? 'Alle' : cat),
                          selected: isSelected,
                          onSelected: (bool selected) {
                            if (selected) {
                              setModalState(() => _selectedCategoryFilter = cat);
                              setState(() => _selectedCategoryFilter = cat);
                            }
                          },
                        ),
                      );
                    }).toList(),
                  ),
                  const SizedBox(height: 20),
                ],
              ),
            );
          },
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final studyProvider = context.watch<StudyProvider>();
    
    // Such- und Kategorie-Pipeline
    final notes = studyProvider.notes.where((n) {
      final matchesSearch = n.title.toLowerCase().contains(_searchQuery.toLowerCase()) || 
                            n.content.toLowerCase().contains(_searchQuery.toLowerCase());
      
      final matchesCategory = _selectedCategoryFilter == 'All' || 
                              (n.category?.toLowerCase() == _selectedCategoryFilter.toLowerCase());
      
      return matchesSearch && matchesCategory;
    }).toList();

    return Scaffold(
      backgroundColor: const Color(0xFF0E0E0E),
      appBar: AppBar(
        backgroundColor: const Color(0xFF131313),
        elevation: 0,
        leading: _isSearching
            ? IconButton(
                icon: const Icon(Icons.arrow_back, color: Color(0xFFC2C1FF)),
                onPressed: () {
                  setState(() {
                    _isSearching = false;
                    _searchQuery = '';
                    _searchController.clear();
                  });
                },
              )
            : const BackButton(color: Color(0xFFC2C1FF)),
        title: _isSearching
            ? TextField(
                controller: _searchController,
                autofocus: true,
                style: const TextStyle(color: Colors.white, fontSize: 16),
                decoration: const InputDecoration(
                  hintText: 'Suchen...',
                  hintStyle: TextStyle(color: Colors.grey, fontSize: 16),
                  border: InputBorder.none,
                ),
                onChanged: (val) {
                  setState(() {
                    _searchQuery = val;
                  });
                },
              )
            : Text(
                'Notes',
                style: GoogleFonts.spaceGrotesk(
                  color: Colors.white,
                  fontWeight: FontWeight.bold,
                  fontSize: 20,
                ),
              ),
        actions: [
          // Wenn nicht gesucht wird: Zeige die Lupe an
          if (!_isSearching)
            IconButton(
              icon: const Icon(Icons.search, color: Colors.grey),
              onPressed: () {
                setState(() {
                  _isSearching = true;
                });
              },
            ),
          // Filter-Symbol (Leuchtet lila, sobald gefiltert wird)
          IconButton(
            icon: Icon(
              Icons.filter_list,
              color: _selectedCategoryFilter != 'All' ? const Color(0xFF5856D6) : Colors.grey,
            ),
            onPressed: _showFilterMenu,
          ),
        ],
      ),
      body: notes.isEmpty
          ? const Center(
              child: Text(
                'Keine Notizen gefunden',
                style: TextStyle(color: Colors.grey),
              ),
            )
          : ListView.builder(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
              itemCount: notes.length,
              itemBuilder: (ctx, index) {
                final note = notes[index];
                return _NoteCard(
                  note: note,
                  onDelete: () => _confirmDelete(context, note),
                );
              },
            ),
      floatingActionButton: FloatingActionButton(
        backgroundColor: const Color(0xFF5856D6),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        onPressed: () {
          showModalBottomSheet(
            context: context,
            isScrollControlled: true,
            backgroundColor: Colors.transparent,
            builder: (ctx) => const CreateNoteSheet(),
          );
        },
        child: const Icon(Icons.add, color: Colors.white),
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

// ─── DEUTLICH KOMPAKTERE NOTIZ-KARTE (IM ORIGINALEN FORMAT) ─────────────────

class _NoteCard extends StatelessWidget {
  final StudyNote note;
  final VoidCallback onDelete;

  const _NoteCard({required this.note, required this.onDelete});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 8), // Kompakterer Außenabstand
      padding: const EdgeInsets.all(10), // Reduziertes Innen-Padding von 14 auf 10
      decoration: BoxDecoration(
        color: const Color(0xFF131313),
        borderRadius: BorderRadius.circular(4), // Originaler Radius
        border: Border.all(color: const Color(0xFF222222), width: 0.5),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          // Zeile 1: Titel links, Stern & Mülleimer rechts
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Expanded(
                child: Text(
                  note.title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: GoogleFonts.manrope(
                    color: Colors.white,
                    fontWeight: FontWeight.w700,
                    fontSize: 13.5, // Leicht verkleinerte Titel-Schrift
                  ),
                ),
              ),
              const SizedBox(width: 8),
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (note.isFavorite)
                    const Padding(
                      padding: EdgeInsets.only(right: 6),
                      child: Icon(Icons.star, color: Colors.amber, size: 12),
                    ),
                  GestureDetector(
                    onTap: onDelete,
                    child: const Icon(
                      Icons.delete_outline, 
                      color: Colors.redAccent, 
                      size: 15 // Etwas kompakteres Icon
                    ),
                  ),
                ],
              ),
            ],
          ),
          
          // Zeile 2: Content (Inhaltstext)
          if (note.content.isNotEmpty) ...[
            const SizedBox(height: 4), // Kleinerer Abstand
            Text(
              note.content,
              maxLines: 2, // Hält die Karte flach und sauber gestaucht
              overflow: TextOverflow.ellipsis,
              style: GoogleFonts.inter(
                color: Colors.grey,
                fontSize: 11, // Kompakteres Schriftbild
                height: 1.3,
              ),
            ),
          ],
          
          const SizedBox(height: 6), // Kleinerer Abstand vorm Badge
          
          // Zeile 3: Kategorie-Badge unten
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1.5),
            decoration: BoxDecoration(
              color: const Color(0xFFC2C1FF).withOpacity(0.06),
              borderRadius: BorderRadius.circular(2),
            ),
            child: Text(
              note.category?.toUpperCase() ?? 'PERSONAL',
              style: GoogleFonts.inter(
                color: const Color(0xFFC2C1FF), 
                fontSize: 7.5, // Miniaturisiertes, klares Badge
                fontWeight: FontWeight.bold
              ),
            ),
          ),
        ],
      ),
    );
  }
}