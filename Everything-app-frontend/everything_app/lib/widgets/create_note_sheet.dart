import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:google_fonts/google_fonts.dart';
import '../providers/study_provider.dart';

// Stitch Design System: Kinetic Mono
const _backgroundColor = Color(0xFF121212);
const _surfaceColor = Color(0xFF1E1E1E);
const _onSurface = Color(0xFFF5F5F5);
const _onSurfaceVariant = Color(0xFFA0A0A0);
const _primary = Color(0xFF5856D6);
const _primaryLight = Color(0xFF9896FF);
const _outlineVariant = Color(0xFF333333);

class CreateNoteSheet extends StatefulWidget {
  const CreateNoteSheet({super.key});

  @override
  State<CreateNoteSheet> createState() => _CreateNoteSheetState();
}

class _CreateNoteSheetState extends State<CreateNoteSheet> {
  final _titleController = TextEditingController();
  final _contentController = TextEditingController();
  String _selectedCategory = 'Personal';

  @override
  void dispose() {
    _titleController.dispose();
    _contentController.dispose();
    super.dispose();
  }

  void _saveNote() {
    final title = _titleController.text.trim();
    if (title.isEmpty) return;

    context.read<StudyProvider>().addNote(
      title: title,
      content: _contentController.text.trim(),
      folderId: _selectedCategory,
    );
    Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(
        bottom: MediaQuery.of(context).viewInsets.bottom,
      ),
      child: Container(
        padding: const EdgeInsets.all(24),
        decoration: const BoxDecoration(
          color: _backgroundColor,
          borderRadius: BorderRadius.vertical(top: Radius.circular(0)), // Kinetic Mono sharp corners
          border: Border(top: BorderSide(color: _outlineVariant, width: 1)),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              'NEUE NOTIZ',
              style: GoogleFonts.manrope(
                fontSize: 18,
                fontWeight: FontWeight.w900,
                color: _onSurface,
                letterSpacing: 2.0,
              ),
            ),
            const SizedBox(height: 24),

            // Category Selection (Personal vs Study)
            Row(
              children: [
                Expanded(
                  child: _CategoryButton(
                    title: 'Personal',
                    isSelected: _selectedCategory == 'Personal',
                    onTap: () => setState(() => _selectedCategory = 'Personal'),
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: _CategoryButton(
                    title: 'Study',
                    isSelected: _selectedCategory == 'Study',
                    onTap: () => setState(() => _selectedCategory = 'Study'),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 24),

            TextField(
              controller: _titleController,
              style: GoogleFonts.inter(color: _onSurface, fontSize: 16),
              decoration: InputDecoration(
                labelText: 'Titel',
                labelStyle: GoogleFonts.inter(color: _onSurfaceVariant, fontSize: 14),
                enabledBorder: const UnderlineInputBorder(borderSide: BorderSide(color: _outlineVariant)),
                focusedBorder: const UnderlineInputBorder(borderSide: BorderSide(color: _primary)),
              ),
            ),
            const SizedBox(height: 16),

            TextField(
              controller: _contentController,
              maxLines: 5,
              style: GoogleFonts.inter(color: _onSurface, fontSize: 14),
              decoration: InputDecoration(
                hintText: 'Inhalt schreiben...',
                hintStyle: GoogleFonts.inter(color: _outlineVariant, fontSize: 14),
                border: InputBorder.none,
              ),
            ),
            const SizedBox(height: 24),

            ElevatedButton(
              onPressed: _saveNote,
              style: ElevatedButton.styleFrom(
                backgroundColor: _primary,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(vertical: 16),
                shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
                elevation: 0,
              ),
              child: Text(
                'NOTIZ SPEICHERN',
                style: GoogleFonts.manrope(
                  fontWeight: FontWeight.w800,
                  letterSpacing: 1.5,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _CategoryButton extends StatelessWidget {
  final String title;
  final bool isSelected;
  final VoidCallback onTap;

  const _CategoryButton({
    required this.title,
    required this.isSelected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 12),
        decoration: BoxDecoration(
          color: isSelected ? _primary : _surfaceColor,
          borderRadius: BorderRadius.zero,
          border: Border.all(
            color: isSelected ? _primary : _outlineVariant,
            width: 1,
          ),
        ),
        child: Center(
          child: Text(
            title.toUpperCase(),
            style: GoogleFonts.manrope(
              fontSize: 12,
              fontWeight: FontWeight.w800,
              color: isSelected ? Colors.white : _onSurfaceVariant,
              letterSpacing: 1.0,
            ),
          ),
        ),
      ),
    );
  }
}
