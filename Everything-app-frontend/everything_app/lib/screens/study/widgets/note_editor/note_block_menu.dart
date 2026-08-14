import 'package:flutter/material.dart';

import 'note_slash_menu.dart';

/// Was das Blockmenü außer dem Umwandeln noch kann.
enum NoteBlockAction { duplicate, delete }

/// Das Menü hinter dem ⋮⋮-Anfasser.
///
/// Die Liste der Zielarten ist dieselbe wie im Slash-Menü ([noteSlashCommands]) — es wäre
/// dieselbe Auswahl an zwei Orten, und die beiden liefen unweigerlich auseinander.
///
/// Auf Touch ist das der einzige Weg, einen Block zu löschen oder zu verschieben: die
/// Bildschirmtastatur meldet ein Backspace am Zeilenanfang gar nicht erst.
Future<void> showNoteBlockMenu(
  BuildContext context, {
  required Offset globalPosition,
  required ValueChanged<NoteBlockAction> onAction,
  required ValueChanged<SlashCommand> onConvert,
}) async {
  final overlay = Overlay.of(context).context.findRenderObject() as RenderBox;
  final position = RelativeRect.fromLTRB(
    globalPosition.dx,
    globalPosition.dy,
    overlay.size.width - globalPosition.dx,
    overlay.size.height - globalPosition.dy,
  );

  final picked = await showMenu<Object>(
    context: context,
    position: position,
    shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
    // Die Voreinstellung sind 256 px, und daran stossen die laengeren Beschriftungen an.
    constraints: const BoxConstraints(minWidth: 220, maxWidth: 320),
    items: [
      const PopupMenuItem<Object>(
        value: NoteBlockAction.duplicate,
        height: 40,
        child: Row(children: [
          Icon(Icons.copy_outlined, size: 16),
          SizedBox(width: 10),
          Text('Duplizieren'),
        ]),
      ),
      const PopupMenuItem<Object>(
        value: NoteBlockAction.delete,
        height: 40,
        child: Row(children: [
          Icon(Icons.delete_outline, size: 16),
          SizedBox(width: 10),
          Text('Löschen'),
        ]),
      ),
      const PopupMenuDivider(),
      PopupMenuItem<Object>(
        enabled: false,
        height: 28,
        child: Text(
          'UMWANDELN IN',
          style: Theme.of(context).textTheme.labelSmall?.copyWith(letterSpacing: 0.6),
        ),
      ),
      for (final command in noteSlashCommands)
        PopupMenuItem<Object>(
          value: command,
          height: 36,
          child: Row(children: [
            Icon(command.icon, size: 16),
            const SizedBox(width: 10),
            Expanded(child: Text(command.label, overflow: TextOverflow.ellipsis)),
          ]),
        ),
    ],
  );

  if (picked is NoteBlockAction) onAction(picked);
  if (picked is SlashCommand) onConvert(picked);
}
