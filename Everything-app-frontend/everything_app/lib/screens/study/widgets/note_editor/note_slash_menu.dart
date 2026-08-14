import 'package:flutter/material.dart';

import '../../../../utils/note_blocks.dart';

/// Ein Eintrag der Befehlspalette.
class SlashCommand {
  final String label;
  final IconData icon;
  final BlockKind kind;
  final int level;

  /// Zusätzliche Suchwörter — damit `/h1`, `/todo` oder `/liste` auch dann treffen, wenn sie
  /// so nicht in der Beschriftung stehen.
  final List<String> keywords;

  const SlashCommand(
    this.label,
    this.icon,
    this.kind, {
    this.level = 1,
    this.keywords = const [],
  });
}

/// Was `/` anbietet. Löst das frühere EINFÜGEN-Bottom-Sheet ab, das nur Textschnipsel an die
/// Einfügemarke warf; hier wird die Blockart des laufenden Blocks umgestellt.
const List<SlashCommand> noteSlashCommands = [
  SlashCommand('Text', Icons.notes_outlined, BlockKind.paragraph,
      keywords: ['absatz', 'p']),
  SlashCommand('Überschrift 1', Icons.title, BlockKind.heading,
      level: 1, keywords: ['h1', 'titel', 'heading']),
  SlashCommand('Überschrift 2', Icons.title, BlockKind.heading,
      level: 2, keywords: ['h2', 'untertitel', 'heading']),
  SlashCommand('Überschrift 3', Icons.title, BlockKind.heading,
      level: 3, keywords: ['h3', 'heading']),
  SlashCommand('Aufzählung', Icons.format_list_bulleted, BlockKind.bullet,
      keywords: ['liste', 'punkt', 'bullet']),
  SlashCommand('Nummerierte Liste', Icons.format_list_numbered, BlockKind.numbered,
      keywords: ['liste', 'zahlen', 'nummer']),
  SlashCommand('Todo', Icons.check_box_outlined, BlockKind.todo,
      keywords: ['aufgabe', 'haken', 'checkbox', 'kasten']),
  SlashCommand('Merksatz', Icons.lightbulb_outline, BlockKind.callout,
      keywords: ['callout', 'hinweis', 'wichtig']),
  SlashCommand('Ausklappbar', Icons.arrow_right, BlockKind.toggle,
      keywords: ['frage', 'antwort', 'toggle', 'abfragen']),
  SlashCommand('Codeblock', Icons.code, BlockKind.code, keywords: ['quelltext']),
  SlashCommand('Trenner', Icons.horizontal_rule, BlockKind.divider,
      keywords: ['linie', 'strich', 'divider']),
];

/// Umlaute und Versalien weg, damit `/uber` genauso trifft wie `/Über`.
String _fold(String value) => value.toLowerCase().replaceAllMapped(
      RegExp('[äöüß]'),
      (m) => switch (m[0]) { 'ä' => 'a', 'ö' => 'o', 'ü' => 'u', _ => 'ss' },
    );

/// Die Befehle, auf die [query] passt — leere Abfrage heißt alle.
List<SlashCommand> filterSlashCommands(String query) {
  final needle = _fold(query.trim());
  if (needle.isEmpty) return noteSlashCommands;
  return noteSlashCommands
      .where((c) =>
          _fold(c.label).contains(needle) ||
          c.keywords.any((k) => _fold(k).contains(needle)))
      .toList();
}

/// Die Palette selbst.
///
/// Bewusst **ohne** eigenen Fokus: der Block behält die Einfügemarke, damit man weitertippen
/// und damit filtern kann. Die Pfeiltasten laufen deshalb durch den Tastenhandler des Editors,
/// und [highlighted] kommt von außen.
class NoteSlashMenu extends StatelessWidget {
  final List<SlashCommand> commands;
  final int highlighted;
  final ValueChanged<SlashCommand> onPick;
  final ScrollController scrollController;

  const NoteSlashMenu({
    super.key,
    required this.commands,
    required this.highlighted,
    required this.onPick,
    required this.scrollController,
  });

  static const double itemHeight = 40;
  static const double maxHeight = 280;
  static const double width = 260;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;

    return Material(
      color: cs.surfaceContainerLow,
      elevation: 8,
      borderRadius: BorderRadius.zero,
      child: Container(
        width: width,
        constraints: const BoxConstraints(maxHeight: maxHeight),
        decoration: BoxDecoration(border: Border.all(color: cs.outlineVariant)),
        child: commands.isEmpty
            ? Padding(
                padding: const EdgeInsets.all(12),
                child: Text('Kein Befehl gefunden',
                    style: theme.textTheme.bodySmall
                        ?.copyWith(color: cs.onSurfaceVariant)),
              )
            : ListView.builder(
                controller: scrollController,
                padding: EdgeInsets.zero,
                shrinkWrap: true,
                itemCount: commands.length,
                itemExtent: itemHeight,
                itemBuilder: (_, i) {
                  final command = commands[i];
                  final active = i == highlighted;
                  return InkWell(
                    onTap: () => onPick(command),
                    child: Container(
                      color: active ? cs.surfaceContainerHighest : null,
                      padding: const EdgeInsets.symmetric(horizontal: 12),
                      child: Row(
                        children: [
                          Icon(command.icon, size: 16, color: cs.onSurfaceVariant),
                          const SizedBox(width: 10),
                          Expanded(
                            child: Text(
                              command.label,
                              style: theme.textTheme.bodyMedium?.copyWith(
                                color: active ? cs.onSurface : cs.onSurfaceVariant,
                                fontWeight: active ? FontWeight.w600 : null,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  );
                },
              ),
      ),
    );
  }
}
