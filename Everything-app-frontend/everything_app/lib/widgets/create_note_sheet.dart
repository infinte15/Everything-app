import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:google_fonts/google_fonts.dart';
import '../providers/study_provider.dart';

import '../config/app_theme.dart';

// Stitch Design System: Kinetic Mono
const _primary = Color(0xFF5856D6);
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
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final backgroundColor = isDark ? const Color(0xFF131313) : Colors.white;
    final onSurface = isDark ? const Color(0xFFF5F5F5) : Colors.black;
    final onSurfaceVariant = isDark ? const Color(0xFFA0A0A0) : Colors.grey;

    return Padding(
      padding: EdgeInsets.only(
        bottom: MediaQuery.of(context).viewInsets.bottom,
      ),
      child: Container(
        padding: const EdgeInsets.all(24),
        decoration: BoxDecoration(
          color: backgroundColor,
          borderRadius: const BorderRadius.vertical(top: Radius.circular(16)),
          border: Border(top: BorderSide(color: isDark ? const Color(0xFF333333) : const Color(0xFFE8EAF0), width: 1)),
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
                color: onSurface,
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
                    primaryColor: _primary,
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: _CategoryButton(
                    title: 'Studium',
                    isSelected: _selectedCategory == 'Studium',
                    onTap: () => setState(() => _selectedCategory = 'Studium'),
                    primaryColor: _primary,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 24),

            TextField(
              controller: _titleController,
              style: GoogleFonts.inter(color: onSurface, fontSize: 16),
              decoration: InputDecoration(
                labelText: 'Titel',
                labelStyle: GoogleFonts.inter(color: onSurfaceVariant, fontSize: 14),
                enabledBorder: UnderlineInputBorder(borderSide: BorderSide(color: isDark ? const Color(0xFF333333) : const Color(0xFFE8EAF0))),
                focusedBorder: UnderlineInputBorder(borderSide: BorderSide(color: _primary)),
              ),
            ),
            const SizedBox(height: 16),

            TextField(
              controller: _contentController,
              maxLines: 5,
              style: GoogleFonts.inter(color: onSurface, fontSize: 14),
              decoration: InputDecoration(
                hintText: 'Inhalt schreiben...',
                hintStyle: GoogleFonts.inter(color: onSurfaceVariant, fontSize: 14),
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
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
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
  final Color primaryColor;

  const _CategoryButton({
    required this.title,
    required this.isSelected,
    required this.onTap,
    required this.primaryColor,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final surfaceColor = isDark ? const Color(0xFF1E1E1E) : const Color(0xFFF5F5F5);
    final onSurfaceVariant = isDark ? const Color(0xFFA0A0A0) : Colors.grey;
    final outlineVariant = isDark ? const Color(0xFF333333) : const Color(0xFFE8EAF0);

    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 12),
        decoration: BoxDecoration(
          color: isSelected ? primaryColor : surfaceColor,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(
            color: isSelected ? primaryColor : outlineVariant,
            width: 1,
          ),
        ),
        child: Center(
          child: Text(
            title.toUpperCase(),
            style: GoogleFonts.manrope(
              fontSize: 12,
              fontWeight: FontWeight.w800,
              color: isSelected ? Colors.white : onSurfaceVariant,
              letterSpacing: 1.0,
            ),
          ),
        ),
      ),
    );
  }
}
