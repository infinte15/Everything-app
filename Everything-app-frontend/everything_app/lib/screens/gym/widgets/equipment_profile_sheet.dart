import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../models/gym/gym_models.dart';
import '../../../providers/sports_provider.dart';
import '../../../theme/lyfta_theme.dart';

/// Ausrüstungsprofile verwalten und eines aktivieren.
///
/// Ein aktives Profil blendet aus der Bibliothek alles aus, was sich damit nicht machen
/// lässt - "Zuhause" ohne Langhantel zeigt keine Kniebeugen mehr an. Das ist kein Filter
/// unter vielen, sondern eine Einstellung, die bleibt, bis man sie zurücknimmt.
class EquipmentProfileSheet extends StatelessWidget {
  const EquipmentProfileSheet({super.key});

  static Future<void> show(BuildContext context) => showModalBottomSheet<void>(
        context: context,
        isScrollControlled: true,
        backgroundColor: LyftaTheme.surface,
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
        ),
        builder: (_) => const EquipmentProfileSheet(),
      );

  @override
  Widget build(BuildContext context) {
    final sports = context.watch<SportsProvider>();
    final profiles = sports.equipmentProfiles;

    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Ausrüstung', style: LyftaTheme.title.copyWith(fontSize: 16)),
            const SizedBox(height: 4),
            Text(
              'Die Bibliothek zeigt dann nur, was du damit trainieren kannst.',
              style: LyftaTheme.caption,
            ),
            const SizedBox(height: 12),
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: Icon(
                sports.activeEquipmentProfile == null
                    ? Icons.radio_button_checked_rounded
                    : Icons.radio_button_unchecked_rounded,
                color: LyftaTheme.primary,
                size: 20,
              ),
              title: Text('Alles verfügbar',
                  style: LyftaTheme.subtitle.copyWith(color: LyftaTheme.textPrimary)),
              onTap: () => sports.activateEquipmentProfile(null),
            ),
            for (final profile in profiles)
              ListTile(
                contentPadding: EdgeInsets.zero,
                leading: Icon(
                  profile.isActive
                      ? Icons.radio_button_checked_rounded
                      : Icons.radio_button_unchecked_rounded,
                  color: LyftaTheme.primary,
                  size: 20,
                ),
                title: Text(profile.name,
                    style: LyftaTheme.subtitle.copyWith(color: LyftaTheme.textPrimary)),
                subtitle: Text(
                  profile.equipment.isEmpty
                      ? 'Keine Geräte gewählt - filtert nicht'
                      : profile.equipment.join(', '),
                  style: LyftaTheme.caption,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
                onTap: () => sports.activateEquipmentProfile(profile.id),
                trailing: IconButton(
                  icon: const Icon(Icons.edit_rounded, size: 18),
                  color: LyftaTheme.textTertiary,
                  onPressed: () => _edit(context, profile),
                ),
              ),
            const SizedBox(height: 6),
            TextButton.icon(
              onPressed: () => _edit(context, null),
              icon: const Icon(Icons.add, size: 18),
              label: const Text('Profil anlegen'),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _edit(BuildContext context, GymEquipmentProfile? profile) =>
      showModalBottomSheet<void>(
        context: context,
        isScrollControlled: true,
        backgroundColor: LyftaTheme.surface,
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
        ),
        builder: (_) => _EditProfileSheet(profile: profile),
      );
}

class _EditProfileSheet extends StatefulWidget {
  final GymEquipmentProfile? profile;

  const _EditProfileSheet({this.profile});

  @override
  State<_EditProfileSheet> createState() => _EditProfileSheetState();
}

class _EditProfileSheetState extends State<_EditProfileSheet> {
  late final TextEditingController _name =
      TextEditingController(text: widget.profile?.name ?? '');
  late final Set<String> _selected = {...(widget.profile?.equipment ?? const [])};
  List<String> _available = const [];
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    _loadEquipment();
  }

  /// Die Geräte-Werte kommen aus dem Katalog, nicht aus einer eigenen Liste - eine zweite
  /// Wertemenge müsste bei jedem Katalog-Wechsel nachgepflegt werden.
  Future<void> _loadEquipment() async {
    final values = await context.read<SportsProvider>().equipmentValues();
    if (mounted) setState(() => _available = values);
  }

  @override
  void dispose() {
    _name.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final sports = context.read<SportsProvider>();

    return SafeArea(
      child: Padding(
        padding: EdgeInsets.fromLTRB(
            16, 16, 16, MediaQuery.of(context).viewInsets.bottom + 16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            TextField(
              controller: _name,
              decoration: const InputDecoration(hintText: 'Zuhause, Studio …'),
              style: LyftaTheme.title.copyWith(fontSize: 15),
            ),
            const SizedBox(height: 14),
            Text('GERÄTE', style: LyftaTheme.label),
            const SizedBox(height: 8),
            ConstrainedBox(
              constraints: const BoxConstraints(maxHeight: 260),
              child: SingleChildScrollView(
                child: Wrap(
                  spacing: 8,
                  runSpacing: 4,
                  children: [
                    for (final value in _available)
                      FilterChip(
                        label: Text(value),
                        selected: _selected.contains(value),
                        onSelected: (on) => setState(() {
                          on ? _selected.add(value) : _selected.remove(value);
                        }),
                      ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                if (widget.profile != null)
                  TextButton(
                    onPressed: _saving
                        ? null
                        : () async {
                            final navigator = Navigator.of(context);
                            await sports.deleteEquipmentProfile(widget.profile!.id);
                            navigator.pop();
                          },
                    child: const Text('Löschen',
                        style: TextStyle(color: LyftaTheme.danger)),
                  ),
                const Spacer(),
                FilledButton(
                  onPressed: _saving ? null : () => _save(sports),
                  child: const Text('Speichern'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _save(SportsProvider sports) async {
    final name = _name.text.trim();
    if (name.isEmpty) return;

    setState(() => _saving = true);
    final navigator = Navigator.of(context);
    final ok = await sports.saveEquipmentProfile(
      id: widget.profile?.id,
      name: name,
      equipment: _selected.toList(),
    );
    if (!mounted) return;
    setState(() => _saving = false);
    if (ok) navigator.pop();
  }
}
